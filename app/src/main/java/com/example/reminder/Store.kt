package com.example.reminder

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import org.json.JSONArray
import org.json.JSONObject

/** days: bitmask, bit i = Calendar.DAY_OF_WEEK - 1 (Sunday = 0). 0 = once, 127 = every day. */
data class Reminder(
    val id: Int, val hour: Int, val minute: Int, val message: String, val enabled: Boolean,
    val days: Int = 0, val color: Int = 0, val nag: Int = 0
)

/** Local-only persistence (SharedPreferences + JSON). Nothing leaves the device. */
object Store {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private fun toJson(l: List<Reminder>): JSONArray {
        val a = JSONArray()
        l.forEach {
            a.put(
                JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("msg", it.message)
                    .put("on", it.enabled).put("days", it.days).put("color", it.color).put("nag", it.nag)
            )
        }
        return a
    }

    private fun fromJson(a: JSONArray): List<Reminder> = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        Reminder(
            o.optInt("id", 0), o.getInt("h"), o.getInt("m"), o.getString("msg"), o.optBoolean("on", true),
            o.optInt("days", 0), o.optInt("color", 0), o.optInt("nag", 0)
        )
    }

    fun load(c: Context): List<Reminder> =
        try { fromJson(JSONArray(p(c).getString("list", "[]") ?: "[]")) } catch (e: Exception) { emptyList() }

    fun save(c: Context, l: List<Reminder>) { p(c).edit().putString("list", toJson(l).toString()).apply() }

    fun export(c: Context): String = toJson(load(c)).toString(2)

    fun importJson(c: Context, text: String): Int {
        val items = try { fromJson(JSONArray(text.trim())) } catch (e: Exception) { return 0 }
        val ok = items.filter { it.hour in 0..23 && it.minute in 0..59 && it.message.isNotBlank() }
        if (ok.isEmpty()) return 0
        val added = ok.map { it.copy(id = nextId(c)) }
        save(c, load(c) + added)
        added.forEach { Scheduler.schedule(c, it) }
        return added.size
    }

    fun nextId(c: Context): Int {
        val n = p(c).getInt("nextId", 1)
        p(c).edit().putInt("nextId", n + 1).apply()
        return n
    }

    fun getInt(c: Context, k: String, d: Int) = p(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) = p(c).edit().putInt(k, v).apply()
    fun getBool(c: Context, k: String, d: Boolean) = p(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
    fun getStr(c: Context, k: String, d: String) = p(c).getString(k, d) ?: d
    fun setStr(c: Context, k: String, v: String) = p(c).edit().putString(k, v).apply()

    fun applyNight(c: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (getInt(c, "theme", 0)) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
