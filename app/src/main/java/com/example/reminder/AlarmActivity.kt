package com.example.reminder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmActivity : AppCompatActivity() {
    private fun act(action: String, id: Int) {
        sendBroadcast(Intent(this, AlarmReceiver::class.java).setAction(action).putExtra("id", id))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Store.applyNight(this)
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra("id", -1)
        val msg = intent.getStringExtra("msg") ?: ""
        val s = Settings.read(this)
        val time = formatTime(intent.getIntExtra("h", 0), intent.getIntExtra("m", 0), s.tf)
        val color = pal(intent.getIntExtra("color", 0))
        val snooze = Store.getInt(this, "snooze", 10)
        setContent {
            AppTheme(s) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(time, fontSize = 72.sp, fontWeight = FontWeight.Light, color = color)
                        Spacer(Modifier.height(24.dp))
                        Text(msg, fontSize = 28.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(64.dp))
                        FilledTonalButton(
                            onClick = { act(Scheduler.SNOOZE_NOW, id) },
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) { Text(stringResource(R.string.snooze_n, snooze), fontSize = 18.sp) }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { act(Scheduler.DISMISS, id) },
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) { Text(stringResource(R.string.dismiss), fontSize = 18.sp) }
                    }
                }
            }
        }
    }
}
