package com.eio258.cleanchooser.data

import android.content.Context
import com.eio258.cleanchooser.R
import com.eio258.cleanchooser.domain.ComponentRule
import com.eio258.cleanchooser.domain.RuleBackup
import com.eio258.cleanchooser.domain.DisplayMode
import com.eio258.cleanchooser.domain.PriorityConfig
import com.eio258.cleanchooser.domain.IntentKind
import com.eio258.cleanchooser.domain.ModuleConfig
import com.eio258.cleanchooser.domain.OpenPreset
import com.eio258.cleanchooser.domain.OpenTypeConfig
import com.eio258.cleanchooser.domain.CustomOpenDefinition
import com.eio258.cleanchooser.domain.BrowserLinkConfig
import com.eio258.cleanchooser.domain.normalizeBrowserHost
import com.eio258.cleanchooser.domain.PackageIdentity
import com.eio258.cleanchooser.domain.VisibilityCompatConfig
import com.eio258.cleanchooser.domain.VisibilityScope
import com.eio258.cleanchooser.domain.MultiSpaceBackup
import com.eio258.cleanchooser.domain.SpaceBackupEntry
import com.eio258.cleanchooser.domain.UserSpaceRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SpaceBackupPreview(
    val sourceUserId: Int,
    val sourceName: String,
    val rules: Int,
    val rootDisabledRecords: Int,
)

data class SpaceBackupInspection(
    val version: Int,
    val spaces: List<SpaceBackupPreview>,
)

class RuleRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(LOCAL_PREFS, Context.MODE_PRIVATE)
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    // v13 discriminator and version MUST be present, even though their Kotlin fields have defaults.
    private val portableBackupJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutableRules = MutableStateFlow(
        prefs.getStringSet(KEY_RULES, emptySet()).orEmpty().mapNotNull(ComponentRule::fromId).toSet()
    )
    private val mutableMode = MutableStateFlow(DisplayMode.fromStored(prefs.getString(KEY_DISPLAY_MODE, null), prefs.getBoolean(KEY_BLACKLIST, true)))
    private val mutablePriorities = MutableStateFlow(runCatching {
        json.decodeFromString(PriorityConfig.serializer(), prefs.getString(KEY_PRIORITIES, null) ?: "{}").validated()
    }.getOrDefault(PriorityConfig()))
    private val mutableOpenTypes = MutableStateFlow(runCatching {
        json.decodeFromString(OpenTypeConfig.serializer(), prefs.getString(KEY_OPEN_TYPES, null) ?: "{}").validated()
    }.getOrDefault(OpenTypeConfig()))
    private val mutableBrowserLinks = MutableStateFlow(runCatching {
        json.decodeFromString(BrowserLinkConfig.serializer(), prefs.getString(KEY_BROWSER_LINKS, null) ?: "{}").validated()
    }.getOrDefault(BrowserLinkConfig()))
    private val mutableTextMenuOrder = MutableStateFlow(
        runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                prefs.getString(KEY_TEXT_MENU_ORDER, null) ?: "[]"
            )
        }.getOrDefault(emptyList()).filter { ComponentRule.fromId(it)?.kind == IntentKind.PROCESS_TEXT }
    )
    private val mutableDiagnostic = MutableStateFlow(prefs.getBoolean(KEY_DIAGNOSTIC, false))
    private val mutableHiddenFromApps = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_FROM_APPS, emptySet()).orEmpty().toSet())
    private val mutableVisibilityScopes = MutableStateFlow(
        prefs.getStringSet(KEY_VISIBILITY_SCOPES, emptySet()).orEmpty().mapNotNull { name ->
            runCatching { VisibilityScope.valueOf(name) }.getOrNull()
        }.toSet()
    )
    private val mutableVisibilityFullPackages = MutableStateFlow<Map<VisibilityScope, Set<String>>>(emptyMap())
    val openTypes: StateFlow<OpenTypeConfig> = mutableOpenTypes.asStateFlow()
    val browserLinks: StateFlow<BrowserLinkConfig> = mutableBrowserLinks.asStateFlow()
    val hiddenFromApps: StateFlow<Set<String>> = mutableHiddenFromApps.asStateFlow()
    val visibilityScopes: StateFlow<Set<VisibilityScope>> = mutableVisibilityScopes.asStateFlow()
    val visibilityFullPackages: StateFlow<Map<VisibilityScope, Set<String>>> = mutableVisibilityFullPackages.asStateFlow()
    val rules: StateFlow<Set<ComponentRule>> = mutableRules.asStateFlow()
    val displayMode: StateFlow<DisplayMode> = mutableMode.asStateFlow()
    val priorities: StateFlow<PriorityConfig> = mutablePriorities.asStateFlow()
    val diagnosticMode: StateFlow<Boolean> = mutableDiagnostic.asStateFlow()
    val textMenuOrder: StateFlow<List<String>> = mutableTextMenuOrder.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    // ---- zh  ----
    // zh  fail-open zh  Android zh hook zh 
    // SHOW_ALL + zh /zh discovery zh  resolver zh 
    // zh KEY_RULES zh  active zh 
    // zh user_spaces_v1 zh  JSON Map<Int,UserSpaceRules>zh 
    // zh  key=0zh  ModuleConfig.validated zh  1..99999 zh 
    // ModuleConfig.userSpaces zh  0zh 
    private val storedSpaces = mutableMapOf<Int, UserSpaceRules>().apply {
        prefs.getString(KEY_USER_SPACES, null)?.let { encoded ->
            if (encoded.length <= MAX_BACKUP_CHARS) runCatching {
                json.decodeFromString(
                    MapSerializer(Int.serializer(), UserSpaceRules.serializer()), encoded
                )
            }.getOrNull()?.forEach { (userId, space) ->
                if (userId in 0..99999 && size < MAX_SPACES + 1) {
                    runCatching { space.validated() }.getOrNull()?.let { put(userId, it) }
                }
            }
        }
    }
    private val mutableActiveSpace = MutableStateFlow(0)
    val activeSpace: StateFlow<Int> = mutableActiveSpace.asStateFlow()

    /** Discovered user spaces are exposed by UserSpaceManager; selection state here. */

    init {
        val restored = prefs.getInt(KEY_ACTIVE_SPACE, 0)
        if (restored != 0 && storedSpaces.containsKey(restored)) {
            // zh  active zh map zh 
            // zh 
            loadSpaceIntoFlows(requireNotNull(storedSpaces[restored]))
            mutableActiveSpace.value = restored
            storedSpaces[restored] = currentSpaceSnapshot()
        } else {
            // zh  map zh  0zh /zh 
            storedSpaces[0] = currentSpaceSnapshot()
        }
    }

    fun hasLocalConfiguration(): Boolean = prefs.contains(KEY_INITIALIZED) || prefs.contains(KEY_RULES) ||
        prefs.contains(KEY_DISPLAY_MODE) || prefs.contains(KEY_BLACKLIST) || prefs.contains(KEY_PRIORITIES) ||
        prefs.contains(KEY_HIDDEN_FROM_APPS) || prefs.contains(KEY_OPEN_TYPES) || prefs.contains(KEY_BROWSER_LINKS) || prefs.contains(KEY_VISIBILITY_SCOPES) ||
        prefs.contains(KEY_TILES) || prefs.contains(KEY_DEFAULT_OPEN) || prefs.contains(KEY_USER_SPACES)

    fun markInitialized() {
        prefs.edit().putBoolean(KEY_INITIALIZED, true).remove(KEY_TILES).remove(KEY_DEFAULT_OPEN).apply()
    }

    @Synchronized fun restoreRemote(config: ModuleConfig) {
        applyConfig(config, config.mode != DisplayMode.SHOW_SELECTED)
    }

    /**
     * Legacy ModuleConfig fields remain deserializable, but new remote snapshots always use defaults.
     *
     * zh  active zh userSpaces zh 
     * hook zh  Binder uid zh  user id zh  userSpaceszh  fail-openzh 
     * SHOW_ALL + zh ModuleConfig.validated() zh  userSpaces key ∈ 1..99999zh 
     * zh  active != 0 zh  0 zh  userSpaces zh user 0 zh 
     * zh  `it != 0` zh 
     */
    @Synchronized fun remoteSnapshot(): ModuleConfig {
        val active = currentSpaceSnapshot()
        val userSpaces = storedSpaces.toMutableMap()
            .apply { put(mutableActiveSpace.value, active) }
            .filterKeys { it != mutableActiveSpace.value && it != 0 }
        return ModuleConfig(
            rules = mutableRules.value.toSet(), mode = mutableMode.value, priorities = mutablePriorities.value,
            diagnostic = mutableDiagnostic.value, managerAppId = android.os.Process.myUid() % 100_000,
            hiddenFromApps = mutableHiddenFromApps.value.toSet(), openTypes = mutableOpenTypes.value,
            browserLinks = mutableBrowserLinks.value,
            textMenuOrder = mutableTextMenuOrder.value,
            visibilityCompat = VisibilityCompatConfig(
                scopes = mutableVisibilityScopes.value.toSet(),
                fullPackages = mutableVisibilityFullPackages.value.mapValues { it.value.toSet() }
            ).validated(),
            userSpaces = userSpaces
        ).validated()
    }

    private fun currentSpaceSnapshot(): UserSpaceRules = UserSpaceRules(
        rules = mutableRules.value.toSet(),
        mode = mutableMode.value,
        priorities = mutablePriorities.value,
        diagnostic = mutableDiagnostic.value,
        hiddenFromApps = mutableHiddenFromApps.value.toSet(),
        openTypes = mutableOpenTypes.value,
        browserLinks = mutableBrowserLinks.value,
        visibilityCompat = VisibilityCompatConfig(scopes = mutableVisibilityScopes.value.toSet())
    )

    /** zh  StateFlowszh  fullPackages zh  */
    private fun loadSpaceIntoFlows(space: UserSpaceRules) {
        mutableRules.value = space.rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        mutableMode.value = space.mode
        mutablePriorities.value = runCatching { space.priorities.validated() }.getOrDefault(PriorityConfig())
        mutableOpenTypes.value = space.openTypes
        mutableBrowserLinks.value = space.browserLinks
        mutableDiagnostic.value = space.diagnostic
        mutableHiddenFromApps.value = space.hiddenFromApps
        mutableVisibilityScopes.value = space.visibilityCompat.scopes
        mutableVisibilityFullPackages.value = emptyMap()
    }

    private fun persistSpaces() {
        prefs.edit().putString(
            KEY_USER_SPACES,
            json.encodeToString(
                MapSerializer(Int.serializer(), UserSpaceRules.serializer()),
                storedSpaces.toMap()
            )
        ).apply()
    }

    /** zh  active zh  init zh  */
    private fun persistActiveSpaceKeys() {
        prefs.edit()
            .putStringSet(KEY_RULES, mutableRules.value.map(ComponentRule::id).toSet())
            .putBoolean(KEY_BLACKLIST, mutableMode.value != DisplayMode.SHOW_SELECTED)
            .putString(KEY_DISPLAY_MODE, mutableMode.value.name)
            .putString(KEY_PRIORITIES, encodePriorities())
            .putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), mutableOpenTypes.value))
            .putString(KEY_BROWSER_LINKS, json.encodeToString(BrowserLinkConfig.serializer(), mutableBrowserLinks.value))
            .putBoolean(KEY_DIAGNOSTIC, mutableDiagnostic.value)
            .putStringSet(KEY_HIDDEN_FROM_APPS, mutableHiddenFromApps.value)
            .putStringSet(KEY_VISIBILITY_SCOPES, mutableVisibilityScopes.value.map { it.name }.toSet())
            .putBoolean(KEY_INITIALIZED, true)
            .remove(KEY_TILES)
            .remove(KEY_DEFAULT_OPEN)
            .apply()
    }

    /**
     * zh  active zh  rules/priorities/openTypes/browserLinks zh 
     * user_spaces_v1zh textMenuOrder zh 
     * zh UserSpaceRules zh 
     */
    @Synchronized fun switchSpace(userId: Int) {
        require(userId in 0..99999)
        if (userId == mutableActiveSpace.value) return
        storedSpaces[mutableActiveSpace.value] = currentSpaceSnapshot()
        loadSpaceIntoFlows(storedSpaces[userId] ?: UserSpaceRules())
        mutableActiveSpace.value = userId
        prefs.edit().putInt(KEY_ACTIVE_SPACE, userId).apply()
        persistSpaces()
        persistActiveSpaceKeys()
        mutableRevision.value++
    }

    @Synchronized fun setHiddenFromApps(packages: Set<String>) {
        val valid = normalizeHiddenApps(packages)
        if (valid == mutableHiddenFromApps.value) return
        mutableHiddenFromApps.value = valid
        prefs.edit().putStringSet(KEY_HIDDEN_FROM_APPS, valid).apply()
        mutableRevision.value++
    }

    @Synchronized fun setVisibilityScopes(scopes: Set<VisibilityScope>) {
        val valid = scopes.toSet()
        if (valid == mutableVisibilityScopes.value) return
        mutableVisibilityScopes.value = valid
        prefs.edit().putStringSet(KEY_VISIBILITY_SCOPES, valid.map { it.name }.toSet()).apply()
        mutableRevision.value++
    }

    /** Derived from the current candidate catalog. Not backed up; rebuilt after scans/rule changes. */
    @Synchronized fun setVisibilityFullPackages(packages: Map<VisibilityScope, Set<String>>) {
        val next = VisibilityCompatConfig(fullPackages = packages).validated().fullPackages
            .mapValues { it.value.toSet() }
        if (next == mutableVisibilityFullPackages.value) return
        mutableVisibilityFullPackages.value = next
        mutableRevision.value++
    }

    @Synchronized fun setCustomOpenDefinition(preset: OpenPreset, definition: CustomOpenDefinition?) {
        require(preset.isCustom) { appContext.getString(R.string.repo_custom_slot_only) }
        val current = mutableOpenTypes.value
        val definitions = current.customDefinitions.toMutableMap()
        val rules = current.rules.toMutableMap()
        val priorities = current.priorities.toMutableMap()
        if (definition == null) {
            definitions.remove(preset)
            rules.remove(preset)
            priorities.remove(preset)
        } else definitions[preset] = definition.validated()
        setOpenTypes(current.copy(rules = rules, priorities = priorities, customDefinitions = definitions))
    }

    @Synchronized fun setOpenTypeSelected(preset: OpenPreset, rules: Collection<ComponentRule>, selected: Boolean) {
        require(preset != OpenPreset.BROWSER)
        if (preset.isCustom) require(preset in mutableOpenTypes.value.customDefinitions) {
            appContext.getString(R.string.repo_custom_type_not_configured)
        }
        val ids = rules.filter { it.kind == IntentKind.OPEN && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.toSet()
        if (ids.isEmpty()) return
        val map = mutableOpenTypes.value.rules.toMutableMap()
        val nextSet = map[preset].orEmpty().toMutableSet().apply {
            if (selected) addAll(ids) else removeAll(ids)
        }
        if (nextSet.isEmpty()) map.remove(preset) else map[preset] = nextSet
        setOpenTypes(mutableOpenTypes.value.copy(rules = map))
    }

    @Synchronized fun toggleOpenType(preset: OpenPreset, rule: ComponentRule) {
        require(preset != OpenPreset.BROWSER && rule.kind == IntentKind.OPEN && rule.isValid())
        setOpenTypeSelected(preset, listOf(rule), rule.id !in mutableOpenTypes.value.rules[preset].orEmpty())
    }

    @Synchronized fun invertOpenTypeSelected(preset: OpenPreset, rules: Collection<ComponentRule>) {
        require(preset != OpenPreset.BROWSER)
        if (preset.isCustom) require(preset in mutableOpenTypes.value.customDefinitions) {
            appContext.getString(R.string.repo_custom_type_not_configured)
        }
        val valid = rules.filter { it.kind == IntentKind.OPEN && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.distinct()
        if (valid.isEmpty()) return
        val map = mutableOpenTypes.value.rules.toMutableMap()
        val nextSet = map[preset].orEmpty().toMutableSet().apply {
            valid.forEach { if (!add(it)) remove(it) }
        }
        if (nextSet.isEmpty()) map.remove(preset) else map[preset] = nextSet
        setOpenTypes(mutableOpenTypes.value.copy(rules = map))
    }

    @Synchronized fun setOpenTypePriority(preset: OpenPreset, packages: List<String>) {
        require(preset != OpenPreset.BROWSER)
        if (preset.isCustom) require(preset in mutableOpenTypes.value.customDefinitions) {
            appContext.getString(R.string.repo_custom_type_not_configured)
        }
        val map = mutableOpenTypes.value.priorities.toMutableMap().apply {
            if (packages.isEmpty()) remove(preset) else put(preset, packages.toList())
        }
        setOpenTypes(mutableOpenTypes.value.copy(priorities = map))
    }

    private fun setOpenTypes(value: OpenTypeConfig) {
        val next = value.validated()
        if (next == mutableOpenTypes.value) return
        mutableOpenTypes.value = next
        prefs.edit().putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), next)).apply()
        mutableRevision.value++
    }

    @Synchronized fun setBrowserHosts(hosts: Set<String>) {
        val normalized = hosts.map { requireNotNull(normalizeBrowserHost(it)) { "invalid_browser_host" } }.toSet()
        require(normalized.size <= BrowserLinkConfig.MAX_HOSTS) { "too_many_browser_hosts" }
        val current = mutableBrowserLinks.value
        setBrowserLinks(
            current.copy(
                hosts = normalized,
                rules = current.rules.filterKeys { it in normalized },
                priorities = current.priorities.filterKeys { it in normalized }
            )
        )
    }

    @Synchronized fun setBrowserHostSelected(host: String, rules: Collection<ComponentRule>, selected: Boolean) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(normalized in mutableBrowserLinks.value.hosts) { "browser_host_not_configured" }
        val ids = rules.filter { it.kind == IntentKind.DEEP_LINK && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.toSet()
        if (ids.isEmpty()) return
        val map = mutableBrowserLinks.value.rules.toMutableMap()
        val next = map[normalized].orEmpty().toMutableSet().apply {
            if (selected) addAll(ids) else removeAll(ids)
        }
        if (next.isEmpty()) map.remove(normalized) else map[normalized] = next
        setBrowserLinks(mutableBrowserLinks.value.copy(rules = map))
    }

    @Synchronized fun toggleBrowserHost(host: String, rule: ComponentRule) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(rule.kind == IntentKind.DEEP_LINK && rule.isValid())
        setBrowserHostSelected(normalized, listOf(rule), rule.id !in mutableBrowserLinks.value.rules[normalized].orEmpty())
    }

    @Synchronized fun invertBrowserHostSelected(host: String, rules: Collection<ComponentRule>) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(normalized in mutableBrowserLinks.value.hosts) { "browser_host_not_configured" }
        val valid = rules.filter { it.kind == IntentKind.DEEP_LINK && it.isValid() }
            .map { requireNotNull(ComponentRule.fromId(it.id)).id }.distinct()
        if (valid.isEmpty()) return
        val map = mutableBrowserLinks.value.rules.toMutableMap()
        val next = map[normalized].orEmpty().toMutableSet().apply {
            valid.forEach { if (!add(it)) remove(it) }
        }
        if (next.isEmpty()) map.remove(normalized) else map[normalized] = next
        setBrowserLinks(mutableBrowserLinks.value.copy(rules = map))
    }

    @Synchronized fun setBrowserHostPriority(host: String, packages: List<String>) {
        val normalized = requireNotNull(normalizeBrowserHost(host)) { "invalid_browser_host" }
        require(normalized in mutableBrowserLinks.value.hosts) { "browser_host_not_configured" }
        val map = mutableBrowserLinks.value.priorities.toMutableMap().apply {
            if (packages.isEmpty()) remove(normalized) else put(normalized, packages.toList())
        }
        setBrowserLinks(mutableBrowserLinks.value.copy(priorities = map))
    }

    private fun setBrowserLinks(value: BrowserLinkConfig) {
        val next = value.validated()
        if (next == mutableBrowserLinks.value) return
        mutableBrowserLinks.value = next
        prefs.edit().putString(KEY_BROWSER_LINKS, json.encodeToString(BrowserLinkConfig.serializer(), next)).apply()
        mutableRevision.value++
    }

    @Synchronized fun setDiagnosticMode(enabled: Boolean) {
        if (mutableDiagnostic.value == enabled) return
        mutableDiagnostic.value = enabled
        prefs.edit().putBoolean(KEY_DIAGNOSTIC, enabled).apply()
        mutableRevision.value++
    }

    @Synchronized fun setPriority(kind: IntentKind, packages: List<String>) {
        val next = mutablePriorities.value.copy(apps = mutablePriorities.value.apps.toMutableMap().apply {
            if (packages.isEmpty()) remove(kind) else put(kind, packages.toList())
        }).validated()
        if (next == mutablePriorities.value) return
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    @Synchronized fun setComponentTitle(ruleId: String, title: String?) {
        val parsed = requireNotNull(ComponentRule.fromId(ruleId)) {
            appContext.getString(R.string.repo_invalid_component_id)
        }
        require(parsed.id == ruleId) { appContext.getString(R.string.repo_component_id_not_normalized) }
        val trimmed = title?.trim().orEmpty()
        val titles = mutablePriorities.value.titles.toMutableMap().apply {
            if (trimmed.isEmpty()) remove(ruleId) else put(ruleId, trimmed)
        }
        val next = mutablePriorities.value.copy(titles = titles).validated()
        if (next == mutablePriorities.value) return
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(next)).apply()
        mutablePriorities.value = next
        mutableRevision.value++
    }

    fun encodePriorities(value: PriorityConfig = mutablePriorities.value): String =
        json.encodeToString(PriorityConfig.serializer(), value)

    @Synchronized fun toggle(rule: ComponentRule) {
        require(rule.isValid()) { appContext.getString(R.string.repo_invalid_component_rule) }
        val canonical = requireNotNull(ComponentRule.fromId(rule.id))
        updateRules(mutableRules.value.toMutableSet().apply { if (!add(canonical)) remove(canonical) }.toSet())
    }

    @Synchronized fun setSelected(rules: Collection<ComponentRule>, selected: Boolean) {
        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }
        val next = mutableRules.value.toMutableSet().apply {
            if (selected) addAll(valid) else removeAll(valid.toSet())
        }.toSet()
        updateRules(next)
    }

    @Synchronized fun invertSelected(rules: Collection<ComponentRule>) {
        val valid = rules.filter(ComponentRule::isValid).mapNotNull { ComponentRule.fromId(it.id) }.distinct()
        if (valid.isEmpty()) return
        updateRules(mutableRules.value.toMutableSet().apply {
            valid.forEach { rule -> if (!add(rule)) remove(rule) }
        }.toSet())
    }

    @Synchronized fun setTextMenuOrder(order: List<String>) {
        val valid = order.distinct().filter { ComponentRule.fromId(it)?.kind == IntentKind.PROCESS_TEXT }.take(512)
        if (valid == mutableTextMenuOrder.value) return
        mutableTextMenuOrder.value = valid
        prefs.edit().putString(
            KEY_TEXT_MENU_ORDER,
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                valid
            )
        ).apply()
        mutableRevision.value++
    }

    // ---- Restore-to-default: one method per configuration section, plus the combined reset ----
    // Each section restores independently, so a mistake in one place never forces the user to
    // rebuild the rest of the configuration. Diagnostic mode is never touched.

    @Synchronized fun resetRules() {
        if (mutableRules.value.isEmpty()) return
        mutableRules.value = emptySet()
        prefs.edit().putStringSet(KEY_RULES, emptySet()).apply()
        mutableRevision.value++
    }

    @Synchronized fun resetPriorities() {
        if (mutablePriorities.value == PriorityConfig()) return
        mutablePriorities.value = PriorityConfig()
        prefs.edit().putString(KEY_PRIORITIES, encodePriorities(PriorityConfig())).apply()
        mutableRevision.value++
    }

    @Synchronized fun resetOpenTypes() {
        if (mutableOpenTypes.value == OpenTypeConfig()) return
        mutableOpenTypes.value = OpenTypeConfig()
        prefs.edit()
            .putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), OpenTypeConfig()))
            .apply()
        mutableRevision.value++
    }

    @Synchronized fun resetBrowserHosts() {
        if (mutableBrowserLinks.value == BrowserLinkConfig()) return
        mutableBrowserLinks.value = BrowserLinkConfig()
        prefs.edit()
            .putString(KEY_BROWSER_LINKS, json.encodeToString(BrowserLinkConfig.serializer(), BrowserLinkConfig()))
            .apply()
        mutableRevision.value++
    }

    @Synchronized fun resetTextMenuOrder() {
        if (mutableTextMenuOrder.value.isEmpty()) return
        mutableTextMenuOrder.value = emptyList()
        prefs.edit().putString(
            KEY_TEXT_MENU_ORDER,
            json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                emptyList<String>()
            )
        ).apply()
        mutableRevision.value++
    }

    @Synchronized fun resetVisibility() {
        val cleared = mutableHiddenFromApps.value.isEmpty() &&
            mutableVisibilityScopes.value.isEmpty() &&
            mutableVisibilityFullPackages.value.isEmpty()
        if (cleared) return
        mutableHiddenFromApps.value = emptySet()
        mutableVisibilityScopes.value = emptySet()
        mutableVisibilityFullPackages.value = emptyMap()
        prefs.edit()
            .putStringSet(KEY_HIDDEN_FROM_APPS, emptySet())
            .putStringSet(KEY_VISIBILITY_SCOPES, emptySet())
            .apply()
        mutableRevision.value++
    }

    /** All six menu sections above in one SharedPreferences transaction and one revision. */
    @Synchronized fun resetMenuConfig() {
        // zh  active zh  user_spaces_v1zh 
        if (mutableActiveSpace.value != 0) {
            loadSpaceIntoFlows(UserSpaceRules())
            storedSpaces[mutableActiveSpace.value] = UserSpaceRules()
            persistSpaces()
            persistActiveSpaceKeys()
            mutableRevision.value++
            return
        }
        val diagnostic = mutableDiagnostic.value
        prefs.edit()
            .putStringSet(KEY_RULES, emptySet())
            .putBoolean(KEY_BLACKLIST, true)
            .putString(KEY_DISPLAY_MODE, DisplayMode.HIDE_SELECTED.name)
            .putString(KEY_PRIORITIES, encodePriorities(PriorityConfig()))
            .putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), OpenTypeConfig()))
            .putString(KEY_BROWSER_LINKS, json.encodeToString(BrowserLinkConfig.serializer(), BrowserLinkConfig()))
            .putString(
                KEY_TEXT_MENU_ORDER,
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                    emptyList<String>()
                )
            )
            .putBoolean(KEY_DIAGNOSTIC, diagnostic)
            .putStringSet(KEY_HIDDEN_FROM_APPS, emptySet())
            .putStringSet(KEY_VISIBILITY_SCOPES, emptySet())
            .putBoolean(KEY_INITIALIZED, true)
            .remove(KEY_TILES)
            .remove(KEY_DEFAULT_OPEN)
            .apply()

        mutableRules.value = emptySet()
        mutableMode.value = DisplayMode.HIDE_SELECTED
        mutablePriorities.value = PriorityConfig()
        mutableOpenTypes.value = OpenTypeConfig()
        mutableBrowserLinks.value = BrowserLinkConfig()
        mutableTextMenuOrder.value = emptyList()
        mutableHiddenFromApps.value = emptySet()
        mutableVisibilityScopes.value = emptySet()
        mutableVisibilityFullPackages.value = emptyMap()
        mutableRevision.value++
    }

    @Synchronized fun setDisplayMode(value: DisplayMode) {
        if (mutableMode.value == value) return
        mutableMode.value = value
        prefs.edit().putString(KEY_DISPLAY_MODE, value.name).apply()
        mutableRevision.value++
    }

    @Synchronized fun replace(
        rules: Set<ComponentRule>, blacklist: Boolean, priorities: PriorityConfig = PriorityConfig(),
        displayMode: DisplayMode = DisplayMode.fromStored(null, blacklist), openTypes: OpenTypeConfig = OpenTypeConfig()
    ) {
        applyConfig(
            ModuleConfig(
                rules = rules,
                mode = displayMode,
                priorities = priorities,
                diagnostic = mutableDiagnostic.value,
                hiddenFromApps = mutableHiddenFromApps.value,
                openTypes = openTypes,
                browserLinks = mutableBrowserLinks.value,
                visibilityCompat = VisibilityCompatConfig(scopes = mutableVisibilityScopes.value)
            ),
            blacklist
        )
    }

    /** Snapshot includes unswitched edits, but never derives settings from another profile. */
    @Synchronized private fun allSpaceSettings(): Map<Int, UserSpaceRules> =
        storedSpaces.toMutableMap()
            .apply { put(mutableActiveSpace.value, currentSpaceSnapshot()) }
            .toMap()

    /** Select any subset of spaces; names are informational and not trusted during restore. */
    @Synchronized fun exportSpaces(
        userIds: Set<Int>,
        names: Map<Int, String>,
        rootDisabledKeys: Set<String> = emptySet(),
    ): String {
        require(userIds.isNotEmpty() && userIds.size <= MAX_SPACES)
        val available = allSpaceSettings()
        val entries = userIds.sorted().map { userId ->
            require(userId in 0..99999)
            val settings = available[userId] ?: UserSpaceRules()
            SpaceBackupEntry(
                sourceUserId = userId,
                sourceName = names[userId]?.trim()?.take(100)?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.space_user_title, userId),
                settings = settings.validated(),
                rootDisabledComponents = rootDisabledKeys.filterTo(linkedSetOf()) {
                    it.startsWith("$userId|")
                },
            )
        }
        val encoded = portableBackupJson.encodeToString(
            MultiSpaceBackup.serializer(),
            MultiSpaceBackup(spaces = entries).validated()
        )
        require(encoded.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        return encoded
    }

    /**
     * Legacy (v1–v11) RuleBackup archives contain exactly one set of resolver rules. Treat it
     * as one source entry that can be explicitly mapped to *any* target Android user.
     */
    @Synchronized private fun decodePortableBackup(content: String): MultiSpaceBackup {
        require(content.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        val root = json.parseToJsonElement(content).jsonObject
        if (root["format"]?.jsonPrimitive?.content == MultiSpaceBackup.FORMAT) {
            return json.decodeFromString(MultiSpaceBackup.serializer(), content).validated()
        }
        val old = json.decodeFromString(RuleBackup.serializer(), content)
        require(old.version in 1..11) {
            appContext.getString(R.string.repo_backup_unsupported_version, old.version)
        }
        if (old.version >= 3) requireNotNull(old.displayMode) {
            appContext.getString(R.string.repo_backup_missing_display_mode)
        }
        require(old.rules.all(ComponentRule::isValid)) {
            appContext.getString(R.string.repo_backup_invalid_component)
        }
        val settings = UserSpaceRules(
            rules = old.rules,
            mode = old.displayMode ?: DisplayMode.fromStored(null, old.blacklist),
            priorities = if (old.version == 1) PriorityConfig() else old.priorities,
            diagnostic = false,
            hiddenFromApps = if (old.version >= 5) old.hiddenFromApps else emptySet(),
            openTypes = if (old.version >= 7) old.openTypes else OpenTypeConfig(),
            browserLinks = if (old.version >= 10) old.browserLinks else BrowserLinkConfig(),
            visibilityCompat = VisibilityCompatConfig(
                scopes = if (old.version >= 9) old.visibilityScopes else emptySet()
            ),
        ).validated()
        return MultiSpaceBackup(spaces = listOf(
            SpaceBackupEntry(
                sourceUserId = 0,
                sourceName = appContext.getString(R.string.backup_spaces_legacy_source),
                settings = settings
            )
        )).validated()
    }

    fun inspectSpaces(content: String): SpaceBackupInspection {
        val version = json.parseToJsonElement(content).jsonObject["version"]
            ?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        val parsed = decodePortableBackup(content)
        return SpaceBackupInspection(version, parsed.spaces.map {
            SpaceBackupPreview(
                it.sourceUserId, it.sourceName, it.settings.rules.size, it.rootDisabledComponents.size
            )
        })
    }

    /** Source IDs are never assumed to be equal to target IDs on another phone. */
    @Synchronized fun importSpaces(content: String, mappings: Map<Int, Int>) {
        val configs = decodePortableBackup(content).mapped(mappings)
        val next = allSpaceSettings().toMutableMap().apply { putAll(configs) }
        replaceSpaceSettings(next)
    }

    /**
     * Publishes every space atomically: one commit, then the active space is reloaded into the
     * StateFlows and re-persisted to the top-level section keys. If persistence fails, no
     * in-memory space is mutated and no partial import starts.
     */
    @Synchronized private fun replaceSpaceSettings(requested: Map<Int, UserSpaceRules>) {
        require(requested.containsKey(0) && requested.keys.all { it in 0..99999 })
        require(requested.keys.size <= MAX_SPACES + 1)
        val clean = requested.mapValues { (_, value) ->
            value.copy(visibilityCompat = VisibilityCompatConfig(
                scopes = value.visibilityCompat.scopes
            )).validated()
        }
        val encoded = json.encodeToString(
            MapSerializer(Int.serializer(), UserSpaceRules.serializer()), clean
        )
        require(encoded.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        check(prefs.edit().putString(KEY_USER_SPACES, encoded).commit()) {
            "Space backup could not be committed"
        }
        storedSpaces.clear()
        storedSpaces.putAll(clean)
        loadSpaceIntoFlows(storedSpaces[mutableActiveSpace.value] ?: UserSpaceRules())
        persistActiveSpaceKeys()
        mutableRevision.value++
    }

    @Synchronized fun exportJson(): String = json.encodeToString(
        RuleBackup.serializer(),
        RuleBackup(
            version = 11,
            blacklist = mutableMode.value != DisplayMode.SHOW_SELECTED,
            rules = mutableRules.value,
            priorities = mutablePriorities.value,
            displayMode = mutableMode.value,
            hiddenFromApps = mutableHiddenFromApps.value,
            openTypes = mutableOpenTypes.value,
            browserLinks = mutableBrowserLinks.value,
            visibilityScopes = mutableVisibilityScopes.value
        )
    )

    @Synchronized fun importJson(content: String) {
        require(content.length <= MAX_BACKUP_CHARS) { appContext.getString(R.string.repo_backup_too_large) }
        val backup = json.decodeFromString(RuleBackup.serializer(), content)
        require(backup.version in 1..11) {
            appContext.getString(R.string.repo_backup_unsupported_version, backup.version)
        }
        val displayMode = if (backup.version >= 3) requireNotNull(backup.displayMode) {
            appContext.getString(R.string.repo_backup_missing_display_mode)
        } else DisplayMode.fromStored(null, backup.blacklist)
        applyConfig(
            ModuleConfig(
                rules = backup.rules,
                mode = displayMode,
                priorities = if (backup.version == 1) PriorityConfig() else backup.priorities,
                diagnostic = mutableDiagnostic.value,
                hiddenFromApps = if (backup.version >= 5) backup.hiddenFromApps else emptySet(),
                openTypes = if (backup.version >= 7) backup.openTypes else OpenTypeConfig(),
                browserLinks = if (backup.version >= 10) backup.browserLinks else BrowserLinkConfig(),
                visibilityCompat = VisibilityCompatConfig(
                    scopes = if (backup.version >= 9) backup.visibilityScopes else emptySet()
                )
            ),
            backup.blacklist
        )
    }

    /**
     * Applies a complete persisted configuration as one SharedPreferences transaction and exposes
     * exactly one revision. Derived full-package visibility targets are intentionally discarded and
     * rebuilt from the current catalog after restore/import.
     */
    private fun applyConfig(config: ModuleConfig, legacyBlacklist: Boolean) {
        require(config.rules.size <= MAX_RULES) { appContext.getString(R.string.repo_backup_too_many_rules) }
        require(config.rules.all(ComponentRule::isValid)) { appContext.getString(R.string.repo_backup_invalid_component) }

        val canonicalRules = config.rules.mapNotNull { ComponentRule.fromId(it.id) }.toSet()
        val priorities = config.priorities.validated()
        val openTypes = config.openTypes.validated()
        val browserLinks = config.browserLinks.validated()
        val hiddenFromApps = normalizeHiddenApps(config.hiddenFromApps)
        val visibilityScopes = config.visibilityCompat.scopes.toSet()
        val prepared = config.copy(
            rules = canonicalRules,
            priorities = priorities,
            hiddenFromApps = hiddenFromApps,
            openTypes = openTypes,
            browserLinks = browserLinks,
            textMenuOrder = config.textMenuOrder,
            visibilityCompat = VisibilityCompatConfig(scopes = visibilityScopes)
        ).validated()

        prefs.edit()
            .putStringSet(KEY_RULES, prepared.rules.map(ComponentRule::id).toSet())
            .putBoolean(KEY_BLACKLIST, legacyBlacklist)
            .putString(KEY_DISPLAY_MODE, prepared.mode.name)
            .putString(KEY_PRIORITIES, encodePriorities(prepared.priorities))
            .putString(KEY_OPEN_TYPES, json.encodeToString(OpenTypeConfig.serializer(), prepared.openTypes))
            .putString(KEY_BROWSER_LINKS, json.encodeToString(BrowserLinkConfig.serializer(), prepared.browserLinks))
            .putString(
                KEY_TEXT_MENU_ORDER,
                json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(String.serializer()),
                    prepared.textMenuOrder
                )
            )
            .putBoolean(KEY_DIAGNOSTIC, prepared.diagnostic)
            .putStringSet(KEY_HIDDEN_FROM_APPS, prepared.hiddenFromApps)
            .putStringSet(KEY_VISIBILITY_SCOPES, visibilityScopes.map { it.name }.toSet())
            .putBoolean(KEY_INITIALIZED, true)
            .remove(KEY_TILES)
            .remove(KEY_DEFAULT_OPEN)
            .apply()

        mutableRules.value = prepared.rules
        mutableMode.value = prepared.mode
        mutablePriorities.value = prepared.priorities
        mutableOpenTypes.value = prepared.openTypes
        mutableBrowserLinks.value = prepared.browserLinks
        mutableTextMenuOrder.value = prepared.textMenuOrder
        mutableDiagnostic.value = prepared.diagnostic
        mutableHiddenFromApps.value = prepared.hiddenFromApps
        mutableVisibilityScopes.value = visibilityScopes
        mutableVisibilityFullPackages.value = emptyMap()
        mutableRevision.value++
    }

    private fun normalizeHiddenApps(packages: Set<String>): Set<String> {
        val self = "com.eio258.cleanchooser"
        val valid = packages.asSequence()
            .map(String::trim)
            .filter { it != "android" && it != self && PackageIdentity.valid(it) }
            .take(2_001)
            .toSet()
        require(valid.size <= 2_000) { appContext.getString(R.string.repo_hidden_apps_too_many) }
        return valid
    }

    private fun updateRules(next: Set<ComponentRule>) {
        require(next.size <= MAX_RULES) { appContext.getString(R.string.repo_too_many_rules) }
        if (next == mutableRules.value) return
        mutableRules.value = next
        prefs.edit().putStringSet(KEY_RULES, next.map(ComponentRule::id).toSet()).apply()
        mutableRevision.value++
    }

    companion object {
        const val REMOTE_PREFS = "rules"
        const val KEY_RULES = "components"
        const val KEY_BLACKLIST = "blacklist"
        const val KEY_DISPLAY_MODE = "display_mode"
        const val KEY_PRIORITIES = "priority_apps"
        const val KEY_DIAGNOSTIC = "diagnostic_mode"
        const val KEY_CONFIG = "config_v1"
        /** Legacy keys retained only so upgrades can detect and erase old local state. */
        const val KEY_TILES = "tile_config"
        const val KEY_DEFAULT_OPEN = "default_open"
        const val KEY_HIDDEN_FROM_APPS = "hidden_from_apps"
        const val KEY_OPEN_TYPES = "open_type_config"
        const val KEY_TEXT_MENU_ORDER = "text_menu_order_v1"
        const val KEY_BROWSER_LINKS = "browser_link_config"
        const val KEY_VISIBILITY_SCOPES = "visibility_scopes"
        val SYNCED_KEYS = setOf(
            KEY_RULES,
            KEY_BLACKLIST,
            KEY_DISPLAY_MODE,
            KEY_PRIORITIES,
            KEY_DIAGNOSTIC,
            KEY_HIDDEN_FROM_APPS,
            KEY_OPEN_TYPES,
            KEY_BROWSER_LINKS,
            KEY_VISIBILITY_SCOPES
        )
        private const val LOCAL_PREFS = "rules_local"
        private const val KEY_INITIALIZED = "configuration_initialized"
        /** Per-space config: JSON Map<Int,UserSpaceRules> (key=0 allowed) + active space id. */
        const val KEY_USER_SPACES = "user_spaces_v1"
        const val KEY_ACTIVE_SPACE = "active_space_v1"
        private const val MAX_SPACES = 16
        private const val MAX_RULES = 20_000
        const val MAX_BACKUP_CHARS = 2_000_000
    }
}
