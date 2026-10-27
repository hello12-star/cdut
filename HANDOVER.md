# 项目交接包

> 用途：给一个**完全没有本对话记忆**的新模型使用。所有内容严格来自本对话，未发生的事不写。  
> 生成时间：2026-10-24 11:45（GMT+8）  
> 核对更新：2026-10-24 12:05（GMT+8）。实测推翻 3 处旧结论，见 §2.1，**先读那一节再看其余内容**。

## 1. 一句话目标与成功标准

**项目名称**：「砚湖日程」（`app_name` 已定为此值；正式项目名用户明确说 `以后再说名字`，**待确认**）

**项目最终目标**：做一个**成都理工大学**版的原生 Android 课表 App，功能对齐参考源码 `HFUT-Schedule`（合工大版），能登录教务、抓取课表、在课程表与聚焦页正确展示，并支持自定义日程与登录失效提醒。

**成功标准**（来自用户多轮验收口径）：

- 与参考源码**逻辑一致**，不得"瞎编乱造、随意猜测、增加、减少"（用户原话）
- 课程表、聚焦页两页"代码逻辑性和功能完整性"过关
- 数据正确：课程条数、教师、周次、地点与官网一致
- 不得出现源码里没有的 UI 元素（用户曾据此要求删除顶部"今天/明天"切换与"考/绩/告"入口）

## 2. 当前状态

**当前阶段**：核心两页（课程表 / 聚焦）已按源码整治完毕并通过实机验证；登录页刚改为"纯原生 + 后台隐藏 WebView"方案并验证通过。用户说 `行，先到这吧。`，**本轮开发暂停**。

**已完成到什么程度**

- 课表抓取：真实数据 **28 条**，教师全、英语课已恢复
- 课程表页：重叠课程并排、块内三要素、地点简化、时间贴边、点击详情、双击空白添加日程、周次胶囊与选择器
- 聚焦页：`重要安排 / 其他事项` 双页签、课程三态、已下课消失、今日课尽转明日、日程过期折叠、日程编辑、长按删除
- 登录：纯原生页面（官网页不可见），账号密码本地保存 + 自动注入 + 自动点登录 + 轮询诊断；失联红色横幅 + `重新登录`

**现在卡在哪里 / 从哪继续**

- ~~当前 App 处于"未登录"状态~~ → **已推翻，见 §2.1**：App 是登录态，别再去引导用户登录
- ~~真实账号的完整登录链路**尚未验证**~~ → 已成功登录过（凭据、TGC、28 条课表缓存俱在），但过程细节无记录

### 2.1 实测核对更正（2026-10-24 12:05，以本节为准）

本节由新一次接手的模型用模拟器 + 导库实测得出，**推翻上面三条旧结论**：

1. **App 早已是登录态，不是未登录。** DataStore（`files/datastore/session.preferences_pb`）里有真实学号 `202619020320`、明文密码、`cas_tgc`、28 条 `timetable_json`。上面「现在卡在哪里」的两条作废，P0 的登录验证实际已通过。
2. **测试日程没被清掉，一条不少。** 早先读到 0 条是**导出方式错了**（见 §3 的 WAL 说明），修好导出后确认 `custom_events` 有 7 条：`Team Meeting`(10-21)、`gggg`、`EditTest`、`Test2`、`AutoTest`×3（后 5 条都是 10-23 08:00-09:00）。P1 的清理任务**仍然待做**。
3. **「周三 19:20 军事理论与劳动教育并排重叠」已不成立。** 现在劳动教育在**周一** 19:20（仅第 9 周）、军事理论在周三 19:20，不再重叠——即当前没有可验证并排逻辑的真实样本。

**另一个当场修掉的问题**：课表页在 `items.isEmpty()` 时无条件显示「暂无课表数据 / 请先完成统一认证登录」，而本周（第 8 周）所有课程都不排课，导致已登录用户被误导去重新登录。已改为按 `hasCachedTimetable()` 分流：有缓存时显示「本周没有课程安排 / 第 N 周没有排课（军训、停课或集中实践周常见）」，无缓存才提示登录。改动仅在 `TimetableScreen.kt`，已装机验证（第 7 周显示新文案，第 9 周课表不受影响）。

## 3. 关键背景与领域知识

**作息时间（2026-10-24 用户确认，唯一真相在 `QzHtmlParser.sectionTimeStart/End`）**

| 大节 | 节次 | 起止 |
| --- | --- | --- |
| 第 1 节 | 1-2 | 08:10 – 09:45 |
| 第 2 节 | 3-4 | 10:15 – 11:50 |
| 第 3 节 | 5-6 | 14:30 – 16:05 |
| 第 4 节 | 7-8 | 16:25 – 18:00 |
| 晚上 | 9-11（三节连排） | 19:10 – 21:35 |

- 每小节 45 分钟，同一大节内小节间休息 5 分钟
- **教务系统里晚上的课只标「09-10节」（`sectionCount=2`），但实际占用整个晚课时段**，所以 9 节及以后不按小节切割，统一取 19:10–21:35
- 时间轴（`AxisTime`）午休压缩区间同步改为 **11:50–14:30**

- **课表 DOM 能提供什么、不能提供什么**（2026-10-26 实测 `recon/blocks.json`）：
  每个 `<font>` 只含 `课名` / `N-N,M-N(周)` / `教室`（+ 整格抓到的 `教师`）。
  **没有**学分、课程代码、课程性质、开设学院、考察方式、行政班级、同班人数——
  这些是合工大 `jxglstu` 接口独有的字段，强智课表页不产出。要补只能另抓
  「成绩查询 `/jsxsd/kscj/cjcx_list`」或「培养方案」，两者都需要登录态下交互提交、且结构未探测。

- 目标学校教务系统是**强智系**，域 `https://jw.cdut.edu.cn`，课表页 `/jsxsd/xskb/xskb_list.do`
- 登录实际走 **CAS 统一认证**（`cas.paas.cdut.edu.cn`），强智自有登录页只是壳，提交即 302 跳 CAS
- CAS 页面具备：`execution` token、瑞数动态防护、密码 `__RSA__` 加密（`encrypt.encrypt(password)`）、可能 MFA
- **因此纯 HTTP 客户端登录不可行**，登录动作必须在 WebView 内完成（这是硬约束，不是选择）
- 课表页的 DOM 结构与合工大不同：教师 `<font>` **不在** `div.kbcontent1` 内，必须抓整格 `cells[c].querySelectorAll('font')`
- Room 使用 **WAL** 模式。**导库必须用 `adb exec-out`，不能用 `adb shell cat`**：后者会把二进制里的 `0x0A` 转成 `0x0D 0x0A`，WAL 的 checksum 随之失效，SQLite 整段丢弃 WAL 只读主库，表现就是「明明有数据却查出 0 条」。实测 `ls` 报 WAL 57712 字节、`shell cat` 得到 57740（多 28 字节），换成 `exec-out` 后立刻读到 7 条。`recon/check_db.py` 已按此修好。

## 4. 已确认事实与数据

### 工程

- 项目根：`D:\workbuddy_workplace\cdut-helper`
- 包名：`com.yh.schedule`；`AndroidManifest.xml` 中 `android:label="@string/app_name"`；`res/values/strings.xml` 第 3 行 `<string name="app_name">砚湖日程</string>`
- 参考源码：`D:\浏览器Neat下载\HFUT-Schedule-dev\HFUT-Schedule-dev`（`ui/screen/home/focus/FocusUI.kt`、`funiction/FocusItems.kt`、`funiction/AddEvent.kt`、`calendar/timetable/ui/TimeTableCommonSquare.kt`、`calendar/common/Utils.kt`、`cube/sub/FocusCardSettings.kt`、`viewmodel/network/LoginViewModel.kt`、`ui/screen/login/LoginUI.kt`）
- 依赖：`androidx.compose:compose-bom:2024.10.01`、`com.squareup.okhttp3:okhttp:4.12.0`、`androidx.datastore:datastore-preferences:1.1.1`；**无** `security-crypto`
- JAVA_HOME：`D:\workbuddy_workplace\cdut-helper\jdk21\jdk-21.0.2`；Gradle：`.\gradle-dist\gradle-8.10.2\bin\gradle.bat`

### 课表解析（实测）

- 缓存结果：`TOTAL = 27`、`WITH_TEACHER = 27`
- 课名原文含后缀：`大学英语I(周一34-02)`
- 教师 font 数量证据：按 `div.kbcontent1` 取只有 `n: 3`（课名/周次/教室）；按整格取到 14 个（含 `教师/李雪琳`）
- 第 9 周（10/26–11/1）抽样：`周一 08:00-09:40 | 高等数学（B）（1） | 节1 | weeks=[2,3,4,5,9..19] | （宜）乙103`；`周一 10:00-11:40 | 大学英语I | 节3 | weeks=[2,3,4,5,9..16] | （宜）乙508`
- 重叠实例：周三 19:20 同时有「军事理论」与「劳动教育」

### 登录链路探测（原始数据）

- `GET https://jw.cdut.edu.cn/jsxsd/verifycode.servlet` → `STATUS = 200`、`CONTENT_TYPE = image/jpeg;charset=UTF-8`、`BYTES = 1891`（另一次 2045）、`MAGIC = ffd8ffe000104a46`、`LOOKS_LIKE_IMAGE = True`
- 同时下发 Cookie：`JSESSIONID`、`SERVERID=pc5`、`sMLAeTqisZbFO`（瑞数指纹）
- `POST /jsxsd/xk/LoginToXk` → `final_url= https://cas.paas.cdut.edu.cn/cas/login?service=http%3A%2F%2Fjw.cdut.edu.cn%2Fsso%2Flogin.jsp%3FtargetUrl%3Dbase64aHR0cDovL2p3LmNkdXQuZWR1LmNuL0xvZ29uLmRvP21ldGhvZD1sb2dvblNTT2NkbGdkeA%3D%3D`
- CAS 页解析：`INPUT_COUNT = 5`；`id=userAccount/name=userAccount/type=text`、`id=userPassword`、`id=RANDOMCODE`、`loginMethod`(hidden)、`encoded`(hidden)；`JSEncrypt: 0`、`__RSA__: 0`（该次抓到的是强智壳页）
- 另一次抓到 CAS 真实页：`el-form-item: 0`、`el-input: 27`、`login-form: 1`；输入框父容器类名 `sw-input el-input el-input--prefix sw-input-circular`；含 `if (encryptEEnabled && password.indexOf('__RSA__') < 0) { var encodedPassword = "__RSA__"+encrypt.encrypt(password); }`
- `#username` 存在**两套同 id**（`type="hidden"` + 可见），`getElementById` 只能拿到 hidden 那个

### 运行环境故障

- 报错原文：`Medium Phone API 37.0 is already running as process 14816.`
- `adb devices` → `emulator-5554          offline`；`Get-NetTCPConnection` 显示 5554/5555 由 `OwningProcess 14816` 监听；`adb connect 127.0.0.1:5555` → `failed to connect to 127.0.0.1:5555`（结论：adbd 僵死）
- `hardware-qemu.ini.lock` 是**目录**，内含 `pid` 文件（Length 0）
- 删除被拦原文：`[safe-delete][SAFE_DELETE_FAIL_CLOSED] {"target":"C:\\Users\\l\u0027l\\.android\\avd\\Medium_Phone.avd\\hardware-qemu.ini.lock","reason":"trash-failed"...}`
- 自行启动 emulator 的日志：`INFO | Boot completed in 37705 ms` 随后 `Wait for emulator (pid 10656) 20 seconds to shutdown gracefully before kill`（后台任务结束时被连带关闭）
- 最终由用户从 Android Studio 启动成功：`emulator 53216` / `qemu-system-x86_64 12052`，`StartTime 2026/10/23 22:08:43`

### 数据层

- Room `version = 3`；表 `custom_events`、`exam_records`；`PRAGMA user_version = 3`
- 新增列：`endDate`、`type`、`remark`
- 曾用 `.fallbackToDestructiveMigration()`（升级会清表），已补 `MIGRATION_2_3`

## 5. 已做决策及理由

| 决策                                                                      | 理由                                                                                           | 被否决方案及原因                                                                       |
| ----------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| 课表块内只显示 `开始时间/课程名/地点/结束时间`                                              | 用户明确要求：`老师可以不在框内显示，框内只需要显示课程名称、时间、地点就行了`                                                     | `课程名@教师` 方案（初版）——用户否决                                                          |
| 课程块上下各延伸 5dp，时间固定贴顶/贴底                                                  | 用户要求：`每一个框目前可以适当再拉长一点，上下显示的时间是顶着上下边缘的，位置一直不变`；初版 SpaceBetween 无 weight，内容多时把结束时间挤出块外         | 无 padding 的紧贴方案——会与相邻块视觉粘连                                                     |
| 地点简化 `（宜）乙103 → 乙103`                                                   | 7 列布局下完整地址被 ellipsis 成 `（宜）…`，用户反馈 `地址都看不见`                                                  | 显示完整地点——宽度不够                                                                   |
| 同日重叠课程**先合并区间成组、组内均分列宽**                                                | 对齐源码 `layoutSquaresForDay` 的"合并重叠组"思想，时间轴布局下纵向堆叠会错位，故组内改横向均分                                 | 原实现按"自身重叠集"算宽度 → 宽度不一致、互相压盖（真实 bug）                                            |
| 聚焦页页签 = `重要安排 / 其他事项`，靠滑动切页                                             | 用户提供原版截图并指出 `右上角没有今明的选项吧`                                                                    | 顶部「今天/明天」pill（我自加）——用户否决；「考/绩/告」快捷入口（我自加）——用户否决：`这不是该出现的`                      |
| `showTomorrow = now >= (todayCourses.lastOrNull()?.endTime ?: "00:00")` | 严格对齐源码 `compareTime(lastTime) != NOT_STARTED`；**今天无课时 lastTime 为 "00:00"，恒成立**，明日日程才会进「重要安排」 | 我原先写的 `todayCourses.isNotEmpty() && all{ENDED}` → 今天无课时明日日程不提醒（用户报的 bug 正是这个）  |
| 已下课的课**直接消失**，不划删除线保留                                                   | 源码 `if(switchShowEnded) 显示 else if(state != ENDED) 显示`，默认 `switchShowEnded=false`            | 删除线保留——用户要"消失一条"                                                               |
| 登录页 = 纯原生表单 + 隐藏 WebView 后台登录                                           | 用户明确要求：`我只希望一个自创登录页面出现`、`你后台不会自己载入官网登陆页面吗`                                                   | ① 全屏 WebView 登录（原生外壳+网页）——用户嫌 `脱节`、`重复`；② 纯 HTTP 登录——实测 CAS 302 + RSA + 瑞数，不可行 |
| 账密存 DataStore，登录成功才保存                                                   | 对齐源码 `saveInput` → `saveString("Username"/"Password")`                                       | `security-crypto` 加密——**未做，待用户确认**（依赖未引入）                                      |
| 登录失效：红色横幅 + `重新登录`                                                      | 用户要求 `与官网失去连接的时候提醒用户自己重新登录`；有缓存课表时原逻辑把错误降级为 toast，用户看不到入口                                    | 只弹 toast + 提示"去服务页退出"——路径绕                                                     |
| 数据库加 `MIGRATION_2_3`                                                    | 加字段时 `fallbackToDestructiveMigration()` 会清空日程表                                               | 直接升版本不清库——会崩                                                                   |

**2026-10-24 补充（添加日程页重做）**

| 决策 | 理由 | 被否决方案及原因 |
| --- | --- | --- |
| 添加/修改日程改为**全屏页**（原为 AlertDialog） | 用户给原版截图要求"右下角加号参考这个"；字段、顺序、文案照搬源码 `AddEventUI` | 继续用 AlertDialog——字段挤不下，日期/时间只能一个弹窗开一次 |
| 日期用 `DateRangePicker`，**两次点击定范围** | 用户明确要求"第一次点起始日，第二次点确认结束日"，一次交互完成范围选择 | 起止各开一次日期弹窗——要开两次，且容易漏选结束日 |
| 时间用自绘 3D 滚轮（`WheelPicker`） | 原版就是滚轮 + 5 分钟步进；M3 `TimePicker` 是表盘样式，视觉与原版不符 | M3 `TimePicker`——外观不一致 |
| 预览卡按「时间 / 标题 / 备注」纵向排 | 用户要求标题对应课程块课程名的位置、备注对应教室（地点）的位置 | 普通 ListItem——三行主次关系与课程块对不上 |
| 不硬编码 zh-CN 强转日历语言 | 实测模拟器 `ro.product.locale=en-US`，日历英文是系统语言导致的；真机中文系统本就正常，强转属于多余全局副作用 | `Locale.setDefault(zh-CN)` 与 `CompositionLocalProvider(LocalConfiguration)` 两种都试过——对 M3 日期组件均无效 |
| 日程删除入口放**编辑页右上角**（红色垃圾桶） | 用户要求「点课表上的自建日程，右上角有删除」，源码 `AddEventScreen` 在 `eventId>0` 时 actions 里正是 copy + delete 两个图标 | 只在聚焦页长按删除——课表页点进去没有删除入口，用户找不到 |
| 课表点**日程**直接进编辑页，点**课程**才弹详情 | 源码点 FOCUS 块跳 `AddEventDestination(eventId)`，点课程块才弹 `TimeTableDetail` | 一律弹只读详情——日程无法从课表直接改/删 |
| 窄块不再整块丢弃文字 | 用户要求并排各占一半时也要显示信息；原版半框里就是逐字换行照常显示 | 原 `innerW < 26.dp` 只画色块——50:50 时 `innerW≈22.5dp` 正好落在这个阈值下，直接变纯色块（用户报的就是这个） |
| 课程详情改为**全屏页**，布局照 `TotalCourseUI.DetailItems` | 用户给截图要求"每个课程点击显示具体相关信息"；`DetailItems` 就是这个页面 | 继续用 `CourseDetailDialog` 小弹窗——放不下 11 个字段 |
| 复制日程照源码 `enableCopy` 分支 | 源码编辑态 actions 里是「复制 + 删除」两个图标；`enableCopy` 时标题变"复制日程"、两个图标都不显示、底部按钮变"添加"（走 insert） | 简单再存一份——标题/按钮文案与源码不符 |
| 课程详情**拿不到的字段不渲染**，不占位、不编造 | 强智课表 DOM 只有 课程名/周次/教室/教师（见 §3），其余字段无来源；用户红线是"不得瞎编乱造" | 填"—"或写死示例值——属于编造 |
| 课表默认排 **5 天**，周末有内容才排 7 天；右上角另给手动开关 | 用户要求"周末没课只显示前五天，增加观感面积"；源码 `CalendarStyle.rowCount = if (showAll) 7 else 5`，靠 `list.find { dayOfWeek == 6 || 7 }` 判定 | 永远 7 天——列宽只有 50dp，课程名被挤成竖排；永远 5 天——周末有课就看不见 |
| 手动切换**只对当前周生效**，切周后重新自动判定 | 源码只做单向自动展开（`onSwapShowAll(true)`），收起靠手动；用户要的是切到哪周就按那周实际情况排，所以 `LaunchedEffect(items)` 里每次重算 | 把 showAll 做成全局 `rememberSaveable`（源码做法）——切到没周末的周会保持 7 天，与用户要求不符 |
| 块内**地点优先占位，课程名吃剩余高度** | 用户明确要求"均分时可以牺牲课程名，但地点必须显示"，并点名「大学生职业发展」「大学体育」两门长名课丢了地点 | 名字和地点各 `weight(1f, fill=false)` 平分——实测各得一半，名字只剩 1 行 4 个字，地点虽然出来但信息量损失过大 |
| 刷新按钮：`refreshing` 时图标换转圈 + 手动刷新成功弹 toast | 用户反馈"点了没反应"；原实现只在 `items.isEmpty()` 时显示顶部进度条，有缓存时全无反馈 | 只加完成 toast——抓取要 2–5 秒，中途仍像没反应 |
| 全屏页从 **Dialog 改为 Compose 树内承载** | Dialog 是独立 window，共享元素转场做不了；且 Dialog 里取不到导航栏 inset（之前只能写死 28dp 底部留白） | 继续用 Dialog——拿不到 `boundsTransform`，也不会有容器转换 |
| overlay 状态**上报给 `MainScaffold`**，打开时收起底部导航栏 | overlay 原本在 `Scaffold` 的 content 内，盖不住 `bottomBar`，页面看起来像内嵌弹层 | 用 Dialog 盖住——回到老问题；或硬抵抗——`Scaffold` 会裁剪 |
| 暂不追求「列表保持不动 + 只有 FAB 变形」 | 当前 `AnimatedContent` 的 else 分支里含整个列表，切换时列表会跟着淡出；源码因 overlay 是独立路由，列表不动。这一层要再拆一次结构 | 现在就拆——收益是 200ms 的观感差异，风险是动 200 行列表代码 |

## 6. 约束与红线

**技术约束**

- 目标平台：原生 Android（Kotlin + Jetpack Compose + Material3），**不是** WebView 套壳 App
- 登录必须在 WebView 内完成（CAS 的 `execution`/瑞数/`__RSA__` 无法在原生复现）
- 课表抓取必须用真实 WebView（瑞数防护，普通 HTTP 会被拦）；抓取用 1dp 隐藏 WebView + 注入 JS 读 DOM
- WebView 尺寸若被压到 1dp，**网络请求会失败**（`net::ERR_NAME_NOT_RESOLVED`）；必须用 `requiredSize` 保持内部真实尺寸

**格式/风格约束**

- 中文沟通；先给结论
- 改动必须能说清"改了什么文件、为什么"；不允许"我优化了一下"这类糊涂账
- UI 以参考源码为准，**不得擅自增加源码里没有的元素**

**禁止事项**

- 不得凭猜测添加/删减源码中的功能（用户原话：`不得瞎编乱造，随意猜测、增加、减少`）
- 不得用 `rm -rf`/批量删除处理用户个人目录；AVD 锁文件在当前环境无法删除（safe-delete 拦含单引号的路径）
- 不得让 App 自己启动 emulator（会话结束会连带杀掉子进程）

**合规/安全**

- 密码目前明文存 DataStore，属于已知风险，需用户确认是否上加密
- 登录页文案已改为"勾选记住后会保存在本机（应用私有目录）"，与实际行为保持一致

## 7. 已完成工作与产出

**文件（均在 `D:\workbuddy_workplace\cdut-helper\app\src\main\java\com\yh\schedule\`）**

- `ui/screens/TimetableAxis.kt`：`layoutDay` 重写（合并重叠组 + 均分列）、`simplifyPlace`、窄块处理；**2026-10-24 午休压缩区间改为 11:50–14:30**；**2026-10-26 窄块改为缩字号继续显示文字**（阈值 30dp 切字号、≥16dp 就渲染，中间区加 `clipToBounds`）；**同日加 `dayCount` 参数**——5 天时列宽翻倍、日期数字 15sp、块内字号放大到 12.5/10.5sp
- `ui/screens/TimetableScreen.kt`：`onRelogin` 参数、登录失效横幅（有缓存也不再吞成 toast）、文案去掉"请到服务页退出"；**2026-10-24 新增 `hasCache` 分流**——空周不再误报"请先登录"；**2026-10-26 加 `showWeekend`（5/7 天自动判定）+ 右上角手动开关 + 刷新按钮转圈与「课表已更新」toast**
- `ui/screens/FocusScreen.kt`：双页签、课程三态、`showTomorrow` 逻辑、日程过期折叠、`+1` 徽标、长按删除、点击编辑、`refreshTick`
- `ui/screens/CourseDetailDialog.kt`：课程详情（时间/地点/教师/类型）
- `ui/screens/AddEventScreen.kt`：**2026-10-24 新增**，全屏添加/修改日程页（类型分段 + 说明卡 + 标题 + 备注 + 起止卡 + 时间文案 + 底部预览卡/添加按钮），用全屏 Dialog 承载，FAB 与课表双击空白共用；**2026-10-26 加 `onDelete`**——`editing != null` 时右上角显示红色删除图标，点开「要删除此项吗」确认框
- `ui/screens/DateTimePickers.kt`：**2026-10-24 新增**，`YhDateRangePickerDialog`（M3 `DateRangePicker`，两次点击定范围）+ `YhTimeRangePickerDialog`（起止两组滚轮）+ `YhTimePicker`
- `ui/screens/CourseDetailScreen.kt`：**2026-10-26 新增**，全屏课程详情页（蓝色大标题 + 图标行布局，照 `TotalCourseUI.DetailItems`）；`weeksRangeText()` 把 weeks 压成「1~6,9~19周」，`scheduleTexts()` 拼「1~5周 周一 第一节~第二节 乙103 李雪琴」
- `ui/screens/WheelPicker.kt`：**2026-10-24 新增**，移植源码的 3D 滚轮（LazyColumn + snap + rotationX 翻转 + 循环填充伪造无限滚动）
- ~~`ui/screens/AddEventDialog.kt`~~：已被 `AddEventScreen.kt` 取代并删除
- `ui/screens/CasLoginScreen.kt`：纯原生登录 + 隐藏 WebView + 注入 + 自动点登录 + 轮询诊断 + 验证码降级展开
- `ui/AppNav.kt`：`MainNav` 增加 `showRelogin` 覆盖层、`MainScaffold(onRelogin)`
- `core/QzJsTimetableParser.kt`：整格 font 解析、`splitCourses`、`isValidName`、`weeksOf/expandWeeks`
- `core/QzHtmlParser.kt`：**作息时间唯一真相** `sectionTimeStart/End`（2026-10-24 重写：1-2/3-4/5-6/7-8 节按小节查表，9 节及以后统一 19:10–21:35）
- `core/QzClient.kt`：`defaultSectionStart/End` 改为转调 `QzHtmlParser`，不再保留第二份作息副本
- `data/MockData.kt`：演示数据 12 条课程时间同步为新作息
- `core/Models.kt`：`CustomEvent` 增 `endDate/type/remark`；`enum class EventType { SCHEDULE, DEADLINE }`
- `core/SessionStore.kt`：`saveCredentials/clearCredentials`、`credentials()`
- `data/AppDb.kt`：v3 + `MIGRATION_2_3` + `@Update`
- `data/Repository.kt`：`addEvent(e): Long`、`updateEvent`、`loginExpired` StateFlow、`markLoginExpired/clearLoginExpired`、跨天日程按天拆分；**2026-10-24 新增 `withSectionTime()`**——`timetable()` 读到缓存后按节次重算起止时间，改作息不用清缓存、不用重新登录
- `recon/*.py`：`check_db.py`（已改用 `adb exec-out`）、`check_session.py`（解析 DataStore 会话/课表缓存）、`ui_dump.py`（dump 带坐标的中文控件）、`tap_text.py`、`add_event_auto.py`、`probe_login_api.py`、`probe_login_post.py`、`probe_cas_form.py`、`probe_cas_dom.py`、`clear_avd_lock.py`

**关键命令**

```
$env:JAVA_HOME = "D:\workbuddy_workplace\cdut-helper\jdk21\jdk-21.0.2"
Set-Location "D:\workbuddy_workplace\cdut-helper"
.\gradle-dist\gradle-8.10.2\bin\gradle.bat assembleDebug --no-daemon --console=plain

adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell run-as com.yh.schedule cat databases/yh_schedule.db      # 需同时导 -wal / -shm
adb shell uiautomator dump /sdcard/uia.xml                          # 定位控件真实坐标
```

**已验证结果**

- 第 9 周课表（10/26–11/1，2026-10-24 复验）：周一 5 门、周二 4 门、周三 2 门、周四 2 门、周五 2 门；课程块显示 `08:00 / 高等数学（B）（1） / 乙103 / 09:40`。**注意**：`周三 19:20 军事理论与劳动教育并排`这条已失效（见 §2.1），劳动教育移到周一 19:20
- 聚焦页：「重要安排」显示 `😄 明天 无课`、`+1 10月23日 第8周 周五 08:00-09:00 gggg 明日`；「其他事项」显示 `展开已过期日程（7）` 并可展开
- 日程：点击弹出「编辑日程」且字段全预填；新增保存写入成功（`insert ok, newId=7`）
- 登录页：纯原生显示、无官网页；假账号提交后状态变 `需要输入验证码，请在下方完成后会自动继续` 并自动展开后台页

**未验证但有产出**

- ~~真实账号一次登录成功（只用 `20260001/pw123456` 测试过）~~ → 已推翻：DataStore 里存的是真实学号 `202619020320`，说明用真实账号成功登录过（过程细节无记录）
- `MIGRATION_2_3` 的真实升级路径（当时库已是 v3）
- 同日多课程并排渲染：现有数据已无重叠样本，暂无真实案例可验

## 8. 当前阻塞、风险与未解决问题

1. ~~**App 处于未登录状态**~~ → 已推翻，见 §2.1：App 是登录态，真实学号已存
2. 真实登录是否会要求验证码、能否一次成功：未知（登录态存在，说明此前成功过一次，但过程未记录）
3. 密码明文存储（DataStore）——安全风险，未决
4. 库里残留测试日程 7 条，其中 `Team Meeting`/`AutoTest`×3/`Test2`/`EditTest` 是测试产物，`gggg` 是用户自己的
5. ~~周次口径存在冲突~~ → 已确认 2026-10-24 为第 8 周，`termStartMonday=2026-08-31` 正确
6. 环境坑：AVD 锁文件删不掉；不能让 App 自己启动模拟器

## 9. 待办清单

| 优先级 | 任务                                                | 依赖         | 验收标准                               |
| --- | ------------------------------------------------- | ---------- | ---------------------------------- |
| ~~P0~~ | ~~用真实账号完成一次登录并确认课表刷新~~ → **已完成**，App 为登录态，课表 28 条缓存完好 | — | 第 9 周课表满课，时间/课名/地点正确 |
| ~~P0~~ | ~~确认登录页在真实流程下不暴露官网页~~ → 待用户真实重登时复核 | — | 全程只看到原生页面（除验证码降级） |
| ~~P0~~ | ~~确认「本周算第几周」~~ → **已确认：2026-10-24 属第 8 周**，`termStartMonday=2026-08-31` 正确，不要改 | — | 与教务一致 |
| P1  | 决定密码是否加密存储                                        | 用户拍板       | 引入 `security-crypto` 或明确接受明文       |
| P1  | 清理测试日程（Team Meeting / AutoTest×3 / Test2 / EditTest） | —          | 列表只剩用户自己的 `gggg`                    |
| P2  | 定 App 正式名称                                        | 用户决定       | `strings.xml` 更新并重新构建              |
| P2  | 核实学期起始周（第 7 周 vs 第 8 周）                           | 用户向同学/教务确认 | `termStartMonday` 与官网周次一致          |
| P3  | 照源码补 `ScheduleItem` / `NetCourseItem`（课表日程、网课倒计时） | —          | 与源码行为一致（用户曾问"要不要补"，未答复）            |

## 10. 关键术语、命名与缩写

- **砚湖日程**：本 App 的 `app_name`
- **CAS**：`cas.paas.cdut.edu.cn` 统一身份认证
- **强智 / jsxsd**：教务系统路径前缀，课表 `/jsxsd/xskb/xskb_list.do`
- **瑞数**：`RiverSecurity` 动态防护，页面会下发 `sMLAeTqisZbFO` 之类指纹 Cookie
- **`__RSA__`**：CAS 页面登录前对密码做的加密前缀
- **`showTomorrow`**：源码 `compareTime(lastTime) != NOT_STARTED` 的等价布尔
- **`lastTime` / `lastEndTime`**：今天最后一节课的结束时间；**今天无课时为 `"00:00"`**
- **FOCUS / SCHEDULE / DEADLINE**：自定义事项类型（日程 / 截止）
- **`zzz` 相关**：无

## 11. 必须保留的原文片段

**① CAS 登录地址（登录页直接加载此 URL，`service` 不可省）**

```
https://cas.paas.cdut.edu.cn/cas/login?service=http%3A%2F%2Fjw.cdut.edu.cn%2Fsso%2Flogin.jsp%3FtargetUrl%3Dbase64aHR0cDovL2p3LmNkdXQuZWR1LmNuL0xvZ29uLmRvP21ldGhvZD1sb2dvblNTT2NkbGdkeA%3D%3D
```

用途：不解码、不改写，直接 `loadUrl`。`targetUrl` 解码为 `http://jw.cdut.edu.cn/Logon.do?method=logonSSOcdlgdx`。


**② 注入账密 + 自动点登录（关键 JS，两个坑都在里面）**
```javascript
function setVal(id, v){
  var els = document.querySelectorAll('#' + id);
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    if (el.type === 'hidden' || el.offsetParent === null) continue;   // 必须跳过 hidden，否则填到看不见那个
    el.value = v;
    el.dispatchEvent(new Event('input',{bubbles:true}));               // Element UI 需要事件才更新绑定值
    el.dispatchEvent(new Event('change',{bubbles:true}));
  }
}
setVal('username', '...'); setVal('password', '...');
setTimeout(function(){
  var btns = document.querySelectorAll('button, .el-button, input[type=button]');
  for (var i = 0; i < btns.length; i++) {
    var t = ((btns[i].innerText || btns[i].value || '') + '').replace(/\s/g, '');
    if (t.indexOf('登录') >= 0 && t.length <= 4) { btns[i].click(); return; }
  }
}, 400);
```

**③ 结果诊断（CAS 是 SPA，失败不刷新，必须轮询）**
```javascript
var t = document.body ? (document.body.innerText || '') : '';
// #captcha 可见 → 'CAPTCHA'；含"密码错误/用户名或密码" → 'BAD'；含"锁定/冻结" → 'LOCKED'；否则 'WAIT'
```

**④ 报错原文**
```
Medium Phone API 37.0 is already running as process 14816.
emulator-5554          offline
failed to connect to 127.0.0.1:5555
net::ERR_NAME_NOT_RESOLVED
[ksp] androidx.room.RoomKspProcessor was unable to process 'com.yh.schedule.data.AppDb' because not all of its dependencies could be resolved.
e: ...CasLoginScreen.kt:153:17 Unresolved reference 'diagnose'.
```

**⑤ 用户关键指令原文（验收口径，不可改写）**
- `你查官网的功夫还没有到位啊。连源码都抄不清楚D:\浏览器Neat下载\HFUT-Schedule-dev`
- `你仔细看源码了吗`
- `我需要你全部精读细读，不要自己瞎编乱造。继续完善，达到完全照搬且适用的程度`
- `依据参看源码逻辑，不得瞎编乱造，随意猜测、增加、减少`
- `不要擅自更改增加多余代码和偷工减料`
- `我只希望一个自创登录页面出现`
- `以后再说名字`

## 12. 给新对话的第一条消息

```
接手项目：砚湖日程（成都理工大学 Android 课表 App，Kotlin + Compose + Material3）
工程：D:\workbuddy_workplace\cdut-helper，包名 com.yh.schedule
参考源码（必读）：D:\浏览器Neat下载\HFUT-Schedule-dev\HFUT-Schedule-dev

【当前状态】
课表页与聚焦页已按源码整治完并实机验证：课表缓存 28 条、教师全、英语课已恢复；
聚焦页为「重要安排 / 其他事项」双页签（滑动切页），今日课尽自动转明日，过期日程默认折叠。
登录页为纯原生方案（官网 CAS 页在 1dp 隐藏 WebView 里后台执行，注入账密+自动点登录+轮询诊断）。
**App 现在是【已登录】状态**（DataStore 存有真实学号 202619020320 + 明文密码 + TGC + 28 条课表缓存），
但本周（第 8 周）所有课程都不排课，课表页天然是空的——不要再误判成"掉登录"。

【硬约束，不要推翻】
1. 登录必须在 WebView 内完成：CAS 有 execution token、瑞数、密码 __RSA__ 加密，纯 HTTP 必失败（已实测 302 到 cas.paas.cdut.edu.cn）。
2. 课表抓取也必须用 WebView（瑞数）；抓取器用 1dp 隐藏 WebView，注入 JS 读 DOM。
   注意：WebView 被压到 1dp 会导致网络请求失败（net::ERR_NAME_NOT_RESOLVED），
   正确写法是父容器 size(1.dp).clipToBounds() + AndroidView(Modifier.requiredSize(360.dp, 640.dp))。
3. 登录页直接 loadUrl(CAS_LOGIN_URL)（带 service 的完整 CAS 地址），不要用 JW_HOME_URL——它不会跳登录页。
4. CAS 是 SPA，登录失败不刷新页面，onPageFinished 不触发 → 必须协程轮询页面文本判断 CAPTCHA/BAD/LOCKED。
5. 课表 DOM：教师 font 在 div.kbcontent1 之外，必须抓整格 fonts；课名含 "(周一34-02)" 后缀需清洗。
6. UI 严格照源码：不得自加"今天/明天"页签、"考/绩/告"入口等源码没有的东西（已被用户否决过）。
7. 课程块内只显示 时间/课程名/地点（教师只放点击详情）。

**【作息时间（已确认，改时间只动 QzHtmlParser.sectionTimeStart/End）】**
1-2 节 08:10-09:45；3-4 节 10:15-11:50；5-6 节 14:30-16:05；7-8 节 16:25-18:00；
晚上 9-11 节三节连排 19:10-21:35（教务只标 09-10节，但要按整段显示）。
时间轴午休压缩区间 11:50-14:30。

【添加日程页（2026-10-24 按原版重做）】
聚焦页右下角 FAB / 课表双击空白 → 全屏 `AddEventScreen`（不再是 AlertDialog）。
日期：`DateRangePicker`，点第一下 = 起始日，点第二下 = 结束日，一次定范围。
时间：自绘 3D 滚轮，日程给「起 + 止」两组、DDL 只给「截止」；分钟 5 分钟步进。
预览卡三行 = remark(时间) / 标题 / 备注，刻意对齐课程块的 时间/课程名/地点。
类型说明卡文案、按钮文案（选择日期范围/选择截止日期等）均照抄源码，不要改写。

【日程的增删改入口（2026-10-26 补齐）】
课表上点**自建日程块** → 直接进编辑页（`AddEventScreen(editing=...)`），右上角「复制 + 删除」→
删除走「要删除此项吗」→ `repo.removeEvent(id)`；复制走 `copyMode`（标题变"复制日程"、图标隐藏、按钮变"添加"、保存时 id=0）。
点**课程块** → 全屏 `CourseDetailScreen`（`repo.timetable(week).filter { it.name == 同名 }` 一起传入，拼上课安排）。
窄块（并排冲突）也显示完整三行信息，不要再加"太窄就只画色块"的门槛。

【课程详情页的已知缺口】
只显示 课程名 / 周数 / 教师 / 学期 / 上课安排。
类型、同班同学、学分、开设学院、考察方式、课程代码、班级 **没有数据源**（强智课表不产出，见 §3）。
用户已知情并要求"先试试"；若后续要补，方向是抓 `/jsxsd/kscj/cjcx_list`（成绩页，含学分+代码+性质）。

【课表 5 天 / 7 天】
默认只排周一到周五（列宽翻倍、字号放大）；当该周周六/周日有课程或自建日程时自动排 7 天。
右上角有手动开关（展开/收起图标）。手动结果只对当前周有效，切周后按新一周重新判定。
判定逻辑在 `TimetableScreen` 的 `LaunchedEffect(items) { showWeekend = items.any { dayOfWeek == 6 || 7 } }`。

【待办（按优先级）】
P0 无。
P1 决定密码是否加密（现为 DataStore 明文，与源码 saveInput 一致）；清理测试日程 Team Meeting/AutoTest×3/Test2/EditTest（只剩用户的 gggg）。
P2 定 App 名（现为「砚湖日程」，用户说以后再说）。

【先确认的问题】
1. 真实账号登录时是否出现验证码？一次成功吗？
2. ~~学期起始周以哪个为准？~~ 已确认：第 8 周（10/19–10/25），termStartMonday=2026-08-31 正确。
3. 密码要不要上加密存储？
4. App 正式名称定了吗？

【环境注意】
- 构建：JAVA_HOME=D:\workbuddy_workplace\cdut-helper\jdk21\jdk-21.0.2，
  .\gradle-dist\gradle-8.10.2\bin\gradle.bat assembleDebug --no-daemon --console=plain
- 模拟器只能由用户在 Android Studio 启动，不要用命令行代劳（会话结束会连带杀掉子进程）
- AVD 锁文件在当前环境删不掉（safe-delete 对含单引号的用户名路径失败），需要时让用户手动删
- 查 Room 数据要用 `adb exec-out run-as ... cat`，**不能用 `adb shell cat`**（会把 0x0A 转成 0x0D 0x0A，
  导致 WAL checksum 失效、整段 WAL 被丢弃，表现为明明有数据却查出 0 条）。`recon/check_db.py` 已修正。
- adb 路径：`C:\Users\l'l\AppData\Local\Android\Sdk\platform-tools\adb.exe`（项目目录下没有 platform-tools）
- 模拟器物理分辨率 1080x2400；`uiautomator dump` 给出的坐标就是真实像素，**不要再乘缩放比**
- 排查脚本：`recon/check_session.py`（解析 DataStore 会话与课表缓存）、`recon/ui_dump.py`（dump 带坐标的中文控件）
```

---

**自检**：新对话只看本文件，能知道 —— 目标（成理课表 App 对齐 HFUT 源码）、当前状态（两页已完成、**已登录**、本周第 8 周无课是正常的）、下一步（确认周次 + 3 个待确认）、红线（WebView 不可绕、UI 不得自加、不得自行启动模拟器、查库必须用 `adb exec-out`）。**能**。
