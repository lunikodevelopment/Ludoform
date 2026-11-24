package dev.lunikodevelopment.ludoform.launch

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

sealed interface LaunchIntentResult {
    data class Ready(val intent: Intent) : LaunchIntentResult

    data class Unavailable(val reason: String) : LaunchIntentResult
}

/** Build only documented contracts; never guess filesystem paths or a Dolphin game ID. */
fun createLaunchIntent(
    context: Context,
    selected: EmulatorResolution.Selected,
    disc: LaunchFile,
    grants: List<LaunchFile>,
): LaunchIntentResult {
    val candidate = selected.candidate
    val template = candidate.definition.template
    if (template.status != TemplateStatus.SOURCE_VERIFIED) {
        return LaunchIntentResult.Unavailable("Launching with ${candidate.definition.name} has not been verified yet.")
    }
    val uri = Uri.parse(disc.uri)
    if (uri.scheme != "content" || grants.none { it.uri == disc.uri }) {
        return LaunchIntentResult.Unavailable("The selected game file is unavailable. Scan the library again.")
    }
    val intent =
        when (template.type) {
            "content_view" -> Intent(template.action).setDataAndType(uri, template.mimeType)
            "package_action_uri" -> Intent(candidate.packageName + template.actionSuffix).setData(uri)
            "extra_string", "retroarch" -> {
                if (template.type == "retroarch" && selected.corePath == null) {
                    return LaunchIntentResult.Unavailable("Choose an installed RetroArch core first.")
                }
                Intent(template.action).apply {
                    template.extras.forEach { (key, value) ->
                        putExtra(key, value.replace("{game}", disc.uri).replace("{core}", selected.corePath.orEmpty()))
                    }
                }
            }
            else -> return LaunchIntentResult.Unavailable("Launching with ${candidate.definition.name} needs additional setup.")
        }
    intent.setPackage(candidate.packageName)
    template.activity?.let { intent.component = ComponentName(candidate.packageName, it) }
    val manager = context.packageManager
    val resolved = manager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))?.activityInfo
    if (resolved == null || !resolved.exported || resolved.packageName != candidate.packageName) {
        return LaunchIntentResult.Unavailable("${candidate.definition.name} cannot open this game on this device.")
    }
    intent.component = ComponentName(resolved.packageName, resolved.name)
    val clip = ClipData.newUri(context.contentResolver, "Game files", uri)
    grants.filter { it.uri != disc.uri }.distinctBy { it.uri }.forEach { file ->
        val additional = Uri.parse(file.uri)
        if (additional.scheme != "content") return LaunchIntentResult.Unavailable("A game file is unavailable. Scan the library again.")
        clip.addItem(ClipData.Item(additional))
    }
    intent.clipData = clip
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return LaunchIntentResult.Ready(intent)
}
