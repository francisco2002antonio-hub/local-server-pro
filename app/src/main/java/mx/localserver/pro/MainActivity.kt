package mx.localserver.pro
import android.app.*
import android.os.*
import android.content.*
import android.net.Uri
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.widget.*
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
class MainActivity: Activity() {
 private val prefs get()=getSharedPreferences("server",0)
 private lateinit var panel:LinearLayout
 private lateinit var status:TextView
 private var dark=true
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun fg()=Color.parseColor(if(dark) "#E5EFFF" else "#172B43")
 private fun bg()=Color.parseColor(if(dark) "#101827" else "#F1F5F9")
 private fun card()=GradientDrawable().apply{setColor(Color.parseColor(if(dark) "#22334A" else "#FFFFFF"));cornerRadius=dp(14).toFloat()}
 private fun label(t:String,size:Float=16f)=TextView(this).apply{text=t;textSize=size;setTextColor(fg());setPadding(dp(12),dp(10),dp(12),dp(10))}
 private fun button(t:String, action:()->Unit){panel.addView(Button(this).apply{text=t;isAllCaps=false;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.parseColor("#2563EB"));cornerRadius=dp(10).toFloat()};setOnClickListener{action()}},LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(9)})}
 override fun onCreate(b:Bundle?){super.onCreate(b);dark=prefs.getBoolean("dark",true);render();if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),2)}
 override fun onResume(){super.onResume();if(::status.isInitialized)refresh()}
 private fun render(){panel=LinearLayout(this).apply{orientation=1;setPadding(dp(20),dp(36),dp(20),dp(28));setBackgroundColor(bg())};setContentView(ScrollView(this).apply{addView(panel);setBackgroundColor(bg())});panel.addView(label("Local Server Pro",28f));panel.addView(label("Panel de control · 2.0.0"));status=label("",18f).apply{background=card()};panel.addView(status);button("Iniciar servidor"){if(!prefs.contains("folder")&&!prefs.contains("path"))toast("Selecciona una carpeta") else if(prefs.getString("password","")!!.length<8)toast("Configura una contraseña de 8 caracteres o más") else if(ServerService.address(this)==null)toast("Conecta el teléfono a Wi-Fi") else {startForegroundService(Intent(this,ServerService::class.java));Handler(mainLooper).postDelayed({refresh()},800)}};button("Detener servidor"){stopService(Intent(this,ServerService::class.java));Handler(mainLooper).postDelayed({refresh()},500)};button("Copiar dirección"){url()?.let{getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Servidor",it));toast("Dirección copiada")}};button("Código QR"){url()?.let{u->val m=QRCodeWriter().encode(u,BarcodeFormat.QR_CODE,512,512);val b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);for(y in 0 until 512)for(x in 0 until 512)b.setPixel(x,y,if(m[x,y]) Color.BLACK else Color.WHITE);AlertDialog.Builder(this).setTitle("Escanea para conectar").setView(ImageView(this).apply{setImageBitmap(b);adjustViewBounds=true}).setPositiveButton("Cerrar",null).show()}};panel.addView(label("Almacenamiento y seguridad",20f));button("Seleccionar carpeta"){stopService(Intent(this,ServerService::class.java));startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(3 or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),1)};button("Explorar almacenamiento interno"){if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager()){startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName")))}else if(Build.VERSION.SDK_INT>=30)browse(Environment.getExternalStorageDirectory()) else toast("Utiliza Seleccionar carpeta")};button("Contraseña de acceso"){edit("Contraseña (mínimo 8 caracteres)","password",true)};button("Puerto HTTP"){edit("Puerto (1024–65535)","port",false)};button("Cambiar tema"){dark=!dark;prefs.edit().putBoolean("dark",dark).apply();render()};button("Actualizaciones en GitHub"){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/francisco2002antonio-hub/local-server-pro/actions")))};refresh()}
 private fun browse(dir:java.io.File){val dirs=dir.listFiles()?.filter{it.isDirectory&&!it.name.startsWith(".") }?.sortedBy{it.name.lowercase()}?:emptyList();AlertDialog.Builder(this).setTitle(dir.absolutePath).setItems(dirs.map{it.name}.toTypedArray()){_,i->browse(dirs[i])}.setPositiveButton("Compartir esta carpeta"){_,_->if(dir==Environment.getExternalStorageDirectory())toast("Selecciona una subcarpeta por seguridad") else {stopService(Intent(this,ServerService::class.java));prefs.edit().remove("folder").putString("path",dir.absolutePath).apply();refresh()}}.setNegativeButton("Cancelar",null).show()}
 private fun edit(title:String,key:String,secret:Boolean){val e=EditText(this).apply{inputType=if(secret)129 else 2;if(!secret)setText(prefs.getString(key,"8080"))};AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("Guardar"){_,_->val v=e.text.toString();if((secret&&v.length>=8&&v.all{it.code in 33..126})||(!secret&&(v.toIntOrNull() ?: 0) in 1024..65535)){stopService(Intent(this,ServerService::class.java));prefs.edit().putString(key,v).apply();refresh()}else toast("Valor inválido; contraseña ASCII sin espacios")}.setNegativeButton("Cancelar",null).show()}
 private fun url()=ServerService.address(this)?.let{"http://$it:${prefs.getString("port","8080")}"}
 private fun refresh(){status.text="${if(ServerService.running) "● Servidor activo" else "○ Servidor detenido"}\n${url()?:"Wi-Fi no disponible"}\nCarpeta: ${prefs.getString("path",null)?:prefs.getString("folder","Sin seleccionar")}\nHTTP sin cifrado: solo red de confianza"}
 private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==1&&c==RESULT_OK)d?.data?.let{try{contentResolver.takePersistableUriPermission(it,d.flags and 3);prefs.edit().remove("path").putString("folder",it.toString()).apply();refresh()}catch(e:Exception){toast("No se pudo guardar el permiso")}}}
}
