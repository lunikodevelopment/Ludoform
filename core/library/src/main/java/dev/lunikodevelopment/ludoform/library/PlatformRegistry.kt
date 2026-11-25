package dev.lunikodevelopment.ludoform.library

import android.content.Context
import org.json.JSONArray
import java.util.Locale

data class PlatformDefinition(val id: String, val name: String, val aliases: Set<String>, val extensions: Set<String>)

class PlatformRegistry(val platforms: List<PlatformDefinition>) {
    constructor(context: Context) : this(parse(context.assets.open("platforms.json").bufferedReader().use { it.readText() }))

    fun findFolder(name: String): PlatformDefinition? {
        val normalized = name.trim().lowercase(Locale.ROOT)
        return platforms.singleOrNull { normalized == it.id || normalized in it.aliases }
    }

    fun byId(id: String): PlatformDefinition? = platforms.firstOrNull { it.id == id }

    companion object {
        fun parse(text: String): List<PlatformDefinition> =
            JSONArray(text).let { json ->
                (0 until json.length()).map { index ->
                    val item = json.getJSONObject(index)

                    fun strings(name: String): Set<String> =
                        item.getJSONArray(name).let { arr ->
                            (0 until arr.length()).map { arr.getString(it).lowercase(Locale.ROOT) }.toSet()
                        }
                    PlatformDefinition(item.getString("id"), item.getString("name"), strings("aliases"), strings("extensions"))
                }
            }
    }
}
