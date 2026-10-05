package net.plainnotes.app

import android.Manifest
import android.app.AlarmManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dagger.hilt.android.AndroidEntryPoint
import net.plainnotes.app.data.*
import net.plainnotes.app.domain.*
import net.plainnotes.app.ui.NotesTheme
import java.time.*
import java.time.format.DateTimeFormatter

@AndroidEntryPoint class MainActivity:ComponentActivity() {
    private val model:NotesViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { NotesTheme { NotesApp(model) } };intent.getStringExtra("reminder_id")?.let{model.notification(it)}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);intent.getStringExtra("reminder_id")?.let{model.notification(it)}}
    override fun onResume(){super.onResume();model.sync()}
}
private fun localString(instant:Instant)=TimestampInput.format(instant,ZoneId.systemDefault())
private fun parseInstant(value:String)=TimestampInput.parse(value,ZoneId.systemDefault())
/** Display-only date in the current UI language; input fields keep the ISO format. */
@Composable private fun localDate(date:LocalDate):String=date.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]))
private fun label(state:SlotState)=when(state){SlotState.PENDING->R.string.status_pending;SlotState.SOON->R.string.status_soon;SlotState.OVERDUE->R.string.status_overdue;SlotState.ON_TIME->R.string.status_on_time;SlotState.LATE->R.string.status_late;SlotState.MISSED->R.string.status_missed;SlotState.SKIPPED->R.string.status_skipped}

/** System settings can change without changing database rows. */
@Composable private fun systemSettingsRevision():MutableIntState {
    val revision=remember{mutableIntStateOf(0)}
    val owner=LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_RESUME)revision.intValue++}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer)}
    }
    return revision
}
@Composable private fun NotesApp(model:NotesViewModel) {
    val state by model.state.collectAsStateWithLifecycle();val editor by model.editor.collectAsStateWithLifecycle();val override by model.override.collectAsStateWithLifecycle()
    var page by remember{mutableIntStateOf(0)};var doseSlot by remember{mutableStateOf<Slot?>(null)};var overrideSlot by remember{mutableStateOf<Slot?>(null)}
    var manual by remember{mutableStateOf(false)};var appointment by remember{mutableStateOf(false)};var archive by remember{mutableStateOf<MedicationEntity?>(null)}
    val notificationSlot by model.notificationSlot.collectAsStateWithLifecycle()
    LaunchedEffect(notificationSlot){notificationSlot?.let{doseSlot=it;model.notificationSlot.value=null}}
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineSmall)
        Row { listOf(R.string.calendar,R.string.medications,R.string.reliability).forEachIndexed{i,title->TextButton(onClick={page=i}){Text(stringResource(title))}} }
        state.error?.let { Text(stringResource(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={model.clearError();model.refresh()}){Text(stringResource(R.string.refresh))} }
        if(state.loading)Text(stringResource(R.string.loading))
        when(page) {
            0 -> {
                val ctx=LocalContext.current;val revision=systemSettingsRevision()
                val exact=remember(revision.intValue){Build.VERSION.SDK_INT<31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}
                if(!exact)Text(stringResource(R.string.exact_denied),color=MaterialTheme.colorScheme.error)
                var fromDate by remember{mutableStateOf(LocalDate.now().toString())}
                Field(R.string.calendar_from,fromDate){fromDate=it}
                TextButton(onClick={model.calendarFrom(fromDate)}){Text(stringResource(R.string.refresh))}
                Row{TextButton(onClick={manual=true},enabled=state.medications.isNotEmpty()){Text(stringResource(R.string.manual))};TextButton(onClick={appointment=true}){Text(stringResource(R.string.appointment))}}
                CalendarContent(state,onComplete={doseSlot=it},onOverride={overrideSlot=it;model.loadOverride(it.key)})
            }
            1 -> {
                Button(onClick={model.edit(null)}){Text(stringResource(R.string.add))}
                if(state.medications.isEmpty())Text(stringResource(R.string.no_medications))
                LazyColumn { items(state.medications,key={it.id}) { m ->
                    Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){
                        Text(m.name,style=MaterialTheme.typography.titleMedium);Text(stringResource(R.string.dose_display,m.dose_per_intake.toString(),choiceLabel(m.unit)))
                        Row{TextButton(onClick={model.edit(m)}){Text(stringResource(R.string.edit))};TextButton(onClick={archive=m}){Text(stringResource(R.string.delete))}}
                    }}
                } }
            }
            2 -> ReliabilityContent(onSync={model.sync()},onTest={model.testReminder()})
        }
    }
    editor?.let{MedicationDialog(it,onDismiss={model.closeEditor()},onSave={m,e,k,n,t,d->model.save(m,e,k,n,t,d)})}
    doseSlot?.let{s->IntakeDialog(s.dose,localString(Instant.now()),onDismiss={doseSlot=null}){t,d->model.complete(s,t,d);doseSlot=null}}
    overrideSlot?.let{s->if(override?.key==s.key)OverrideDialog(s,override!!,{overrideSlot=null}){o->model.changeOverride(s,o);overrideSlot=null}}
    if(manual)ManualDialog(state.medications,{manual=false}){id,t,d->model.manual(id,t,d);manual=false}
    if(appointment)AppointmentDialog({appointment=false}){model.appointment(it);appointment=false}
    archive?.let{m->AlertDialog(onDismissRequest={archive=null},text={Text(stringResource(R.string.delete_confirm))},confirmButton={TextButton(onClick={model.delete(m.id);archive=null}){Text(stringResource(R.string.yes))}},dismissButton={TextButton(onClick={archive=null}){Text(stringResource(R.string.cancel))}})}
}

@Composable fun CalendarContent(state:NotesState,onComplete:(Slot)->Unit={},onOverride:(Slot)->Unit={}) {
    val names=state.medications.associateBy{it.id};val today=LocalDate.now()
    if(state.slots.isEmpty()&&state.appointments.isEmpty())Text(stringResource(R.string.empty))
    LazyColumn(Modifier.fillMaxWidth()) {
        items(state.slots,key={it.slot.key}){e->
            val s=e.slot;val date=s.at.atZone(ZoneId.systemDefault()).toLocalDate();val days=java.time.temporal.ChronoUnit.DAYS.between(today,date)
            val heading=when(days){0L->stringResource(R.string.today);1L->stringResource(R.string.tomorrow);else->if(days>1)stringResource(R.string.date_ahead,localDate(date),days.toInt())else localDate(date)}
            Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){
                Text(heading,style=MaterialTheme.typography.labelLarge);Text(names[s.medicationId]?.name ?: "",style=MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.due,s.at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm:ss"))))
                Text(stringResource(R.string.dose_display,s.dose.toString(),names[s.medicationId]?.unit?.let{choiceLabel(it)} ?: ""));Text(stringResource(label(e.state)))
                if(e.state !in listOf(SlotState.ON_TIME,SlotState.LATE,SlotState.SKIPPED))TextButton(onClick={onComplete(s)}){Text(stringResource(R.string.complete))}
                if(e.state !in listOf(SlotState.ON_TIME,SlotState.LATE,SlotState.MISSED))TextButton(onClick={onOverride(s)}){Text(stringResource(R.string.reschedule))}
            }}
        }
        items(state.appointments,key={"appointment:${it.id}"}) { a ->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){Text(stringResource(R.string.appointment));Text(localString(Instant.ofEpochMilli(a.at_utc)));a.location?.let{Text(it)}}} }
    }
}
@Composable private fun Field(label:Int,value:String,onChange:(String)->Unit) { OutlinedTextField(value,onChange,label={Text(stringResource(label))},modifier=Modifier.fillMaxWidth(),singleLine=true) }
@Composable private fun Toggle(label:Int,value:Boolean,onChange:(Boolean)->Unit){Row{Checkbox(value,onChange);Text(stringResource(label),Modifier.padding(top=12.dp))}}
@Composable private fun Choice(label:Int,options:List<String>,selected:String,onChange:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Box{OutlinedButton(onClick={expanded=true}){Text(stringResource(label)+": "+choiceLabel(selected))};DropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{o->DropdownMenuItem(text={Text(choiceLabel(o))},onClick={onChange(o);expanded=false})}}}
}
@Composable private fun choiceLabel(value:String)=stringResource(when(value){"E2"->R.string.choice_e2;"T"->R.string.choice_t;"P4"->R.string.choice_p4;"CPA"->R.string.choice_cpa;"SPI"->R.string.choice_spi;"BICA"->R.string.choice_bica;"FIN"->R.string.choice_fin;"DUT"->R.string.choice_dut;"DHT"->R.string.choice_dht;"CMA"->R.string.choice_cma;"NOMAC"->R.string.choice_nomac;"TRIP"->R.string.choice_trip;"OTHER"->R.string.choice_other;"ORAL"->R.string.choice_oral;"SUBLINGUAL"->R.string.choice_sublingual;"GEL"->R.string.choice_gel;"PATCH"->R.string.choice_patch;"INJECTION"->R.string.choice_injection;"EV"->R.string.choice_ev;"EC"->R.string.choice_ec;"EB"->R.string.choice_eb;"EN"->R.string.choice_en;"EU"->R.string.choice_eu;"MG"->R.string.choice_mg;"TABLET"->R.string.choice_tablet;"ML"->R.string.choice_ml;"PUMP"->R.string.choice_pump;"EVERY_N_DAYS"->R.string.choice_every_n_days;"EVERY_N_HOURS"->R.string.choice_every_n_hours;"WEEKLY"->R.string.choice_weekly;else->R.string.choice_other})

@Composable private fun MedicationDialog(edit:EditMedication,onDismiss:()->Unit,onSave:(MedicationEntity,String?,RuleKind,Int,List<LocalTime>,Set<DayOfWeek>)->Unit) {
    val m=edit.medication
    var name by remember{mutableStateOf(m?.name ?: "")};var molecule by remember{mutableStateOf(m?.molecule ?: "E2")}
    var route by remember{mutableStateOf(m?.route ?: "ORAL")};var ester by remember{mutableStateOf(edit.profile?.ester ?: "E2")}
    var unit by remember{mutableStateOf(m?.unit ?: "MG")};var dose by remember{mutableStateOf(m?.dose_per_intake?.toString() ?: "")}
    var capacity by remember{mutableStateOf(m?.container_capacity?.toString() ?: "")};var expiry by remember{mutableStateOf(m?.expiry_days_after_open?.toString() ?: "")}
    var soon by remember{mutableStateOf(m?.soon_alert_minutes?.toString() ?: "15")};var late by remember{mutableStateOf(m?.late_after_minutes?.toString() ?: "120")}
    var active by remember{mutableStateOf(m?.active ?: true)};var notifications by remember{mutableStateOf(m?.notifications_on ?: true)}
    var kind by remember{mutableStateOf(edit.rule?.kind ?: "EVERY_N_DAYS")};var n by remember{mutableStateOf(edit.rule?.interval?.toString() ?: "1")}
    var times by remember{mutableStateOf(edit.times.joinToString(","){it.local_time})};var days by remember{mutableStateOf(edit.rule?.let{r->(1..7).filter{r.weekday_mask and (1 shl(it-1))!=0}.joinToString(",")} ?: "")}
    var invalid by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.medications))},text={Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState())){
        Field(R.string.name,name){name=it};Choice(R.string.molecule,listOf("E2","T","P4","CPA","SPI","BICA","FIN","DUT","DHT","CMA","NOMAC","TRIP","OTHER"),molecule){molecule=it}
        if(molecule=="E2"){Choice(R.string.route,listOf("ORAL","SUBLINGUAL","GEL","PATCH","INJECTION"),route){route=it};Choice(R.string.ester,listOf("E2","EV","EC","EB","EN","EU"),ester){ester=it};Text(stringResource(R.string.e2_metadata))}
        Choice(R.string.unit,listOf("MG","TABLET","ML","PUMP","PATCH","OTHER"),unit){unit=it}
        Field(R.string.dose,dose){dose=it};Field(R.string.capacity,capacity){capacity=it};Field(R.string.expiry,expiry){expiry=it}
        Field(R.string.soon,soon){soon=it};Field(R.string.late,late){late=it};Toggle(R.string.active,active){active=it};Toggle(R.string.notifications,notifications){notifications=it}
        Choice(R.string.kind,listOf("EVERY_N_DAYS","EVERY_N_HOURS","WEEKLY"),kind){kind=it};Field(R.string.interval,n){n=it}
        if(kind!="EVERY_N_HOURS")Field(R.string.times,times){times=it}
        if(kind=="WEEKLY")Field(R.string.weekdays,days){days=it}
        if(invalid)Text(stringResource(R.string.invalid),color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(onClick={runCatching {
        val d=dose.toDouble();val c=capacity.toDouble();val adv=soon.toInt();val l=late.toInt();val i=n.toInt()
        require(name.isNotBlank()&&d.isFinite()&&d>0&&c.isFinite()&&c>0&&adv>=0&&l>=0&&i in 1..36500)
        val t=if(kind=="EVERY_N_HOURS")emptyList()else times.split(',').map{LocalTime.parse(it.trim()).withNano(0)}
        val w=if(kind=="WEEKLY")days.split(',').map{DayOfWeek.of(it.trim().toInt())}.toSet()else emptySet()
        val ex=expiry.takeIf{it.isNotBlank()}?.toInt();require(ex==null||ex>0)
        onSave(MedicationEntity(m?.id ?: 0,name,molecule,if(molecule=="E2")route else null,unit,d,c,ex,adv,l,false,null,notifications,active,m?.sort_order ?: 0,null),if(molecule=="E2")ester else null,RuleKind.valueOf(kind),i,t,w)
    }.onFailure{invalid=true}}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
@Composable private fun IntakeDialog(initialDose:Double,initialTime:String,onDismiss:()->Unit,onSave:(Instant,Double)->Unit) {
    var time by remember{mutableStateOf(initialTime)};var dose by remember{mutableStateOf(initialDose.toString())};var invalid by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.complete))},text={Column{Field(R.string.time,time){time=it};Field(R.string.actual_dose,dose){dose=it};if(invalid)Text(stringResource(R.string.invalid))}},confirmButton={TextButton(onClick={runCatching{val d=dose.toDouble();require(d.isFinite()&&d>0);onSave(parseInstant(time),d)}.onFailure{invalid=true}}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
@Composable private fun OverrideDialog(slot:Slot,initial:SlotOverride,onDismiss:()->Unit,onSave:(SlotOverride)->Unit) {
    var time by remember{mutableStateOf(initial.rescheduled?.let(::localString) ?: "")};var dose by remember{mutableStateOf(initial.dose?.toString() ?: "")};var skip by remember{mutableStateOf(initial.skipped)};var invalid by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.reschedule))},text={Column{
        Field(R.string.time,time){time=it};TextButton(onClick={time=""}){Text(stringResource(R.string.reset_time))}
        Field(R.string.dose,dose){dose=it};TextButton(onClick={dose=""}){Text(stringResource(R.string.reset_dose))};Toggle(R.string.skipped,skip){skip=it}
        TextButton(onClick={time="";dose="";skip=false}){Text(stringResource(R.string.reset_all))};if(invalid)Text(stringResource(R.string.invalid))
    }},confirmButton={TextButton(onClick={runCatching{
        val t=time.takeIf{it.isNotBlank()}?.let(::parseInstant);onSave(SlotOverride(slot.key,t,if(t==null)null else ZoneId.systemDefault(),dose.takeIf{it.isNotBlank()}?.toDouble(),skip))
    }.onFailure{invalid=true}}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
@Composable private fun ManualDialog(meds:List<MedicationEntity>,onDismiss:()->Unit,onSave:(Long,Instant,Double)->Unit) {
    var id by remember{mutableLongStateOf(meds.first().id)};var show by remember{mutableStateOf(false)}
    if(show)IntakeDialog(meds.first{it.id==id}.dose_per_intake,localString(Instant.now()),onDismiss,onSave={t,d->onSave(id,t,d)})
    else AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.manual))},text={Column{meds.forEach{m->TextButton(onClick={id=m.id;show=true}){Text(m.name)}}}},confirmButton={},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
@Composable private fun AppointmentDialog(onDismiss:()->Unit,onSave:(AppointmentEntity)->Unit) {
    var time by remember{mutableStateOf(localString(Instant.now().plusSeconds(86400)))};var type by remember{mutableStateOf("")};var location by remember{mutableStateOf("")};var practitioner by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var reminder by remember{mutableStateOf("30")};var invalid by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.appointment))},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState())){Field(R.string.time,time){time=it};Field(R.string.appointment_type,type){type=it};Field(R.string.location,location){location=it};Field(R.string.practitioner,practitioner){practitioner=it};Field(R.string.note,note){note=it};Field(R.string.reminder_before,reminder){reminder=it};if(invalid)Text(stringResource(R.string.invalid))}},confirmButton={TextButton(onClick={runCatching{val n=reminder.toInt();require(n>=0);onSave(AppointmentEntity(type=type,at_utc=parseInstant(time).toEpochMilli(),at_zone=ZoneId.systemDefault().id,location=location,practitioner=practitioner,note=note,remind_minutes_before=n))}.onFailure{invalid=true}}){Text(stringResource(R.string.save))}},dismissButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})
}
@Composable private fun ReliabilityContent(onSync:()->Unit,onTest:()->Unit) {
    val context=LocalContext.current;val inspection=LocalInspectionMode.current;val prefs=remember{context.getSharedPreferences("prefs",Context.MODE_PRIVATE)}
    var high by remember{mutableStateOf(prefs.getBoolean("high_reliability",false))}
    val revision=systemSettingsRevision()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){revision.intValue++;onSync()}
    fun open(action:String,packageUri:Boolean=false){runCatching{context.startActivity(Intent(action).apply{if(packageUri)data=Uri.parse("package:${context.packageName}")})}}
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        val exact=remember(revision.intValue){inspection||Build.VERSION.SDK_INT<31||context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()}
        Text(stringResource(if(exact)R.string.exact_allowed else R.string.exact_denied))
        val notifications=remember(revision.intValue){inspection||androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()}
        val battery=remember(revision.intValue){inspection||context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)}
        val channel=remember(revision.intValue){if(inspection)null else context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel("reminders")}
        Text(stringResource(R.string.notification_state,stringResource(if(notifications)R.string.enabled else R.string.disabled)))
        Text(stringResource(R.string.battery_state,stringResource(if(battery)R.string.enabled else R.string.disabled)))
        Text(stringResource(R.string.channel_state,stringResource(if(channel==null||channel.importance!=android.app.NotificationManager.IMPORTANCE_NONE)R.string.enabled else R.string.disabled)))
        if(Build.VERSION.SDK_INT>=31)Button(onClick={open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,true)}){Text(stringResource(R.string.exact_settings))}
        if(Build.VERSION.SDK_INT>=33)Button(onClick={launcher.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text(stringResource(R.string.notification_permission))}
        Button(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}}){Text(stringResource(R.string.notification_settings))}
        Button(onClick={open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)}){Text(stringResource(R.string.battery_settings))}
        Toggle(R.string.high_reliability,high){high=it;prefs.edit().putBoolean("high_reliability",it).apply();onSync()}
        Text(stringResource(R.string.high_privacy));Text(stringResource(R.string.manufacturer,Build.MANUFACTURER));Text(stringResource(R.string.vendor_guidance));Text(stringResource(R.string.reliability_limits))
        Button(onClick=onTest){Text(stringResource(R.string.test_reminder))}
    }
}
@Preview(showBackground=true) @Composable fun CalendarPreview(){NotesTheme{CalendarContent(NotesState(loading=false))}}
@Preview(showBackground=true) @Composable fun MedicationPreview(){NotesTheme{MedicationDialog(EditMedication(null,null,null,emptyList()),{}, {_,_,_,_,_,_->})}}
@Preview(showBackground=true) @Composable fun IntakePreview(){NotesTheme{IntakeDialog(1.0,"2026-10-05T12:00:00",{}, {_,_->})}}
@Preview(showBackground=true) @Composable fun OverridePreview(){NotesTheme{val s=Slot("wall:1@2026-10-05T12:00:00",1,1,Instant.EPOCH,Instant.EPOCH,ZoneId.of("UTC"),1.0,15,120,Instant.EPOCH);OverrideDialog(s,SlotOverride(s.key),{}, {})}}
@Preview(showBackground=true) @Composable fun AppointmentPreview(){NotesTheme{AppointmentDialog({}, {})}}
@Preview(showBackground=true) @Composable fun ReliabilityPreview(){NotesTheme{ReliabilityContent({}, {})}}

@Preview(showBackground=true) @Composable fun ManualIntakePreview(){NotesTheme{ManualDialog(listOf(MedicationEntity(id=1,name="Synthetic note",molecule="OTHER",unit="MG",dose_per_intake=1.0,container_capacity=10.0,soon_alert_minutes=15,late_after_minutes=120,site_rotation=false,notifications_on=false,active=true,sort_order=0)),{}, {_,_,_->})}}
