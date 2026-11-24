package dev.lunikodevelopment.ludoform.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Only packages visible through normal or TV launcher queries are considered. */
class InstalledEmulators(private val context: Context) {
    fun packages(): Set<String> {
        val manager = context.packageManager
        return listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER).flatMap { category ->
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            manager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)).map { it.activityInfo.packageName }
        }.toSet()
    }
}
