package mx.localserver.pro
import android.app.*
import android.os.*
import android.content.*
import android.net.Uri
import android.widget.*
import java.net.NetworkInterface
class MainActivity: Activity() {
 private lateinit var status: TextView
 private val prefs get() = getSharedPreferences("server",0)
 override fun onCreate(b: Bundle?) { super.onCreate(b)
 val layout=LinearLayout(this).apply { orientation=1; setPadding(32,64,32,32) }
 status=TextView(this).apply { textSize=20f }; layout.addView(status)
 fun button(label:String, action:()->Unit){ layout.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
 button("Seleccionar carpeta") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),1) }
 button("Iniciar servidor") { if(prefs.getString("folder",null)==null) Toast.makeText(this,"Selecciona una carpeta",0).show() else startForegroundService(Intent(this,ServerService::class.java)) }
 button("Detener servidor") { stopService(Intent(this,ServerService::class.java)) }
 button("Actualizar estado") { refresh() }; setContentView(layout); refresh()
 if(Build.VERSION.SDK_INT>=33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),2)
 }
 override fun onResume(){super.onResume(); if(::status.isInitialized) refresh()}
 private fun refresh(){ val ips=NetworkInterface.getNetworkInterfaces().toList().flatMap{it.inetAddresses.toList()}.filter{!it.isLoopbackAddress && it is java.net.Inet4Address}.joinToString("\n"){"http://${it.hostAddress}:8080"}; status.text="Local Server Pro\n${if(ServerService.running) "Activo" else "Detenido"}\n$ips\nCarpeta: ${if(prefs.contains("folder")) "seleccionada" else "sin seleccionar"}\nSolo redes de confianza. Acceso sin contraseña." }
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d); if(r==1 && c==RESULT_OK) d?.data?.let{ contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION); prefs.edit().putString("folder",it.toString()).apply(); refresh() }}
}
