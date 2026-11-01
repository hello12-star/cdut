# 掌上成理（YHSchedule）

成都理工大学校园日程助手 —— MVP。功能语义参照"聚在工大"（HFUT-Schedule）的功能级逆向分析成果重新设计，接口契约全部换为成都理工大学生态。

> 逆向分析交付物见 `D:\workbuddy_workplace\jzgdx_re\deliverables\`
> （recon_report / feature_specs / gap_analysis / implementation_plan）。

## 功能（MVP）

| 模块 | 状态 | 数据源 |
|---|---|---|
| 登录 | ✅ 统一认证 WebView 登录（密码不经过本应用）；演示模式可用 | CAS `cas.paas.cdut.edu.cn`（瑞数防护下唯一合法路径） |
| 课程表（周视图/考试条/周切换） | ✅ 演示数据；真实接口路径待校准 | 强智 jsxsd 适配器 |
| 聚焦页（重要安排/明日课程/自定义事项） | ✅ 本地 Room 持久化 | 本地 |
| 成绩（统计卡 + 学期分组） | ✅ 演示数据 | 强智 jsxsd 适配器 |
| 服务容器（砚湖易办/教务/后勤/缴费/图书馆/邮箱） | ✅ WebView 容器 | 官方页面 |

**演示模式**：登录页点"演示模式进入"，内置宜宾校区楼栋命名（甲/乙/丙楼）的示例数据，不影响任何真实账号。
**真实模式**：点"统一身份认证登录"进入 WebView 完成登录，成功后提取 CAS 会话 Cookie 复用。

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
> 已验证：**课表页渲染出真实课程数据**（第 9 周：大学体育(1)、劳动教育(理论) (宜)甲A104），成绩页正常（新生暂无数据）。
> 协议细节留档于 `recon/`（cas_probe.py / cas_login_rsa.py / RsaEncrypt.java / cdp_*.js），均不含凭据。

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
| 2 | 登录从 `https://jw.cdut.edu.cn/jsxsd/` 进入，**不要手拼 CAS URL** | 手拼会漏掉 `targetUrl=base64...`，SSO 断在 sso/login.jsp，jw 会话建不起来 |
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

选项页「网络接入 → 自动刷新登录」：冷启动后用已保存账密在后台静默登录一次 CAS
（对标 HFUT 的「自动刷新登录状态」，默认关闭，耗时较长，按需开启）。

## 查询中心原生展示（0.1.8）

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

## 功能清单（MVP 已完成）

- [x] 统一认证登录（WebView，密码不进 App）
- [x] 退出登录 / 切换账号（清登录态与全部 Cookie，无需清应用数据）
- [x] 课表：真实数据抓取 + **时间轴原生渲染** + 本地缓存 + 加载/空/错误三态
- [x] 聚焦页：日期周次、明日课程、自定义事项（Room）、3 个快捷入口
- [x] 成绩：统计卡（加权均分/学分/绩点）+ 学期分组
- [x] 服务：9 个真实入口（课表/成绩/考试/培养方案/选课结果/公告/砚湖易办/智慧后勤/缴费/图书馆/邮箱）
- [x] 添加日程（标题/开始/结束/描述）+ 保存后立即出现在课表

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

命令行构建（已验证可用）：

```bat
set JAVA_HOME=D:\workbuddy_workplace\cdut-helper\jdk21\jdk-21.0.2
cd /d D:\workbuddy_workplace\cdut-helper
gradle-dist\gradle-8.10.2\bin\gradle.bat assembleDebug --no-daemon
```

产物：`app\build\outputs\apk\debug\app-debug.apk`（约 17.5 MB，2026-10-18 已构建成功）。
分发包：`app\build\dist\掌上成理-debug.apk`（`assembleDebug` 后自动复制，文件名用于直接发给同学）。

- JDK：17 ~ 21（本仓库已附带 JDK 21 于 `jdk21\`；Java 25 会被 Gradle 8.10 拒绝）
- Android SDK：`local.properties` 已指向 `C:\Users\l'l\AppData\Local\Android\Sdk`；首次构建时 AGP 自动补装了 platform 35

## 待联调校准清单（拿到账号/接口确认后）

| 项 | 位置 | 说明 |
|---|---|---|
| CAS 表单字段 | `core/CasClient.kt` | Apereo CAS 6 可能无 `lt` 字段；错误判定文案需按实际页面校准 |
| CAS 各子系统 service 参数 | `core/CasClient.kt` | 需向信息中心确认注册名，避免风控 |
| 强智登录路径 | `core/QzClient.kt#QzPaths` | 经典 `/jsxsd/xk/LoginToXk` vs 新版 `/sso/login.jsp`，以实测为准 |
| 课表/成绩解析列序 | `core/QzClient.kt#parse*` | 强智 HTML 结构随版本有差异 |
| 学期代码 | `data/Repository.kt` | 现硬编码 `2026-2027-1`，需从教务学期接口动态获取 |
| 一卡通/电费/校园网 | 聚焦页占位 | 属 Requires New Integration（见 gap_analysis.md） |

## 结构

```
app/src/main/java/com/yh/schedule/
├─ CdutApp.kt              Application（全局 OkHttp）
├─ MainActivity.kt
├─ core/
│  ├─ Models.kt            课程/考试/成绩模型 + 学期周次计算
│  ├─ SessionStore.kt      DataStore 登录态（正式版需接 Keystore 加密）
│  ├─ CasClient.kt         成理 CAS 客户端（真实模式骨架）
│  └─ QzClient.kt          强智 jsxsd 适配器（课表/成绩 HTML 解析）
├─ data/
│  ├─ AppDb.kt             Room：自定义事项 / 考试记录
│  ├─ MockData.kt          演示数据（宜宾校区命名）
│  └─ Repository.kt        Mock / 真实双模式路由
└─ ui/
   ├─ theme/Theme.kt       Material3（成理蓝）
   ├─ AppNav.kt            登录门控 + 底部四 Tab + WebView 路由
   └─ screens/             Login / Timetable / Focus / Grade / Services / WebContainer
```

## 合规边界

- 功能语义借鉴自"聚在工大"（开源项目 Chiu-xaH/HFUT-Schedule）的逆向分析；**未复制其代码与资源**；实施前请确认其开源协议条款。
- 未复用原开发者的任何凭据（OAuth clientName / Gitee token / Supabase key）。
- 本应用仅存储与查询用户本人数据，不自研支付（一卡通/水电充值走官方小程序 Deep Link，后续版本）。
