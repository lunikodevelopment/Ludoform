package dev.lunikodevelopment.ludoform.launch

/** What Ludoform can honestly say about launching with an emulator before it tries. */
enum class LaunchReadiness { READY, NEEDS_CORE, NOT_VERIFIED }

private val supportedTypes = setOf("content_view", "package_action_uri", "extra_string")

fun EmulatorDefinition.readiness(): LaunchReadiness =
    when {
        template.status != TemplateStatus.SOURCE_VERIFIED -> LaunchReadiness.NOT_VERIFIED
        template.type == "retroarch" -> LaunchReadiness.NEEDS_CORE
        template.type in supportedTypes -> LaunchReadiness.READY
        else -> LaunchReadiness.NOT_VERIFIED
    }

/** How the game is handed to the emulator, for the Emulator sheet's details. */
fun EmulatorDefinition.launchMethod(): String =
    if (template.status != TemplateStatus.SOURCE_VERIFIED) {
        "Not verified yet"
    } else {
        when (template.type) {
            "content_view" -> "Opens the game file"
            "package_action_uri" -> "Launch intent · game file"
            "extra_string" -> "Launch intent · game path"
            "retroarch" -> "RetroArch · core and game"
            "dolphin_game_id" -> "Needs Dolphin's game ID"
            else -> "Not verified yet"
        }
    }
