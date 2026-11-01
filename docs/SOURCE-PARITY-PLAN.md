# 源码对照清单与改造计划（v0.1.5 → 0.1.7）

> 参考源码：`github-sources/HFUT-Schedule`（主参考）、`github-sources/SmartHNU`（备选）、`github-sources/libs`（22 个库）
> 状态：**A+B、C+D（C3 除外）、WebVPN 链路与 D4 已完成并验证（2026-11-01）**
> 执行记录见文末「六、执行记录与决策」与「七、C+D 执行记录」。

## 一、对照范围与方法

- 对照对象：底部四个页签（课程表 / 聚焦 / 查询中心 / 选项）、登录链路、WebView 容器、数据层、关于页。
- 参考文件：
  - 课表：`app/src/main/java/com/hfut/schedule/ui/screen/home/MainUI.kt`、`ui/style/CalendarStyle.kt`
  - 聚焦：`ui/screen/home/focus/…`
  - 查询中心：`ui/screen/home/search/SearchUI.kt`
  - 选项：`ui/screen/home/cube/SettingsItemsUI.kt`、`cube/screen/AppearanceSettingsScreen.kt`、`AppSettingsScreen.kt`、`NetworkSettingsScreen.kt`
  - 关于：`ui/screen/fix/about/AboutDeveloper.kt`
- 判定口径：
  - **已对齐**：行为与源码一致或按本项目需求做了合理适配
  - **可落地**：不依赖服务端、不引入重型库，本地即可真实生效
  - **不适用**：依赖服务端/重型依赖/本校没有的业务
  - **待验证**：需要真机或教务账号确认后才能做

## 二、逐模块差异表

### 1. 课程表

| 项 | 现状 | 源码 | 结论 |
|---|---|---|---|
| 5/7 天开关 | 已做，持久偏好 | `showAll` 用 `rememberSaveable`（重启不保留） | 已对齐（持久化是需求增强） |
| 课程块内容 | 课程名 + 地点 | 可选显示教师（`app_settings_display_teachers_title`） | **可落地：加“显示教师”开关** |
| 同时段多课 | 直接叠放，可能相互遮挡 | `enableMergeSquare` 合并冲突方格 | **可落地：加“合并同时段课程”开关** |
| 课表外观 | 固定配色/字号/行距 | 背景图、方块透明度、字号、行距均可调 | 部分可落地（透明度/字号/行距）；背景图工作量大 |
| 点击课程详情 | 已做 | 已做 | 已对齐 |
| 空白格双击添加日程 | 已做 | 已做 | 已对齐 |
| 周次选择/切换 | 已做 | 已做 | 已对齐 |

### 2. 聚焦页

| 项 | 现状 | 源码 | 结论 |
|---|---|---|---|
| 今日课程 / 明日概况 / 重要安排 / 其他事项 | 已做 | 已做 | 已对齐 |
| 已过期事项折叠 | 已做且可持久 | 源码应用设置中有“聚焦显示已过期课程” | 已对齐 |
| 添加日程 FAB | 已做 | 已做 | 已对齐 |
| 教务检查卡 / 自动抓取 | 已做（6 小时节流） | 源码有自动更新/检查 | 已对齐 |

### 3. 查询中心

| 项 | 现状 | 源码 | 结论 |
|---|---|---|---|
| 搜索框 + 双列卡片 | 已做 | 已做 | 已对齐 |
| 入口清单 | 13 项（成理已验证可用） | 约 50 项（多数 HFUT 专属） | 已筛选，未照搬 |
| 入口顺序自定义 | 未做 | `reorderByIds` + 排序持久化 | **可落地但工作量中（拖拽排序）** |
| 新增教务入口 | 未做 | 源码含评教/空教室/学籍等 | **待验证：URL 能打开再加** |

### 4. 选项页

| 分组/项 | 现状 | 源码 | 结论 |
|---|---|---|---|
| 账号与登录 | 已做 | 设置页顶部个人信息 | 已对齐 |
| 外观：深色模式 | 已做 | 主题 / 纯黑 / 网页深色 / 动效 / 模糊 | 可补“网页强制深色”；其余不适用 |
| 显示：周末 / 已过期 | 已做 | 应用设置含教师、合并方格、已过期课程 | 见课程表两条 |
| 网络接入 | 只读展示 | 密码管理 / 自动刷新登录 / 更新数据 / 刷新登录 | 可补“清除课表缓存”“学期起始日”；自动刷新登录风险高 |
| 维护与关于 | 已做 | 关于页 | 见下 |

源码应用设置中**可落地**的项：
- 清除缓存（`app_settings_clear_cache_title`）
- 当前学期 / 开学日期（`app_settings_start_date_of_term_title`）
- 预测性返回（`app_settings_predictive_back_gesture_title`，API 33+）
- 无限滚轮（`enableInfiniteWheelPicker`，需改现有 WheelPicker）
- 备份与恢复（`app_settings_backup_and_restore_title`，可做 JSON 导入导出）

**不适用**：语言切换、小组件、默认日历账号、验证码自动填充（OCR）、在线更新、星标/访问量统计。

### 5. 关于页

| 项 | 现状 | 源码 | 结论 |
|---|---|---|---|
| 开发人员 + 反馈邮箱 | 已做 | 已做（含邮件按钮） | 已对齐 |
| 开源引用（双列） | 已做（只列本项目真实依赖） | 已做 | 已对齐 |
| 用户协议 | 已做（简短版） | 已做（完整协议） | 已对齐（按本项目实际情况写的） |
| 关于本应用（应用名/包名/版本） | 只有版本行 | 有“关于本应用”卡片 | **可落地：补一张静态信息卡** |
| 更新日志 / 版本信息页 | 无 | 有（需要发行数据） | 可做离线静态更新日志，可选 |

### 6. 登录 / WebView

| 项 | 现状 | 源码 | 结论 |
|---|---|---|---|
| CAS WebView 登录 + 自动填充 | 已做 | 已做 | 已对齐 |
| 刷新登录状态 | 已做 | 已做 | 已对齐 |
| WebView 强制深色 | 未做 | `saveWebViewDark` 开关 | **可落地**（API 33+ FORCE_DARK） |

## 三、拟执行清单（按批次）

### 批次 A：设置类偏好（低风险，纯本地）

| 编号 | 改动 | 涉及文件 | 风险 |
|---|---|---|---|
| A1 | 课表块显示教师（开关） | `TimetableAxis.kt`、`TimetableScreen.kt`、`SessionStore.kt`、`Repository.kt`、`OptionsScreen.kt` | 低 |
| A2 | 合并同时段课程（开关） | `TimetableAxis.kt`、同上偏好链 | 中（排版逻辑） |
| A3 | WebView 强制深色（开关） | `WebContainerScreen.kt`、偏好链 | 低 |
| A4 | 清除课表缓存（按钮 + 确认） | `Repository.kt`、`OptionsScreen.kt` | 低 |
| A5 | 学期起始日展示 + 恢复默认 | `Repository.kt`、`OptionsScreen.kt` | 低 |
| A6 | 关于本应用信息卡 | `AboutScreen.kt` | 低 |

### 批次 B：查询中心增强

| 编号 | 改动 | 说明 | 风险 |
|---|---|---|---|
| B1 | 入口顺序自定义（长按拖拽 + 持久化） | 需自实现或引入拖拽库 | 中 |
| B2 | 新增教务入口（学籍/评教/空教室/补考） | **先真机验证 URL**，打不开就不加 | 待验证 |

### 批次 C：课表外观（可选）

| 编号 | 改动 | 说明 |
|---|---|---|
| C1 | 方块透明度滑块 | 源码有对应设置 |
| C2 | 字号/行距滑块 | 源码有对应设置 |
| C3 | 课表背景图 | 需要图片选择与存储，工作量大 |

### 批次 D：应用设置（按需）

| 编号 | 改动 | 说明 |
|---|---|---|
| D1 | 无限滚轮开关 | 改 `WheelPicker` |
| D2 | 预测性返回开关 | API 33+，manifest 配置 |
| D3 | 备份/恢复（JSON） | 日程 + 偏好导出导入 |
| D4 | 自动刷新登录 | 需后台静默刷新，风险较高，建议暂缓 |

## 四、明确不照搬

- 在线更新、星标数、访问量、用户数（无服务端）
- Haze 模糊、液态玻璃、镜面、复杂转场（重型依赖，影响包体与稳定性）
- 主题取色（MaterialKolor）、小组件、默认日历账号、OCR 验证码、语言切换
- HFUT 专属业务入口（慧新易校、洗衣、洗浴、乐跑、校车、快递等）

## 五、待确认问题

1. 批次怎么选：A 全做？B1 要不要做（拖拽排序工作量中等）？C 做哪几项？D 做哪几项？
2. A2 合并规则：同时段多门课，是合并成一格（显示“课1 / 课2”），还是并排缩小显示？
3. A1 默认值：显示教师默认开还是关？
4. B2 新增教务入口：真机上打不开就放弃，可以吗？
5. B1 是否允许新增一个拖拽排序依赖，还是必须自实现？
6. 这批做完版本号是否升到 `0.1.6`？

## 六、执行记录与决策（2026-11-01）

### 已确认决策
- 本轮先做 A+B，C/D 后续再做并需记得提醒。
- A1 取消：教师信息只在课程详情页展示，课表块不再加「显示教师」开关。
- A2 取消：保持现有并排分列，不合并成一格。
- B1 允许新增 `sh.calvin.reorderable:reorderable:3.1.0`。
- B2 先验证 URL；打不开也保留入口，空白页显示「暂未开放」。
- 所有 WebView 统一拦截未登录 JSON，弹出「登录已过期」。
- 应用名改为「掌上成理」，分发包文件名 `掌上成理-debug.apk`。
- 版本升到 `versionName = "0.1.6"`、`versionCode = 6`。

### 已完成并验证
- A3 网页强制深色：选项页新增开关（默认关），仅在深色主题下生效；系统 API 不生效时按 HFUT 方案注入深色 CSS。
- 下拉刷新顶部白条修复：`MainActivity` 开启 edge-to-edge；`AppRoot` 根容器铺主题背景并 `statusBarsPadding()`，`Scaffold` 取消重复 inset；`YhTheme` 同步 Window/DecorView 背景、系统栏颜色与图标明暗；`themes.xml` 关闭浅色状态栏标志；Focus 内关闭 `LocalOverscrollConfiguration` 光晕。深色、浅色下拉过程均实测顶部与内容同色，无白条。
- A4 清除课表缓存：确认弹窗 + 清除 + 重置抓取节流；清空后从聚焦页可重新抓取。
- A5 学期起始日：展示当前值，点按恢复默认 `2026-08-31`。
- A6 关于本应用：应用名 / 包名 / 版本信息卡；开源引用补充 Reorderable。
- B1 入口顺序自定义：长按卡片拖拽，`query_order` 持久化，重启后保持。
- B2 新增入口：学籍信息、学生评教、空教室、补考报名；四个 URL 均可打开，学籍正常，其余显示教务自身的「系统功能暂未开放」。
- 全 WebView 会话过期拦截：实测清除 Cookie 后打开学籍信息，弹出「登录已过期」，重新登录可恢复。
- 构建产物：`app/build/outputs/apk/debug/app-debug.apk` + `app/build/dist/掌上成理-debug.apk`。

### 尚未执行
- 批次 C3（课表背景图）：用户明确决定不做。
- 换账号清空日程、退出登录等依赖第二账号的实测。

## 七、C+D 执行记录（2026-11-01）

### 已完成并验证
- C1 课程块透明度：选项页「课表外观 · 课程块透明度」滑块，0.3–1.0；写入 `course_block_alpha`，课表块背景按 alpha 渲染。
- C2 课程文字大小与行距：`course_text_scale`（0.75–1.35）、`course_line_scale`（0.85–1.6）；TimetableAxis 内时间/课程名/地点字号与行距同步缩放。
- D1 无限滚轮：`infinite_wheel` 偏好（默认开）；AddEvent 的时间滚轮按偏好传入 `enableInfiniteScroll`，关闭后滚到边界会停住并保留上下留白。
- D2 预测性返回：`AndroidManifest.xml` 增加 `android:enableOnBackInvokedCallback="true"`；选项页显示「已启用系统预测性返回（Android 13+ 返回手势）」状态行。Android 不支持运行时关闭该清单能力，因此未做伪开关。
- D3 备份/恢复：JSON 备份包含自定义日程与偏好（不含账号、密码、Cookie、课表缓存）；导出用 `CreateDocument`，导入用 `OpenDocument` + 二次确认；导入先校验全部事件，再在 Room 事务内替换，最后应用偏好。
- 版本升到 `versionName = "0.1.7"`、`versionCode = 7`；`aapt` 已确认。

### 明确不做 / 暂缓
- C3 背景图：不做（用户决定）。
- D4 自动刷新登录：当时暂缓，VPN 打通后已补做（见第八节）。

### 验证
- 滑块从 100% 调到 69% / 115% / 134% 后，课表块透明、文字变大、行距变宽，截图核对；随后恢复默认。
- 导出备份成功写入 `Downloads/掌上成理-backup-2026-11-01.json`（1801 字节），内容含 8 条日程与全部偏好。
- 从备份恢复成功，toast「备份已恢复」。
- 无限滚轮开关可切换；预测性返回清单标志已用 `aapt dump xmltree` 确认。
- 测试后已恢复浅色主题、默认开关与课表外观默认值。

### 验证截图
- `.screenshots/v016_*.png`（首页、查询中心、选项、关于、深色网页、会话过期弹窗、清除缓存等）。
- `.screenshots/v017_*.png`（课表外观滑块、无限滚轮、导出/恢复备份、0.1.7 选项页等）。

## 八、WebVPN 链路与 D4 执行记录（2026-11-01）

### 背景
`WebConst.USE_VPN` 切到 `true`，整条链路改走校园 WebVPN（校外与校内都可用）。
此前卡在「门户里没找到教务入口」，点击后 URL 原地不动并无限重载门户。

### 定位与修复
1. **VPN CAS 页识别**：`cas-paas-cdut-edu-cn-s.vpn.cdut.edu.cn:8118` 不在 `onPageFinished`
   的匹配里，登录按钮一直灰着、8 秒后才放开。已在 `CasLoginScreen` 补该分支。
2. **门户入口不跳转**：点击后日志仍停在 `bsdt.../homePage.htm`。带出真实 onClick：
   `openExpress('https://jw.cdut.edu.cn/sso/login.jsp','3','教务管理系统','4cf...')`。
   门户用 `window.open` 打开新窗口，抓取 WebView 没有 `onCreateWindow`，点击被吞。
   现由抓取器注入 `window.open` 钩子记录目标地址，并把 `target=_blank` 改回当前窗口。
3. **直连 CAS 没有 TGC**：门户给的是直连 jw 地址，打开后落到 `cas.paas.cdut.edu.cn` 登录页。
   新增 `WebConst.proxify()`：把 jw / cas / bsdt 改写成 `*-cdut-edu-cn-s.vpn.cdut.edu.cn:8118`，
   由代理转发后在代理 CAS 上凭 VPN 登录的 TGC 免密进入教务。
4. **SSO 链不回调 onPageFinished**：抓取器加 5 秒轮询兜底，避免永远卡在「正在检查」。
5. **门户循环重载**：点击不导航时原逻辑无限重载门户；加 3 次上限并输出诊断。
6. **查询容器误报空白**：强智成绩页是 frameset 套壳，外层 body 无文本；
   空白检测改为把 iframe/frame 文本一起算。容器 URL 也统一走 `proxify()`。
7. **Cookie 判定**：登录成功时补充读取 `cas-paas-...` 与 `jw-cdut-...` 代理域 Cookie；
   成功判定从 `vpn.cdut.edu.cn/por` 收紧为 `/portal`。
8. **直连/VPN 自动切换**：`NetworkAccess` 在启动和网络变化时判定：
   WiFi 先探测 `jw.cdut.edu.cn` 是否可达（任意 HTTP 响应都算），可达=校园网直连，
   不可达=校外 WiFi 走 VPN；蜂窝流量一律走 VPN。判定完成前显示短暂加载圈。
   接入方式变化后直连与 VPN 会话不通用，用已保存账密静默重登一次
   （`login_access_vpn` 记录上次登录方式；选项页显示当前模式）。

### D4 自动刷新登录
- 新增偏好 `auto_refresh_login`（默认关），选项页「网络接入」增加开关与说明。
- `AutoLoginRefresher`：冷启动时用 1dp 隐藏 WebView 打开 VPN CAS，
  注入已保存账密并点登录，命中 VPN 门户/代理教务域即成功，最多提示一次 toast。
- 只认冷启动时的开关值；会话中途打开开关不会立刻触发；随备份导出/导入。

### 验证
- VPN 登录：CAS → `vpn.cdut.edu.cn/portal` → 应用回到主界面。
- 聚焦页抓取：日志 `open proxified: http://jw-cdut...:8118/sso/login.jsp` →
  `xskb/xskb_list.do`（标题「学期理论课表」）→ 检查卡自动消失，轮询停止说明 `grabbed=true`。
- 查询中心「成绩查询」：VPN 容器正常渲染筛选栏与成绩表头，不再弹「该入口暂未开放」。
- 自动刷新登录：冷启动日志 `onPageFinished: vpn.cdut.edu.cn/portal/#!/service` → `done=true`。
- 接入方式切换（模拟器实测）：
  - 默认蜂窝 → 选项页显示「校外 WebVPN」；
  - 连接 WiFi（宿主可直连 jw）→ 自动切到「校园网直连」，日志显示直连 CAS 登录成功
    （`cas.paas.cdut.edu.cn` → `jsxsd/framework/xsMainV.htmlx`），聚焦页抓取无错误；
  - 关闭 WiFi → 自动切回 VPN，日志 `vpn.cdut.edu.cn/portal/#!/service` → `done=true`，
    再次下拉抓取到「学期理论课表」。
- 截图：`.screenshots/v018-v024_*.png`。
- 版本仍为 `0.1.7 / 7`（0.1.7 尚未对外分发，本轮 VPN 修复并入同一版本）。
