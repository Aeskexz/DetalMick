package com.example.detalmick

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.detalmick.data.Cita
import com.example.detalmick.data.FirebaseProvider
import com.example.detalmick.domain.CitaValidation
import com.google.firebase.database.*
import org.json.JSONArray
import org.json.JSONObject

/** Local reminders. Persist only opaque IDs and trigger times, never clinical text. */
object AppointmentReminders {
    private const val CHANNEL = "appointment-reminders"
    private var subscription: Pair<DatabaseReference, ValueEventListener>? = null
    fun start(context: Context) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 26) app.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL,"Recordatorios de citas",NotificationManager.IMPORTANCE_DEFAULT))
        FirebaseProvider.auth.addAuthStateListener { auth ->
            subscription?.let { it.first.removeEventListener(it.second) }; subscription = null
            val previousOwner=app.getSharedPreferences("reminders",0).getString("owner",null)
            if(auth.currentUser==null || previousOwner != auth.currentUser?.uid) clear(app)
            app.getSharedPreferences("reminders",0).edit().putString("owner",auth.currentUser?.uid).apply()
            auth.currentUser?.uid?.let { uid ->
                val ref = FirebaseProvider.database.getReference("citas/$uid")
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (FirebaseProvider.auth.currentUser?.uid != uid) return
                        val appointments = snapshot.children.mapNotNull { runCatching { it.getValue(Cita::class.java) }.getOrNull() }
                        val events = JSONArray()
                        appointments.filter(CitaValidation::esActiva).forEach { c ->
                            val start = CitaValidation.inicioMillis(c.fecha,c.horario) ?: return@forEach
                            listOf(24L,1L).forEach { hours ->
                                val at = start - hours * 3600000
                                if (at > System.currentTimeMillis()) events.put(JSONObject().put("uid",uid).put("id",c.id).put("at",at).put("start",start))
                            }
                        }
                        clear(app)
                        app.getSharedPreferences("reminders",0).edit().putString("events",events.toString()).apply()
                        restore(app)
                    }
                    override fun onCancelled(error: DatabaseError) { clear(app) }
                }
                subscription = ref to listener; ref.addValueEventListener(listener)
            }
        }
    }
    private fun events(context: Context) = runCatching { JSONArray(context.getSharedPreferences("reminders",0).getString("events","[]")) }.getOrDefault(JSONArray())
    private fun pending(context: Context, event: JSONObject): PendingIntent {
        val key = "${event.getString("uid")}/${event.getString("id")}/${event.getLong("at")}"
        return PendingIntent.getBroadcast(context,0,Intent(context,ReminderReceiver::class.java).apply {
            data = Uri.parse("detalmick://reminder/$key")
            putExtra("uid",event.getString("uid")); putExtra("id",event.getString("id")); putExtra("start",event.getLong("start"))
        },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun clear(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val entries = events(context)
        for (i in 0 until entries.length()) alarms.cancel(pending(context,entries.getJSONObject(i)))
        context.getSharedPreferences("reminders",0).edit().remove("events").apply()
        NotificationManagerCompat.from(context).cancelAll()
    }
    fun restore(context: Context) {
        val entries = events(context)
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            if (e.getLong("at") > System.currentTimeMillis() && e.getString("uid") == FirebaseProvider.auth.currentUser?.uid)
                context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,e.getLong("at"),pending(context,e))
        }
    }
    fun notify(context: Context, id: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val content = PendingIntent.getActivity(context,0,Intent(context,CitasActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle("Tienes una cita próxima").setContentText("Abre Mis citas para confirmar, reprogramar o cancelar.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(content).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(id.hashCode(),notice)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { AppointmentReminders.restore(context); return }
        val uid = intent.getStringExtra("uid") ?: return
        val id = intent.getStringExtra("id") ?: return
        if (FirebaseProvider.auth.currentUser?.uid != uid) return
        // Suppress a canceled/rescheduled reminder using the latest snapshot received by the app.
        val entries = JSONArray(context.getSharedPreferences("reminders",0).getString("events","[]"))
        if ((0 until entries.length()).any { entries.getJSONObject(it).let { e -> e.getString("uid") == uid && e.getString("id") == id && e.getLong("start") == intent.getLongExtra("start",0) } })
            AppointmentReminders.notify(context,id)
    }
}
