package com.example.sounddeck

import android.content.Context
import org.json.JSONArray

data class Sample(val slot: Int, val name: String = "", val path: String? = null) {
    val configured get() = path != null
}
class SampleStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("samples", Context.MODE_PRIVATE)
    fun load(): List<Sample> {
        val a = JSONArray(prefs.getString("items", "[]"))
        return (0 until 50).map { i ->
            val o = if (i < a.length()) a.optJSONObject(i) else null
            Sample(i, o?.optString("name", "") ?: "", o?.optString("path", null))
        }
    }
    fun save(items: List<Sample>) {
        val a = JSONArray(); items.sortedBy { it.slot }.forEach { s ->
            a.put(org.json.JSONObject().put("name", s.name).put("path", s.path))
        }; prefs.edit().putString("items", a.toString()).apply()
    }
}
