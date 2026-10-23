# 来源、改动与许可证说明

本文件记录本项目的代码来源、参考项目、第三方依赖，以及当前的许可证状态。分发或再发布前请先读完第 1 节。

---

## 1. 上游项目与许可证状态（重要）

| 项 | 内容 |
|---|---|
| 上游仓库 | https://github.com/yagay/ListCleaner |
| 本仓库起点 | 上游 `main` 分支 commit `0d8657c` |
| 上游许可证 | **仓库中没有任何 LICENSE 文件** |

上游没有声明开源许可证。按著作权默认规则，没有许可证的公开仓库**默认保留所有权利**，"公开可读"不等于"允许修改和再分发"。

因此：

- 本仓库是对上游代码的修改版本，**不附带任何许可证**，也不代表已获得上游授权；
- 在获得上游作者书面许可之前，公开发布或分发本项目的二进制/源码存在版权风险；
- 如果你不是版权持有人，建议先向上游作者申请授权，或只在本机使用、不公开分发；
- 本项目**不会**擅自替上游选择许可证。若上游后续补充许可证，本项目应随之调整。

如果你就是上游作者，请在上游仓库补一个 LICENSE，并在本文件中注明，届时本节可以改为对应的许可证声明。

---

## 2. 参考项目

### txtoi（文本选择菜单控制）

| 项 | 内容 |
|---|---|
| 仓库 | https://github.com/TheKingBucket001/txtoi |
| 许可证 | GPL-3.0-only |
| 本项目使用方式 | **只参考功能设计与交互模型，未复制任何源码** |

具体参考的内容：

- 文本菜单"固定项 / 扩展项"的区分思路；
- 隐藏项需先恢复才能拖动、固定项位置保留的交互约束；
- "恢复默认排序"与"恢复全部显示"两个独立操作的产品设计；
- 菜单观测（读取系统真实菜单）这一能力方向。

本项目没有引入 txtoi 的任何 `.java` / `.kt` 文件，也没有复制其 `RuleConfig`、`SystemMenuClassifier`、`ProbeProtocol`、`PendingJournal` 等实现。相关功能在 `domain/TextMenuSnapshot.kt`、`xposed/MenuSnapshotProbe.kt`、`ui/TextMenuScreen.kt` 中按本项目的数据结构重写。

如果后续需要直接复用 txtoi 的源码，本项目必须整体改为 GPL-3.0 并公开完整源码——目前**没有**这样做。


### 2.1 功能归属

| 功能 | 来源 | 实现方式 |
|---|---|---|
| 分享 / 打开方式过滤、优先排序 | 上游 ListCleaner | 迁移并重写 UI |
| 磁贴 / 快捷方式 / 小部件管理 | 上游 ListCleaner | 原样保留业务逻辑 |
| 三页导航、四段工作台、统一行组件 | 本项目 | 原创 |
| 文本菜单隐藏与排序 | txtoi 设计参考 | 独立实现（`TextMenuScreen` + `textMenuOrder`） |
| 恢复默认排序 / 恢复全部显示 | txtoi 设计参考 | 独立实现（两个 VM 方法） |
| 系统菜单观测 | txtoi 思路参考 | 独立实现（`MenuSnapshotProbe`） |

### 2.2 GPL 边界

- 未复制 txtoi 的任何源码文件，因此本仓库不是 txtoi 的衍生作品，不受 GPL-3.0 传染。
- 若后续直接合并 txtoi 源码（`.java` / `.kt`），本仓库必须整体改为 **GPL-3.0-only**，附完整源码与修改说明，并保留原作者版权声明。
- 若只是继续参考设计，则保持当前状态：仅在文档中致谢并标注来源。

---

## 3. 相对上游的修改清单

### 3.1 标识

- 包名：`com.yagay.ListCleaner` → `com.eio258.cleanchooser`
- 应用名：列表清理 / List Cleaner → 选择器净化 / CleanChooser
- Xposed 入口 `java_init.list` 同步为新包名下的四个模块类
- `versionCode 44`、`hookCompat 44`、`versionName 1.6.19`

### 3.2 新增

| 文件 | 作用 |
|---|---|
| `ui/Theme.kt` | 固定品牌配色与收紧的字号体系，替换动态取色 |
| `ui/DesignSystem.kt` | 统一行组件、卡片、分段控件、形状常量 |
| `ui/HomeScreen.kt` | 首页：健康条、三张任务卡（就地展开）、入口区 |
| `ui/WorkbenchScreen.kt` | 工作台四段外壳 |
| `ui/RulesList.kt` / `PriorityList.kt` / `ComponentsList.kt` | 由旧页面拆分重写 |
| `ui/TextMenuScreen.kt` | 文本菜单工作区 |
| `ui/AppDetailScreen.kt` | 应用详情二级页 |
| `ui/ListHeader.kt` | 频道 chips、筛选面板、帮助面板 |
| `ui/StatusScreen.kt` | 状态页四块 |
| `domain/TextMenuSnapshot.kt` | 菜单快照模型与编解码 |
| `xposed/MenuSnapshotProbe.kt` | 文本菜单观测 hook |

### 3.3 删除

- `ui/MainTabs.kt`、`ui/PriorityDialog.kt`、`ui/RootComponentsScreen.kt`：旧页面被拆分重写后删除。
- `ui/RuntimePanel.kt`：状态页重写后无引用。
- `MainState.expandedAppKey` / `toggleExpandedApp`：内联展开方案被详情页取代。
- `ModuleStatusRow`、`ComponentRow`、`ComponentSelectionMark`、`SectionHeader`、`ActionTile`：旧组件已无引用。
- 未使用字符串资源 170 条（中英各一份）。
- `requestScope()` 运行时申请作用域整条链路：改为 `staticScope=true`，作用域只由 `scope.list` 静态声明。

### 3.4 行为变化

- 导航从 4 个底部页签改为 3 个（首页 / 工作台 / 状态），工作台内部四段切换。
- 应用行不再内联展开，改为进入详情页；详情页分段去掉「全部」，只保留「已选择 / 未选择」。
- 规则频道去掉「全部」以外的聚合入口不再存在（频道仍保留「全部」项）。
- 组件统计、筛选面板、行样式统一到同一套 DesignSystem。
- debug 构建启用 R8 与资源收缩，APK 从约 56 MB 降至 9.5 MB。

---

## 4. 第三方依赖

| 依赖 | 版本 | 许可证 |
|---|---|---|
| AndroidX Activity Compose | 1.10.1 | Apache-2.0 |
| AndroidX Lifecycle (viewmodel / runtime compose) | 2.9.0 | Apache-2.0 |
| AndroidX Core KTX | 1.16.0 | Apache-2.0 |
| Jetpack Compose Material3 | 1.3.2 | Apache-2.0 |
| Material Icons Extended | 1.7.8 | Apache-2.0 |
| Kotlinx Coroutines Android | 1.10.2 | Apache-2.0 |
| Kotlinx Serialization JSON | 1.8.1 | Apache-2.0 |
| io.github.libxposed:api / service | 102.0.0 | 见该库仓库声明 |
| JUnit | 4.13.2 | EPL-1.0（仅测试） |

以上依赖均以二进制依赖方式引入，未修改其源码。

---

## 5. 联系与署名

- 上游作者：yagay（https://github.com/yagay）
- 本项目维护者：jeio258

如果你认为本仓库的某部分侵犯了你的权利，请通过仓库 Issue 联系，我们会立即处理。
