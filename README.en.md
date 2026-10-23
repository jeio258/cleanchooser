# CleanChooser

[简体中文](README.md) | **English**

Trim the Android **share / open with / process text** candidate menus and keep frequently used apps on top; with Root, also manage tiles, shortcuts and widgets.

Package `com.eio258.cleanchooser`, rebuilt from [yagay/ListCleaner](https://github.com/yagay/ListCleaner) at commit `0d8657c`. Provenance, changes and licensing status: [NOTICE.md](NOTICE.md).

---

## 1. What it does

| Capability | Description | Requirement |
|---|---|---|
| Candidate filtering | Drops unwanted entries from share / open / process-text results | LSPosed (libxposed API 102) |
| Priority ordering | Reorders candidates by saved app order, per kind / open type / browser host | LSPosed |
| Text menu | Hides or reorders the "process text" extensions of the selection menu | LSPosed |
| Components | Disables or enables tiles, shortcuts and widgets for real | Root (su) |
| Backup and diagnostics | JSON rule backup, diagnostic bundle | None |

The two capability groups are independent: rules and ordering work without Root, and reading the component list never requires it.

---

## 2. Architecture

### 2.1 Process model

```
┌────────────────────────┐        ┌─────────────────────────────┐
│  Manager app process   │        │  system_server (injected)   │
│  ui/ data/ domain/     │        │  xposed/                    │
│                        │        │                             │
│  RuleRepository ───────┼─config▶│  ListCleanerModule          │
│  (SharedPreferences)   │ chunked│   ├─ queryIntentActivities  │
│                        │protocol│   ├─ mSortedList ordering   │
│  RuntimeStatus ◀───────┼───ack──│   ├─ AppsFilter visibility  │
│  (hits / menu snapshot)│ label  │   ├─ FloatingToolbar probe  │
└────────────────────────┘        │   └─ component guard        │
                                   └─────────────────────────────┘
```

- **Config push**: `RuntimeProtocol` splits the serialized `ModuleConfig` into chunks of at most 20k characters and hands them to the hook through Intent extras on `PackageManager` queries.
- **Status pull**: the hook encodes `hookCompat:digest:hits…:menu snapshot` into `ResolveInfo.nonLocalizedLabel`, and the manager parses it into `RuntimeStatus`.
- The channel does not depend on RemotePreferences cache freshness, so an older hook can still reject a newer config.

### 2.2 Layout

```
app/src/main/java/com/eio258/cleanchooser/
├── ListCleanerApp.kt          entry point: session, sync, RuntimeStatus
├── ui/                        Compose Material3, single activity
│   ├── MainActivity.kt        Scaffold: top bar / bottom bar / routes
│   ├── Theme.kt               brand colors, type scale, shapes
│   ├── DesignSystem.kt        shared rows, cards, segmented tabs
│   ├── HomeScreen.kt          health strip + three task cards
│   ├── WorkbenchScreen.kt     rules / ordering / components / text menu
│   ├── RulesList.kt           rules segment
│   ├── PriorityList.kt        ordering segment with drag
│   ├── ComponentsList.kt      root-managed components
│   ├── TextMenuScreen.kt      system actions + extensions
│   ├── AppDetailScreen.kt     per-app detail
│   ├── ListHeader.kt          channel chips + filter panel
│   ├── MainControls.kt        top bar and search field
│   ├── StatusScreen.kt        runtime / sync / backup / diagnostics
│   └── ...                    dialogs, scope, discovery controllers
├── data/                      RuleRepository, IntentCatalog, RootComponentCatalog
├── domain/                    pure policy objects and wire protocols
├── runtime/                   boot reconcile, JobService, ServiceSession
└── xposed/                    system_server side hooks
    ├── ListCleanerModule.kt   query / ordering / visibility / ack
    ├── MenuSnapshotProbe.kt   text menu observation
    ├── ComponentStateGuardModule.kt
    ├── ComponentDiscoveryFilterModule.kt
    └── DirectShareConsistencyModule.kt
```

### 2.3 Data model

| Type | Purpose | Storage |
|---|---|---|
| `ComponentRule` | one candidate rule (`PROCESS_TEXT\|pkg\|class`) | rule set |
| `PriorityConfig` | per-kind app order and component titles | prefs JSON |
| `OpenTypeConfig` | open-type sub-rules and priority | prefs JSON |
| `BrowserLinkConfig` | host rules and host priority | prefs JSON |
| `TextMenuSnapshot` | observed system menu order, read-only | runtime channel |
| `ModuleConfig` | atomic snapshot of everything above | config channel |

### 2.4 Hook points

| Target | Stage | Purpose |
|---|---|---|
| `IPackageManagerBase.queryIntentActivities` | query | filter candidates, attach policy metadata, answer ack |
| `IntentResolver.mSortedList` | ordering | apply priority while preserving fixed slots |
| `AppsFilter` | visibility | hide target packages per caller |
| `FloatingToolbar` menu build | observation | capture the real text menu, read-only |
| Component discovery / state | guard | prevent re-enabling and filter discovery results |

### 2.5 UI structure

```
Bottom bar, 3 items
├─ Home       health strip + rules/ordering/components cards + backup, diagnostics
├─ Workbench  search + four segments
│             ├─ Rules       channel chips + filter panel + app rows
│             ├─ Ordering    drag to reorder, rank badges
│             ├─ Components  kind chips + enabled state
│             └─ Text menu   system actions (read-only) + extensions
└─ Status     display mode / runtime / sync / backup / diagnostics
```

The detail page is a second-level route; the bottom bar is hidden while it is open.

---

## 3. Build

Requires JDK 17, Android SDK 37, Gradle 9.4.1 and AGP 9.2.

```bash
gradle :app:assembleClean      # clean + debug, guarantees a compact APK
gradle :app:assembleDebug      # incremental build
gradle :app:testDebugUnitTest
```

Put `sdk.dir=<your SDK>` in `local.properties`; do not commit it.

`verifyApkSize` compares the file size against the sum of zip entries after packaging and warns when stale bytes remain.

---

## 4. Usage

1. Install the APK.
2. Enable the module in LSPosed; the static scope is `system`, `android` and `com.android.intentresolver`.
3. Reboot so the hook is loaded.
4. Confirm the home page shows "connected", then configure rules in the workbench.
5. Grant Root when you want to manage components.

An already-open menu must be closed and reopened before new rules apply.

---

## 5. Differences from upstream

- Package `com.yagay.ListCleaner` → `com.eio258.cleanchooser`; app name is CleanChooser.
- Full UI rewrite: three-page navigation, shared row components, brand palette and type scale, bottom sheets instead of native dropdowns and dialogs.
- New text-menu workspace and menu observation hook.
- Removed inline expansion, `ModuleStatusRow`, `ComponentRow`, `RuntimePanel`, plus 170 unused string entries.
- Debug builds now run R8, shrinking the APK from roughly 56 MB to 9.5 MB.

See [NOTICE.md](NOTICE.md) for the complete list.

---

## 6. License and provenance

This repository is not an original work; the two sources below must be treated separately.

### 6.1 Upstream: yagay/ListCleaner

| Item | Value |
|---|---|
| Repository | https://github.com/yagay/ListCleaner |
| Commit used | `0d8657c` |
| License | **no LICENSE file in the repository** |

No license means all rights reserved. This repository is a modified version and **carries no license and no upstream permission**. Obtain permission from the upstream author before publishing binaries or source; otherwise keep it local.

### 6.2 Reference: txtoi (text selection menu control)

| Item | Value |
|---|---|
| Repository | https://github.com/TheKingBucket001/txtoi |
| License | **GPL-3.0-only** |
| Usage | interaction and data design only, **no source code copied** |

Because no txtoi code was copied, this repository is not a derivative work of txtoi and is not required to be GPL. If txtoi source is ever merged, the whole project must be released under GPL-3.0 with complete source; that is **not** the case today.

GPL-3.0 text: https://www.gnu.org/licenses/gpl-3.0.txt

### 6.3 Feature provenance

| Feature | Source | Implementation |
|---|---|---|
| Share / open-with filtering | upstream ListCleaner | existing code, package renamed |
| Priority ordering, open types, browser hosts | upstream ListCleaner | existing code |
| Tiles / shortcuts / widgets | upstream ListCleaner | existing code |
| Three-page navigation, four segments, home cards | this project | rewritten |
| Shared rows, brand palette, bottom sheets | this project | rewritten |
| Text menu hide and reorder | txtoi (design reference) | independently implemented |
| Text menu reset order / show all | txtoi (design reference) | independently implemented |
| System menu observation | txtoi (idea reference) | independent FloatingToolbar implementation |

Third-party dependencies and licenses: [NOTICE.md](NOTICE.md) section 4.
