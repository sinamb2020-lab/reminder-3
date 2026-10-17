package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object Scheduler {
    const val FIRE = "com.example.reminder.FIRE"
    const val NAG = "com.example.reminder.NAG"
    const val SNOOZE_FIRE = "com.example.reminder.SNOOZE_FIRE"
    const val SNOOZE_NOW = "com.example.reminder.SNOOZE_NOW"
    const val DISMISS = "com.example.reminder.DISMISS"

    private fun pi(c: Context, code: Int, action: String, id: Int, count: Int = 0): PendingIntent =
        PendingIntent.getBroadcast(
            c, code,
            Intent(c, AlarmReceiver::class.java).setAction(action).putExtra("id", id).putExtra("count", count),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun actionPi(c: Context, id: Int, action: String): PendingIntent = pi(c, id + (if (action == DISMISS) 200000 else 500000), action, id)

    fun nextTrigger(r: Reminder, from: Long = System.currentTimeMillis()): Long {
        for (d in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, d)
                set(Calendar.HOUR_OF_DAY, r.hour); set(Calendar.MINUTE, r.minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val dayOk = r.days == 0 || (r.days and (1 shl (cal.get(Calendar.DAY_OF_WEEK) - 1))) != 0
            if (cal.timeInMillis > from && dayOk) return cal.timeInMillis
        }
        return from + 86400000L
    }

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun showPi(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** setAlarmClock is exact, Doze-exempt, and needs no special alarm permission. */
    fun schedule(c: Context, r: Reminder) {
        am(c).cancel(pi(c, r.id, FIRE, r.id))
        if (!r.enabled) { cancelExtras(c, r.id); return }
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(nextTrigger(r), showPi(c)), pi(c, r.id, FIRE, r.id))
    }

    fun scheduleNag(c: Context, r: Reminder, count: Int) {
        if (r.nag <= 0 || count >= 5) return
        val t = System.currentTimeMillis() + r.nag * 60000L
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(t, showPi(c)), pi(c, r.id + 300000, NAG, r.id, count + 1))
    }

    fun scheduleSnooze(c: Context, id: Int, minutes: Int) {
        val t = System.currentTimeMillis() + minutes * 60000L
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(t, showPi(c)), pi(c, id + 400000, SNOOZE_FIRE, id))
    }

    fun cancelExtras(c: Context, id: Int) {
        am(c).cancel(pi(c, id + 300000, NAG, id))
        am(c).cancel(pi(c, id + 400000, SNOOZE_FIRE, id))
    }

    fun cancelAll(c: Context, id: Int) {
        am(c).cancel(pi(c, id, FIRE, id))
        cancelExtras(c, id)
    }

    fun rescheduleAll(c: Context) = Store.load(c).forEach { schedule(c, it) }
}
