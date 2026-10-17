package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private fun channel(c: Context): String {
        val s = Store.getBool(c, "sound", true)
        val v = Store.getBool(c, "vib", true)
        val rt = Store.getStr(c, "ringtone", "")
        val id = "rem_${if (s) 1 else 0}_${if (v) 1 else 0}_${rt.hashCode()}"
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
            if (!s) {
                ch.setSound(null, null)
            } else {
                val uri: Uri? = when (rt) {
                    "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    "silent" -> null
                    else -> Uri.parse(rt)
                }
                ch.setSound(
                    uri,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
            ch.enableVibration(v)
            nm.createNotificationChannel(ch)
        }
        return id
    }

    fun show(c: Context, r: Reminder) {
        val open = Intent(c, AlarmActivity::class.java)
            .putExtra("id", r.id).putExtra("msg", r.message).putExtra("h", r.hour).putExtra("m", r.minute)
            .putExtra("color", r.color).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val openPi = PendingIntent.getActivity(c, r.id + 100000, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = Store.getInt(c, "snooze", 10)
        val n = NotificationCompat.Builder(c, channel(c))
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(pal(r.color).toArgb())
            .setContentTitle(c.getString(R.string.notif_title))
            .setContentText(r.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.message))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .addAction(0, c.getString(R.string.snooze_n, snooze), Scheduler.actionPi(c, r.id, Scheduler.SNOOZE_NOW))
            .addAction(0, c.getString(R.string.dismiss), Scheduler.actionPi(c, r.id, Scheduler.DISMISS))
            .build()
        val mgr = NotificationManagerCompat.from(c)
        if (mgr.areNotificationsEnabled()) {
            try { mgr.notify(r.id, n) } catch (_: SecurityException) {}
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", -1)
        val nm = NotificationManagerCompat.from(c)
        when (i.action) {
            Scheduler.DISMISS -> { nm.cancel(id); Scheduler.cancelExtras(c, id) }
            Scheduler.SNOOZE_NOW -> {
                nm.cancel(id); Scheduler.cancelExtras(c, id)
                Scheduler.scheduleSnooze(c, id, Store.getInt(c, "snooze", 10))
            }
            Scheduler.FIRE -> {
                val r = Store.load(c).firstOrNull { it.id == id } ?: return
                if (!r.enabled) return
                Notifier.show(c, r)
                Scheduler.scheduleNag(c, r, 0)
                if (r.days == 0) {
                    Store.save(c, Store.load(c).map { if (it.id == id) it.copy(enabled = false) else it })
                } else {
                    Scheduler.schedule(c, r)
                }
            }
            Scheduler.NAG -> {
                val r = Store.load(c).firstOrNull { it.id == id } ?: return
                Notifier.show(c, r)
                Scheduler.scheduleNag(c, r, i.getIntExtra("count", 1))
            }
            Scheduler.SNOOZE_FIRE -> {
                val r = Store.load(c).firstOrNull { it.id == id } ?: return
                Notifier.show(c, r)
                Scheduler.scheduleNag(c, r, 0)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Scheduler.rescheduleAll(c) }
}
