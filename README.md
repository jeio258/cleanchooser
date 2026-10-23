# CleanChooser · 选择器净化

精简 Android 的**分享 / 打开方式 / 文本处理**候选菜单，让常用应用排在前面；同时通过 Root 管理磁贴、快捷方式、小部件的入口。

包名 `com.eio258.cleanchooser`，基于 [yagay/ListCleaner](https://github.com/yagay/ListCleaner)（commit `0d8657c`）重构而来。来源、改动与许可证状态见 [NOTICE.md](NOTICE.md)。

---

## 1. 它做什么

| 能力 | 说明 | 依赖 |
|---|---|---|
| 候选过滤 | 在系统返回分享/打开方式/文本处理候选时按规则剔除非目标项 | LSPosed（libxposed API 102） |
| 优先排序 | 按保存的应用顺序重排候选，支持按类别、开型、浏览器域名细分 | LSPosed |
| 文本菜单 | 隐藏或排序长按文字后的「处理文本」扩展项，并展示系统实际菜单 | LSPosed |
| 组件管理 | 真实禁用/启用磁贴、快捷方式、小部件组件 | Root（su） |
| 备份与诊断 | 导入导出 JSON 规则、导出诊断包 | 无 |

两类能力互相独立：没有 Root 也能用规则与排序；只读组件列表不需要 Root。

---

## 2. 架构

### 2.1 进程模型

```
┌────────────────────────┐        ┌─────────────────────────────┐
│  管理端（普通应用进程）  │        │  system_server（注入进程）    │
│  ui/ data/ domain/     │        │  xposed/                    │
│                        │        │                             │
│  RuleRepository ───────┼──配置──▶│  ListCleanerModule          │
│  (SharedPreferences)   │  分块   │   ├─ queryIntentActivities  │
│                        │  协议   │   ├─ mSortedList 排序        │
│  RuntimeStatus ◀───────┼──ack───│   ├─ AppsFilter 可见性       │
│  (命中数/菜单快照)      │  label  │   ├─ FloatingToolbar 观测    │
└────────────────────────┘        │   └─ 组件状态守护             │
                                   └─────────────────────────────┘
```

- **配置下发**：`RuntimeProtocol` 把序列化后的 `ModuleConfig` 切成不超过 20k 字符的分块，通过 `PackageManager` 查询的 Intent extra 传给 hook。
- **状态回传**：hook 把 `hookCompat:digest:hits…:菜单快照` 编码进 `ResolveInfo.nonLocalizedLabel`，管理端解析成 `RuntimeStatus`。
- 这条通道不依赖 RemotePreferences 的缓存刷新，因此 APK 更新后旧 hook 仍能正确拒绝新配置。

### 2.2 目录

```
app/src/main/java/com/eio258/cleanchooser/
├── ListCleanerApp.kt          应用入口：会话、同步、RuntimeStatus
├── ui/                        Compose Material3，单 Activity
│   ├── MainActivity.kt        Scaffold：顶栏 / 底栏 / 二级页路由
│   ├── Theme.kt               品牌色、字号、圆角
│   ├── DesignSystem.kt        统一行组件、卡片、分段控件
│   ├── HomeScreen.kt          首页：健康条 + 三张任务卡（就地展开）
│   ├── WorkbenchScreen.kt     工作台：规则 / 排序 / 组件 / 文本菜单 四段
│   ├── RulesList.kt           规则段
│   ├── PriorityList.kt        排序段（长按拖动、边缘滚动）
│   ├── ComponentsList.kt      组件段（Root 启停）
│   ├── TextMenuScreen.kt      文本菜单段（系统动作 + 扩展项）
│   ├── AppDetailScreen.kt     应用详情：已选择 / 未选择，或组件三态
│   ├── ListHeader.kt          频道 chips + 筛选面板 + 帮助
│   ├── MainControls.kt        顶栏、搜索框
│   ├── StatusScreen.kt        状态页：显示模式 / 运行时 / 同步 / 备份 / 诊断
│   └── ...                    弹窗、作用域、组件发现控制器
├── data/                      RuleRepository、IntentCatalog、RootComponentCatalog
├── domain/                    纯策略：Rule、Priority、OpenType、BrowserLink、
│                              ModuleConfig、RuntimeProtocol、TextMenuSnapshot
├── runtime/                   开机校正、JobService、ServiceSession
└── xposed/                    system_server 侧
    ├── ListCleanerModule.kt   主 hook（查询 / 排序 / 可见性 / ack）
    ├── MenuSnapshotProbe.kt   文本菜单观测
    ├── ComponentStateGuardModule.kt
    ├── ComponentDiscoveryFilterModule.kt
    └── DirectShareConsistencyModule.kt
```

### 2.3 数据模型

| 类型 | 作用 | 存储 |
|---|---|---|
| `ComponentRule` | `PROCESS_TEXT\|包名\|类名`，一条候选规则 | 规则集合 |
| `PriorityConfig` | 每个 `IntentKind` 一份包名顺序 + 组件显示名 | prefs JSON |
| `OpenTypeConfig` | 打开方式的子类型规则与优先级 | prefs JSON |
| `BrowserLinkConfig` | 域名规则、域名优先级 | prefs JSON |
| `TextMenuSnapshot` | 系统菜单实测顺序（只读，不回写） | 运行时通道 |
| `ModuleConfig` | 以上内容的原子快照 + 文本菜单组件顺序 | 下发通道 |

### 2.4 Hook 点

| 目标 | 阶段 | 作用 |
|---|---|---|
| `IPackageManagerBase.queryIntentActivities` | 查询 | 过滤候选、注入 policy metadata、回 ack |
| `IntentResolver` 的 `mSortedList` | 排序 | 应用优先顺序（含固定槽位保护） |
| `AppsFilter` | 可见性 | 按调用方隐藏目标包 |
| `FloatingToolbar` 菜单构建 | 观测 | 抓真实文本菜单条目与顺序（只读） |
| 组件发现 / 状态 | 守护 | 防止系统恢复已禁用组件、过滤发现结果 |

### 2.5 UI 结构

```
底栏 3 项
├─ 首页     健康条 + 菜单候选/显示顺序/组件三张卡 + 备份、诊断入口
├─ 工作台   搜索栏 + 四段
│            ├─ 规则    频道 chips + 筛选面板 + 应用行 → 详情
│            ├─ 排序    同上 + 长按拖动 + 排名
│            ├─ 组件    种类 chips + 启用状态 + 应用行 → 详情
│            └─ 文本菜单  系统动作（只读）+ 扩展项（隐藏/排序）
└─ 状态     显示模式 / 运行时能力 / 同步与作用域 / 备份 / 诊断
```

详情页为二级路由，进入时隐藏底栏；返回键逐层回退。

---

## 3. 构建

要求 JDK 17、Android SDK 37、Gradle 9.4.1、AGP 9.2。

```bash
  # 交付用：先 clean 再打包，避免旧 APK 的尾部字节残留
gradle :app:assembleClean

  # 常规调试
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

`local.properties` 里写 `sdk.dir=<你的 SDK 路径>`，不要提交。

打包后 `verifyApkSize` 会比对「文件大小」与「zip 条目总量」，差值超过 1 MB 就告警，提示改用 `assembleClean`。

---

## 4. 使用

1. 安装 APK。
2. LSPosed 启用模块，作用域为静态声明的三项：`system`、`android`、`com.android.intentresolver`。
3. 重启手机让 hook 加载。
4. 首页确认「已连接」，再到工作台配置规则。
5. 需要管理组件时，在 Root 管理器授权；组件列表只读不需要 Root。

已打开的菜单需要关闭重开才会应用新规则。

---

## 5. 与上游的差异

- 包名 `com.yagay.ListCleaner` → `com.eio258.cleanchooser`，应用名改为「选择器净化」。
- UI 全量重构：三页导航、统一行组件、品牌配色与字号、底部面板替代原生下拉与对话框。
- 新增文本菜单工作区与菜单观测 hook。
- 删除内联展开、`ModuleStatusRow`、`ComponentRow`、`RuntimePanel` 等旧实现，移除 170 条未使用字符串。
- debug 构建启用 R8，体积从约 56 MB 降到 9.5 MB。

详细清单见 [NOTICE.md](NOTICE.md)。

---

## 6. 许可证与来源

本仓库不是从零开始的原创项目，必须按下面两条分别对待，缺一不可。

### 6.1 上游：yagay/ListCleaner

| 项 | 内容 |
|---|---|
| 仓库 | https://github.com/yagay/ListCleaner |
| 使用的提交 | `0d8657c` |
| 许可证 | **仓库内没有 LICENSE 文件** |

没有许可证 = 默认保留所有权利。本仓库是它的修改版本，**不附带任何许可证，也未获得上游授权**。公开发布二进制或源码前必须先取得上游作者许可；未获授权时请仅在本机使用。

### 6.2 参考项目：txtoi（文本选择菜单控制）

| 项 | 内容 |
|---|---|
| 仓库 | https://github.com/TheKingBucket001/txtoi |
| 许可证 | **GPL-3.0-only** |
| 使用方式 | 仅参考交互与数据设计，**未复制任何源码** |

由于没有复制 txtoi 的代码，本仓库不构成 txtoi 的衍生作品，不需要整体改为 GPL。一旦有人把 txtoi 的源码合并进来，整个项目就必须以 GPL-3.0 发布并公开完整源码——目前**没有**这样做。

GPL-3.0 全文：https://www.gnu.org/licenses/gpl-3.0.txt

### 6.3 功能来源标注

| 功能 | 来源 | 实现方式 |
|---|---|---|
| 分享 / 打开方式候选过滤 | 上游 ListCleaner | 原有代码，迁移包名 |
| 优先排序、开型、浏览器域名 | 上游 ListCleaner | 原有代码 |
| 磁贴 / 快捷方式 / 小部件管理 | 上游 ListCleaner | 原有代码 |
| 三页导航、工作台四段、首页卡片 | 本项目 | 重写 |
| 统一行组件、品牌配色、底部面板 | 本项目 | 重写 |
| 文本菜单隐藏与排序 | txtoi（设计参考） | 本项目独立实现 |
| 文本菜单「恢复排序 / 恢复显示」 | txtoi（设计参考） | 本项目独立实现 |
| 系统菜单观测 | txtoi（思路参考） | 本项目基于 `FloatingToolbar` 独立实现 |

第三方依赖及其许可证见 [NOTICE.md](NOTICE.md) 第 4 节。
