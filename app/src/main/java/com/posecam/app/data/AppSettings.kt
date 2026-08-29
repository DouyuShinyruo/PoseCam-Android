package com.posecam.app.data

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("posecam_settings", Context.MODE_PRIVATE)

    var defaultAlpha: Float
        get() = sp.getFloat(KEY_ALPHA, 0.7f)
        set(value) = sp.edit().putFloat(KEY_ALPHA, value).apply()

    var compositeOnCapture: Boolean
        get() = sp.getBoolean(KEY_COMPOSITE, true)
        set(value) = sp.edit().putBoolean(KEY_COMPOSITE, value).apply()

    /** 上次使用的参考图："res:insp_street_walk" 或 "file:/path/to.jpg" */
    var lastReference: String?
        get() = sp.getString(KEY_LAST_REF, null)
        set(value) = sp.edit().putString(KEY_LAST_REF, value).apply()

    var guideShown: Boolean
        get() = sp.getBoolean(KEY_GUIDE, false)
        set(value) = sp.edit().putBoolean(KEY_GUIDE, value).apply()

    /** 收藏的素材文件路径集合 */
    var favorites: Set<String>
        get() = sp.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_FAVORITES, value).apply()

    /** 最近使用的参考图 key（file:/res:，最多5张，新→旧） */
    var recentRefs: List<String>
        get() = sp.getString(KEY_RECENT, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = sp.edit().putString(KEY_RECENT, value.joinToString("\n")).apply()

    private companion object {
        const val KEY_ALPHA = "default_alpha"
        const val KEY_COMPOSITE = "composite_on_capture"
        const val KEY_LAST_REF = "last_reference"
        const val KEY_GUIDE = "guide_shown"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT = "recent_refs"
    }
}
