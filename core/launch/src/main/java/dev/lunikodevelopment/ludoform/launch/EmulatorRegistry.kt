package dev.lunikodevelopment.ludoform.launch

import android.content.Context
import org.json.JSONObject
import java.util.Locale

enum class EmulatorKind { STANDALONE, RETROARCH }

enum class TemplateStatus { SOURCE_VERIFIED, NEEDS_VERIFICATION }

data class LaunchTemplate(
    val type: String,
    val action: String?,
    val activity: String?,
    val actionSuffix: String?,
    val mimeType: String?,
    val extras: Map<String, String>,
    val status: TemplateStatus,
)

data class EmulatorDefinition(
    val id: String,
    val name: String,
    val kind: EmulatorKind,
    val packageFamilies: List<String>,
    val platforms: Set<String>,
    val template: LaunchTemplate,
) {
    fun matchesPackage(packageName: String): Boolean =
        packageFamilies.any { family ->
            packageName == family || packageName.startsWith("$family.")
        }
}

data class CoreDefinition(val id: String, val name: String, val library: String, val platforms: Set<String>)

data class EmulatorRegistry(val emulators: List<EmulatorDefinition>, val cores: List<CoreDefinition>) {
    companion object {
        fun load(context: Context): EmulatorRegistry = parse(context.assets.open("emulators.json").bufferedReader().use { it.readText() })

        fun parse(text: String): EmulatorRegistry {
            val root = JSONObject(text)
            require(root.getInt("version") == 1) { "Unsupported emulator registry version" }

            fun org.json.JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

            fun JSONObject.optionalString(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
            val items = root.getJSONArray("emulators")
            val emulators =
                (0 until items.length()).map { index ->
                    val item = items.getJSONObject(index)
                    val template = item.getJSONObject("template")
                    val extrasJson = template.optJSONObject("extras")
                    val extras = extrasJson?.keys()?.asSequence()?.associateWith { extrasJson.getString(it) } ?: emptyMap()
                    EmulatorDefinition(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        kind = EmulatorKind.valueOf(item.getString("kind").uppercase(Locale.ROOT)),
                        packageFamilies = item.getJSONArray("packages").strings(),
                        platforms = item.getJSONArray("platforms").strings().toSet(),
                        template =
                            LaunchTemplate(
                                type = template.getString("type"),
                                action = template.optionalString("action"),
                                activity = template.optionalString("activity"),
                                actionSuffix = template.optionalString("actionSuffix"),
                                mimeType = template.optionalString("mimeType"),
                                extras = extras,
                                status = TemplateStatus.valueOf(template.getString("status").uppercase(Locale.ROOT)),
                            ),
                    )
                }
            val coreItems = root.getJSONArray("cores")
            val cores =
                (0 until coreItems.length()).map { index ->
                    val item = coreItems.getJSONObject(index)
                    CoreDefinition(
                        item.getString("id"),
                        item.getString("name"),
                        item.getString("library"),
                        item.getJSONArray("platforms").strings().toSet(),
                    )
                }
            require(emulators.map { it.id }.distinct().size == emulators.size) { "Duplicate emulator id" }
            require(cores.map { it.id }.distinct().size == cores.size) { "Duplicate core id" }
            require(emulators.all { it.packageFamilies.isNotEmpty() && it.platforms.isNotEmpty() }) { "Invalid emulator entry" }
            require(cores.all { it.library.endsWith(".so") && '/' !in it.library && it.platforms.isNotEmpty() }) { "Invalid core entry" }
            return EmulatorRegistry(emulators, cores)
        }
    }
}
