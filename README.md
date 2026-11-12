# 掌上成理

成都理工大学校园课表与日程助手（Android · Kotlin · Jetpack Compose）。

**当前版本：0.2.2（debug）** ｜ 最低系统：Android 8.0（API 26） ｜ 包名：`com.yh.schedule`

**下载安装包：[掌上成理 0.2.2-debug.apk](https://gitee.com/hellogitee-star/cdut/releases/download/Android/cdut-helper-0.2.2-debug.apk)**

> 个人自用工具，不上架应用商店。密码经 Android Keystore 加密后保存，
> 教务登录全部在官方 WebView 页面完成。

## 功能

| 模块 | 说明 |
|---|---|
| 登录 | 统一身份认证 WebView（账号密码不经过本应用）；另有演示模式 |
| 课表 | 强智教务真实数据抓取、原生时间轴、周切换、课程详情、本地缓存 |
| 聚焦页 | 今日 / 明日课程、自定义日程（Room）、已过期事项 |
| 查询中心 | 17 个入口；12 个教务入口原生卡片化，5 个外部服务仍为网页容器；支持搜索与长按拖拽排序 |
| 网络接入 | 校园网 WiFi 自动直连；蜂窝 / 校外 WiFi 自动 WebVPN；切换网络会静默重登 |
| 外观 | 深色模式、网页强制深色、课表块透明度 / 字号 / 行距 |
| 设置 | 学期起始日、清除课表缓存、JSON 备份 / 恢复、无限滚轮、预测性返回 |
| 自动刷新登录 | 可选；先进入主页，再用已保存账密后台刷新一次 CAS；失败在聚焦页提示（默认关闭） |
| 应用内更新 | 检查 Gitee/GitHub 的 `update.json`；优先下载 bsdiff 增量补丁，失败自动回退完整包；下载后校验 SHA256 并拉起系统安装器 |

## 下载与安装

1. 从 Gitee Release 下载 [掌上成理 0.2.2](https://gitee.com/hellogitee-star/cdut/releases/tag/Android)
   （`cdut-helper-0.2.2-debug.apk`，约 20 MB，可覆盖旧版升级）。
2. 手机允许「安装未知来源应用」后安装。
3. 打开 App → 统一身份认证登录（学号 + 密码）。
   校外或使用流量时会自动走 WebVPN，无需安装任何 VPN 客户端。

> 演示模式：登录页点「演示模式进入」，内置宜宾校区楼栋命名（甲 / 乙 / 丙楼）的示例数据，
> 不影响任何真实账号。

> **2026-10-18 联调结论（重要）**
>
> **成理 CAS 的登录链路（实测）**：
> ① 密码字段要求 `__RSA__` + JSEncrypt（PKCS1）加密（`GET /cas/jwt/publicKey` 取公钥）；
> ② 认证通过后下发 TGC，但外层**瑞数动态防护（RiverSecurity）**对非浏览器客户端返回 **412 挑战页**（`$_ts` 混淆脚本）；
> ③ 因此纯 OkHttp/Retrofit 直接 POST 不可行，**必须走 WebView 登录 + Cookie 提取**（本 App 已实现）。
>
> **强智教务（jw.cdut.edu.cn）的可用入口（实测渲染成功）**：
> - 菜单页 `/jsxsd/`、`/jsxsd/framework/xsMainV.htmlx` 在 WebView 中会因 `layout.js:56`
>   `tabMove[1].addEventListener` 报错而白屏（页面自身 bug，与 UA 无关）；
> - **功能页是独立 HTML，可正常渲染**，URL 取自菜单 `data-url`：
>
> | 功能 | URL |
> |---|---|
> | 学期理论课表 | `https://jw.cdut.edu.cn/jsxsd/xskb/xskb_list.do` |
> | 课程成绩查询 | `https://jw.cdut.edu.cn/jsxsd/kscj/cjcx_frm` |
> | 考试安排查询 | `https://jw.cdut.edu.cn/jsxsd/xsks/xsksap_query` |
> | 培养方案及完成情况 | `https://jw.cdut.edu.cn/jsxsd/pyfa/topyfamx` |
> | 选课结果查询 | `https://jw.cdut.edu.cn/jsxsd/xkgl/xsxkjgcx` |
> | 缓考申请 | `https://jw.cdut.edu.cn/jsxsd/kscj/hksq_query` |
> | 消息通知 / 已收公告 | `/jsxsd/ggly/xxtz_query`、`/jsxsd/ggly/ysgg_query` |
>
> 已验证：**课表页渲染出真实课程数据**，成绩页、考试页、选课结果等均能正常抓取。

## 课表真实数据链路（2026-10-18 打通）

```
隐藏 WebView（1dp，必须挂载布局）
  → 加载 https://jw.cdut.edu.cn/jsxsd/xskb/xskb_list.do
  → onPageFinished 后轮询 evaluateJavascript（等 loadingMask 消失、表格渲染）
  → 页面内 JS 直接把 table#timetable 结构化为 [{row,day,text}]
  → QzJsTimetableParser 解析为 Course 列表
  → DataStore 缓存 → 原生周视图渲染
```

**五个必须遵守的约束（都是实测踩出来的）**：

| # | 约束 | 后果 |
|---|---|---|
| 1 | 所有 WebView 使用**同一 UA**（本项目统一不设置，用系统默认） | PC UA 下 CAS 手机版登录页白屏；UA 不一致时瑞数判定为两个客户端，接口返回 `{"flag1":2}` 未登录 |
| 2 | 直连模式从 `https://jw.cdut.edu.cn/jsxsd/` 进入（VPN 模式走 `vpn.cdut.edu.cn/public/cas_login`），**不要手拼教务 CAS URL** | 手拼会漏掉 `targetUrl=base64...`，SSO 断在 sso/login.jsp，jw 会话建不起来 |
| 3 | 登录成功判定必须等 SSO 全链路（sso/login → Logon.do → LoginToXk） | 过早销毁登录 WebView 会丢失会话 |
| 4 | 抓取 WebView 不可 0dp | 零尺寸会跳过渲染，DOM 不生成 |
| 5 | 切换周次要留意课表周次空档 | 测试账号周次为 2-5、9-19，第 6 周本身无课 |

**课表单元格格式**（解析依据）：
```
高等数学（B）（1） 李雪琳 2-5,9-19(周) [01-02节] （宜）乙103
```
行标签：`第一二节 / 第三四节 / 中午 / 第五六节 / 第七八节 / 第九十十一节 / 备注`。

## 直连 / WebVPN 自动切换（2026-11-01）

`NetworkAccess` 在启动时与网络切换时自动判定接入方式：

- 校园网 WiFi（能直连到 `jw.cdut.edu.cn`，任意 HTTP 响应即算可达）→ 直连；
- 蜂窝流量 / 校外 WiFi（直连探测失败）→ 校园 WebVPN；
- 判定完成前显示一个短暂加载圈（WiFi 下最多等 2.5 秒）。

两种接入的会话不通用，因此接入方式一变，App 会用已保存账密静默重登一次
（记录在 `login_access_vpn`），并在选项页「网络接入 → 接入方式」显示当前模式。

VPN 登录入口：`https://vpn.cdut.edu.cn/public/cas_login?entry_id=2`；
登录后落在 `https://vpn.cdut.edu.cn/portal`，无需安装 VPN 客户端（浏览器版 WebVPN）。

```
登录：vpn.cdut.edu.cn/public/cas_login → CAS（代理域）→ vpn.cdut.edu.cn/portal
抓取：门户 bsdt 首页 → 点「教务管理系统」
      → 门户实际调用 openExpress('https://jw.cdut.edu.cn/sso/login.jsp', ...)
      → 抓取 WebView 钩住 window.open 拿到目标地址，并改写成代理域
      → jw-cdut-edu-cn-s.vpn.cdut.edu.cn:8118/sso/login.jsp
      → 代理 CAS 凭 VPN 登录拿到的 TGC 免密 → 学期理论课表
```

踩坑与结论：

| 现象 | 原因 | 修法 |
|---|---|---|
| 点「教务管理系统」后 URL 原地不动，反复重载门户 | 入口用 `window.open`/`target=_blank`，抓取 WebView 没开 `onCreateWindow`，点击等于没点 | 注入 `window.open` 钩子记录目标 URL，并把 `target=_blank` 改回当前窗口 |
| 拿到目标地址后跳到 `cas.paas.cdut.edu.cn` 登录页 | 门户给的是**直连** jw 地址；VPN 的 TGC 只在代理域 `cas-paas-...:8118` 上 | `WebConst.proxify()` 把 jw / cas / bsdt 改写成代理域；查询中心容器同样适用 |
| 登录成功但页面永远「正在检查教务系统登陆状态」 | 教务 SSO 跳转链偶发不回调 `onPageFinished` | 抓取器加 5 秒轮询兜底 |
| 成绩查询页正常渲染却弹「该入口暂未开放」 | 强智 `cjcx_frm` 是 frameset 套壳，外层 body 没有文本 | 空白检测把内层 frame 文本一起算 |

选项页「网络接入 → 自动刷新登录」：冷启动先进入主页，再在后台用已保存账密静默登录一次 CAS；
失败时聚焦页显示「后台自动刷新登录失败 / 重新登录」。默认关闭，按需开启。

## 查询中心原生展示（0.1.8 起）

教务内部页面不再直接丢网页（PC 页字号太小、手机上几乎看不清），改为：

- 隐藏 WebView 打开页面（自动选择直连 / WebVPN），Cookie 与登录态共享；
- 首屏只有筛选表单时，自动点一次「查询 / 搜索」再抓结果；
- 抓页面里的 table：
  - 两列表格 → 键值卡片（字段名 / 值）；
  - 多列表格 → **一行一张卡片**，列名做标签、单元格做值，避免横向滚动与列错位；
- 成绩 / 考试页如果只剩表头没有数据，显示明确的空态，不再把整页筛选表单当正文；
- 右上角保留「打开原始网页」与「重新读取」两个兜底入口。

覆盖：成绩查询、考试安排、培养方案、选课结果、缓考申请、学籍信息、学生评教、
空教室、补考报名、已收公告。「我的课表」直接跳到 App 自己的课表页；
图书馆、砚湖易办、后勤、缴费、邮箱仍是网页容器。

## 对照参考源码的重构（2026-10-19）

参考仓库：[`Chiu-xaH/HFUT-Schedule`](https://github.com/Chiu-xaH/HFUT-Schedule)（聚在工大）

| 原版实现 | 本项目落地 |
|---|---|
| `calendar/timetable/ui/TimeTableSingleSquare.kt`：时间轴绝对定位、`timeToY`、午休压缩、重叠分列 | `ui/screens/TimetableAxis.kt` |
| `timetable/logic/Util.kt`：`parseTimeToFloat` / `timeToY` / `DEFAULT_START_TIME=08:00` / 午休 12:10–14:00 压缩 0.1 | `AxisTime` 对象（同参数） |
| `calendar/common/DraggableWeekButton.kt`：右下角可拖动周次胶囊（点击回本周、拖动切周、文字垂直动画） | `ui/screens/WeekFloatingButton.kt` |
| `calendar/common/TopDate.kt`：表头只有日期数字，今天下划线 | `TimetableAxis` 日期表头 |
| 课程块四要素：开始时间 / 课程名@教师 / 地点 / 结束时间 | 同布局（`Arrangement.SpaceBetween`） |
| `timetable/logic/TimeTableItem.kt`：COURSE / FOCUS / EXAM 统一时间轴项 | `core/Models.kt: TimeTableItem` |
| `timetable/logic/DataAdapter.kt`：课程 + 日程 + 考试按周叠加 | `Repository.timetableItems(week)` |
| 双击课表空白 → 添加日程 | `TimetableAxis.onBlankDoubleTap` → `AddEventDialog` |

**数据正确性修正**：强智周次可能是**多段**（如 `2-5,9-19(周)`，中间为军训/停课周）。
早期只取第一段导致第 9 周以后课程全部丢失（表现为"连续数周无课"）。
现由 `QzJsTimetableParser.expandWeeks()` 展开成完整周次集合 `Course.weeks`。

## 打开与构建

要求：Android Studio（Hedgehog 以上，内置 JBR 17/21 即可）。

1. Android Studio → Open → 选择本目录（`cdut-helper`）。
2. 等待 Gradle Sync（已配置阿里云镜像，国内网络较快）。
3. 连接手机或启动模拟器 → Run。

命令行构建（需 JDK 17 ~ 21）：

```bat
set JAVA_HOME=<JDK 17~21 的安装目录>
gradlew.bat assembleDebug
```

产物：`app\build\outputs\apk\debug\app-universal-debug.apk`（约 21 MB）。
分发包：`app\build\dist\掌上成理-debug.apk`（`assembleDebug` 后自动复制）。
本地固定目录：`dist\latest\`，每次 `assembleDebug` / `assembleRelease` 后自动刷新，
包含 `掌上成理-debug.apk`、`掌上成理-release.apk` 及带版本号的副本；该目录已 gitignore。
APK 统一走 Gitee Release 资产，仓库不再保存 APK。

> 注意：Java 25 会被当前 Gradle / Kotlin 版本拒绝；本仓库的 `gradlew.bat` 直接调用 PATH 里的 `java`，
> 需用 JDK 21 的 `java.exe` 启动 wrapper，或把 PATH 中的 `java` 指向 JDK 21。

## 已知限制

- 图书馆、砚湖易办、智慧后勤、缴费平台、学生邮箱仍是网页容器（属于第三方 / 交易页面，未做原生化）。
- 「成绩统计」是本地汇总页；真实成绩明细在查询中心的「成绩查询」原生页。
- 校外依赖浏览器版 WebVPN（`vpn.cdut.edu.cn`），无需安装 VPN 客户端。
- debug 签名，仅个人分发；换账号 / 真机复验仍待补测。

## 结构

```
app/src/main/java/com/yh/schedule/
├─ CdutApp.kt              Application
├─ MainActivity.kt
├─ core/
│  ├─ Models.kt            课程/考试/日程模型 + 学期周次
│  ├─ SessionStore.kt      DataStore 登录态与偏好（密码 Keystore 加密）
│  ├─ NetworkAccess.kt     直连 / WebVPN 自动判定
│  ├─ WebConst.kt          链路常量与代理地址改写
│  ├─ QzClient.kt          强智 jsxsd 适配
│  └─ QzJsTimetableParser.kt  课表页 JS 结果解析
├─ data/
│  ├─ AppDb.kt             Room：自定义事项 / 考试记录
│  └─ Repository.kt        数据与偏好入口
└─ ui/
   ├─ theme/Theme.kt       Material3（成理蓝）
   ├─ AppNav.kt            登录门控 + 四 Tab + 网页/数据路由
   └─ screens/
      ├─ CasLoginScreen.kt     统一认证登录 + 冷启动自动刷新
      ├─ TimetableScreen.kt    课表 + 隐藏 WebView 抓取
      ├─ DataPageScreen.kt     查询中心教务页原生展示
      ├─ WebContainerScreen.kt 外部服务网页容器
      └─ ...                   聚焦 / 查询中心 / 选项 / 日程编辑
```

## 合规边界

- 功能语义借鉴自"聚在工大"（开源项目 Chiu-xaH/HFUT-Schedule）的逆向分析；**未复制其代码与资源**；实施前请确认其开源协议条款。
- 未复用原开发者的任何凭据（OAuth clientName / Gitee token / Supabase key）。
- 本应用仅存储与查询用户本人数据，不自研支付（一卡通/水电充值走官方小程序 Deep Link，后续版本）。
