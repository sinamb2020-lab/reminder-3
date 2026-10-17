package com.example.reminder

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Store.applyNight(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Scheduler.rescheduleAll(this)
        setContent {
            var s by remember { mutableStateOf(Settings.read(this@MainActivity)) }
            AppTheme(s) { Screen(s) { s = Settings.read(this@MainActivity) } }
        }
    }
}

@Composable
fun Screen(s: Settings, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var list by remember { mutableStateOf(Store.load(ctx)) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(tick) { list = Store.load(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val notifOk = remember(tick) { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val fsOk = remember(tick) {
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }
    val nextText = remember(list, tick) {
        val t = list.filter { it.enabled }.minOfOrNull { Scheduler.nextTrigger(it) }
        if (t == null) ctx.getString(R.string.no_active) else {
            val mins = ((t - System.currentTimeMillis()) / 60000).toInt().coerceAtLeast(0)
            if (mins >= 60) ctx.getString(R.string.next_in_hm, mins / 60, mins % 60)
            else ctx.getString(R.string.next_in_m, mins)
        }
    }
    val msgDeleted = stringResource(R.string.deleted)
    val undo = stringResource(R.string.undo)
    val todayS = stringResource(R.string.today)
    val tomorrowS = stringResource(R.string.tomorrow)

    fun upsert(r: Reminder) {
        list = list.filter { it.id != r.id } + r
        Store.save(ctx, list); Scheduler.schedule(ctx, r)
    }
    fun remove(r: Reminder) {
        list = list.filter { it.id != r.id }
        Store.save(ctx, list); Scheduler.cancelAll(ctx, r.id)
        scope.launch {
            val res = snack.showSnackbar(msgDeleted, actionLabel = undo, duration = SnackbarDuration.Short)
            if (res == SnackbarResult.ActionPerformed) upsert(r)
        }
    }
    fun dayLabel(r: Reminder): String {
        val d = Instant.ofEpochMilli(Scheduler.nextTrigger(r)).atZone(ZoneId.systemDefault()).toLocalDate()
        return when (ChronoUnit.DAYS.between(LocalDate.now(), d)) {
            0L -> todayS
            1L -> tomorrowS
            else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
    }
    fun quick(minutesFromNow: Int?) {
        val cal = Calendar.getInstance()
        if (minutesFromNow == null) { cal.add(Calendar.DAY_OF_YEAR, 1); cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0) }
        else cal.add(Calendar.MINUTE, minutesFromNow)
        editing = Reminder(0, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), "", true)
    }

    BackHandler(showSettings) { showSettings = false }
    if (showSettings) {
        SettingsScreen(onSettings, { list = Store.load(ctx) }) { showSettings = false }
        return
    }

    val shown = list.sortedWith(compareBy<Reminder>({ !it.enabled }, { if (it.enabled) Scheduler.nextTrigger(it) else (it.hour * 60 + it.minute).toLong() }))

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = Reminder(0, 9, 0, "", true) }) {
                Text("+  " + stringResource(R.string.add_reminder), fontSize = 16.sp)
            }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reminders), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                        Text(nextText, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                    }
                    TextButton(onClick = { showSettings = true }) { Text(stringResource(R.string.settings)) }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { quick(10) }, label = { Text(stringResource(R.string.quick_10)) })
                    AssistChip(onClick = { quick(30) }, label = { Text(stringResource(R.string.quick_30)) })
                    AssistChip(onClick = { quick(60) }, label = { Text(stringResource(R.string.quick_1h)) })
                    AssistChip(onClick = { quick(null) }, label = { Text(stringResource(R.string.quick_tomorrow)) })
                }
            }
            if (!notifOk) item {
                Banner(stringResource(R.string.notif_blocked), stringResource(R.string.open_settings)) {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
            }
            if (notifOk && !fsOk) item {
                Banner(stringResource(R.string.fs_hint), stringResource(R.string.allow)) {
                    if (Build.VERSION.SDK_INT >= 34)
                        ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
                }
            }
            if (list.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.empty_title), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.empty_body), color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(shown, key = { it.id }) { r ->
                val col = if (r.enabled) pal(r.color) else MaterialTheme.colorScheme.outlineVariant
                Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().clickable { editing = r }) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(8.dp).fillMaxHeight().background(col))
                        Row(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        formatTime(r.hour, r.minute, s.tf), fontSize = 36.sp, fontWeight = FontWeight.Light,
                                        color = if (r.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                    )
                                    if (r.enabled) Text(
                                        dayLabel(r), fontSize = 13.sp, color = col,
                                        modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
                                    )
                                }
                                Text(r.message, maxLines = 2)
                                Text(
                                    if (!r.enabled) stringResource(R.string.off)
                                    else repeatText(ctx, r.days) + (if (r.nag > 0) " · " + stringResource(R.string.nag_short) else ""),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(checked = r.enabled, onCheckedChange = { upsert(r.copy(enabled = it)) })
                        }
                    }
                }
            }
        }
    }

    editing?.let { r ->
        Editor(
            r, s,
            onSave = { x ->
                upsert(if (x.id == 0) x.copy(id = Store.nextId(ctx)) else x)
                editing = null
            },
            onDelete = if (r.id != 0) ({ remove(r); editing = null }) else null,
            onDuplicate = if (r.id != 0) ({ upsert(r.copy(id = Store.nextId(ctx))); editing = null }) else null,
            onDismiss = { editing = null }
        )
    }
}

@Composable
fun Banner(text: String, button: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label) })
        }
    }
}

@Composable
fun ColorDots(selected: Int, extra: (@Composable () -> Unit)? = null, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Palette.forEachIndexed { i, c ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(c)
                    .then(if (i == selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onPick(i) }
            )
        }
        extra?.invoke()
    }
}

@Composable
fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(
    init: Reminder, s: Settings,
    onSave: (Reminder) -> Unit, onDelete: (() -> Unit)?, onDuplicate: (() -> Unit)?, onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val is24 = when (s.tf) { 1 -> false; 2 -> true; else -> DateFormat.is24HourFormat(ctx) }
    val st = rememberTimePickerState(init.hour, init.minute, is24)
    var msg by remember { mutableStateOf(init.message) }
    var days by remember { mutableIntStateOf(init.days) }
    var color by remember { mutableIntStateOf(init.color) }
    var nag by remember { mutableIntStateOf(init.nag) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sugs = listOf(R.string.sug_medication, R.string.sug_expenses, R.string.sug_meeting, R.string.sug_call).map { stringResource(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(if (init.id == 0) R.string.new_reminder else R.string.edit_reminder),
                fontSize = 20.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(16.dp))
            TimePicker(st)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(msg, { msg = it }, label = { Text(stringResource(R.string.message)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sugs.forEach { t -> SuggestionChip(onClick = { msg = t }, label = { Text(t) }) }
            }

            Column(Modifier.fillMaxWidth()) {
                Section(stringResource(R.string.repeat))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekOrder().forEach { b ->
                        FilterChip(
                            selected = (days and (1 shl b)) != 0,
                            onClick = { days = days xor (1 shl b) },
                            label = { Text(dayName(b)) }
                        )
                    }
                }
                Row {
                    TextButton(onClick = { days = 127 }) { Text(stringResource(R.string.every_day)) }
                    TextButton(onClick = { days = 0 }) { Text(stringResource(R.string.clear)) }
                }
                Text(stringResource(R.string.repeat_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

                Section(stringResource(R.string.color))
                ColorDots(color) { color = it }

                Section(stringResource(R.string.alerts))
                SwitchRow(stringResource(R.string.keep_alerting), nag > 0) { nag = if (it) 2 else 0 }
                if (nag > 0) {
                    val opts = listOf(1, 2, 5)
                    ChipRow(opts.map { stringResource(R.string.every_n_min, it) }, opts.indexOf(nag).coerceAtLeast(0)) { nag = opts[it] }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                if (onDuplicate != null) TextButton(onClick = onDuplicate) { Text(stringResource(R.string.duplicate)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = { onSave(init.copy(hour = st.hour, minute = st.minute, message = msg.trim(), days = days, color = color, nag = nag, enabled = true)) },
                    enabled = msg.isNotBlank()
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
fun SettingsScreen(onChanged: () -> Unit, onData: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var theme by remember { mutableIntStateOf(Store.getInt(ctx, "theme", 0)) }
    var amoled by remember { mutableStateOf(Store.getBool(ctx, "amoled", false)) }
    var accent by remember { mutableIntStateOf(Store.getInt(ctx, "accent", 0)) }
    var lang by remember { mutableIntStateOf(Store.getInt(ctx, "lang", 0)) }
    var tf by remember { mutableIntStateOf(Store.getInt(ctx, "tf", 0)) }
    var sound by remember { mutableStateOf(Store.getBool(ctx, "sound", true)) }
    var vib by remember { mutableStateOf(Store.getBool(ctx, "vib", true)) }
    var snooze by remember { mutableIntStateOf(Store.getInt(ctx, "snooze", 10)) }
    var ringtone by remember { mutableStateOf(Store.getStr(ctx, "ringtone", "")) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val u = res.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            ringtone = u?.toString() ?: "silent"
            Store.setStr(ctx, "ringtone", ringtone)
        }
    }
    val rtName = remember(ringtone) {
        when (ringtone) {
            "" -> ctx.getString(R.string.ringtone_default)
            "silent" -> ctx.getString(R.string.ringtone_silent)
            else -> try { RingtoneManager.getRingtone(ctx, Uri.parse(ringtone)).getTitle(ctx) } catch (e: Exception) { ctx.getString(R.string.ringtone_default) }
        }
    }
    val importOk = stringResource(R.string.import_ok)
    val importFail = stringResource(R.string.import_fail)

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.settings), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)

            Section(stringResource(R.string.appearance))
            Text(stringResource(R.string.theme), fontSize = 14.sp)
            ChipRow(listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).map { stringResource(it) }, theme) {
                theme = it; Store.setInt(ctx, "theme", it); Store.applyNight(ctx); onChanged()
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(stringResource(R.string.amoled), amoled) { amoled = it; Store.setBool(ctx, "amoled", it); onChanged() }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.accent), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            ColorDots(
                accent,
                extra = if (Build.VERSION.SDK_INT >= 31) ({
                    FilterChip(selected = accent >= Palette.size, onClick = { accent = Palette.size; Store.setInt(ctx, "accent", accent); onChanged() },
                        label = { Text(stringResource(R.string.accent_dynamic)) })
                }) else null
            ) { accent = it; Store.setInt(ctx, "accent", it); onChanged() }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.language), fontSize = 14.sp)
            ChipRow(listOf(R.string.lang_system, R.string.lang_en, R.string.lang_fa).map { stringResource(it) }, lang) {
                lang = it; Store.setInt(ctx, "lang", it)
                AppCompatDelegate.setApplicationLocales(
                    when (it) {
                        1 -> LocaleListCompat.forLanguageTags("en")
                        2 -> LocaleListCompat.forLanguageTags("fa")
                        else -> LocaleListCompat.getEmptyLocaleList()
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.time_format), fontSize = 14.sp)
            ChipRow(listOf(R.string.tf_system, R.string.tf_12, R.string.tf_24).map { stringResource(it) }, tf) {
                tf = it; Store.setInt(ctx, "tf", it); onChanged()
            }

            Section(stringResource(R.string.alerts))
            SwitchRow(stringResource(R.string.sound), sound) { sound = it; Store.setBool(ctx, "sound", it) }
            SwitchRow(stringResource(R.string.vibration), vib) { vib = it; Store.setBool(ctx, "vib", it) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ringtone))
                    Text(rtName, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                TextButton(onClick = {
                    picker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    )
                }) { Text(stringResource(R.string.change)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.default_snooze), fontSize = 14.sp)
            val snoozeOpts = listOf(5, 10, 15, 30)
            ChipRow(snoozeOpts.map { stringResource(R.string.min_n, it) }, snoozeOpts.indexOf(snooze).coerceAtLeast(0)) {
                snooze = snoozeOpts[it]; Store.setInt(ctx, "snooze", snooze)
            }

            Section(stringResource(R.string.data))
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Store.export(ctx))
                ctx.startActivity(Intent.createChooser(send, null))
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_data)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() ?: ""
                val n = Store.importJson(ctx, text)
                Toast.makeText(ctx, if (n > 0) String.format(importOk, n) else importFail, Toast.LENGTH_SHORT).show()
                if (n > 0) onData()
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_data)) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val cal = Calendar.getInstance()
                Notifier.show(ctx, Reminder(999999, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), ctx.getString(R.string.test_message), true, 0, Store.getInt(ctx, "accent", 0).coerceAtMost(Palette.size - 1)))
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.test_notification)) }
            Spacer(Modifier.height(32.dp))
        }
    }
}
