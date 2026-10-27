package com.winstone.connect.data

import android.content.Context

/**
 * The running app version, remembered once at startup.
 *
 * Every lead and report this phone sends is tagged with it, so the CRM can
 * prove which app version produced which data — and hand that data back if the
 * phone is later moved to a newer version.
 */
object AppVersion {
    @Volatile var name: String? = null
        private set

    @Volatile var code: Int = 0
        private set

    fun init(context: Context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            name = info.versionName
            code = info.longVersionCode.toInt()
        }
    }
}
