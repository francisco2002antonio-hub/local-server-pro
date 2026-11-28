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
 private lateinit var scroll:ScrollView
 private var dark=true
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun fg()=Color.parseColor(if(dark) "#E5EFFF" else "#172B43")
 private fun bg()=Color.parseColor(if(dark) "#101827" else "#F1F5F9")
 private fun card()=GradientDrawable().apply{setColor(Color.parseColor(if(dark) "#22334A" else "#FFFFFF"));cornerRadius=dp(14).toFloat()}
 private fun label(t:String,size:Float=16f)=TextView(this).apply{text=t;textSize=size;setTextColor(fg());setPadding(dp(12),dp(10),dp(12),dp(10))}
 private fun button(t:String, action:()->Unit){panel.addView(Button(this).apply{text=t;isAllCaps=false;setTextColor(Color.WHITE);background=GradientDrawable().apply{setColor(Color.parseColor("#2563EB"));cornerRadius=dp(10).toFloat()};setOnClickListener{action()}},LinearLayout.LayoutParams(-1,dp(38)).apply{bottomMargin=dp(6)})}
 override fun onCreate(b:Bundle?){super.onCreate(b);dark=prefs.getBoolean("dark",true);render();if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"),2)}
 override fun onResume(){super.onResume();if(::status.isInitialized)render()}
 private var page="Inicio"
 private fun go(t:String){page=t;render()}
 private fun tile(t:String, glyph:String, action:()->Unit)=TextView(this).apply{text="$glyph\n$t";textSize=13f;gravity=17;setTextColor(fg());background=card();setOnClickListener{action()}}
 private fun pair(a:View,b:View){panel.addView(LinearLayout(this).apply{orientation=0;addView(a,LinearLayout.LayoutParams(0,dp(72),1f).apply{rightMargin=dp(5)});addView(b,LinearLayout.LayoutParams(0,dp(72),1f).apply{leftMargin=dp(5)})},LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)})}
 private fun stop(){stopService(Intent(this,ServerService::class.java));Handler(mainLooper).postDelayed({render()},400)}
 private fun render(){
 val outer=LinearLayout(this).apply{orientation=1;setBackgroundColor(bg());setOnApplyWindowInsetsListener{v,i->val bars=if(Build.VERSION.SDK_INT>=30)i.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()) else null;v.setPadding(bars?.left?:i.systemWindowInsetLeft,bars?.top?:i.systemWindowInsetTop,bars?.right?:i.systemWindowInsetRight,bars?.bottom?:i.systemWindowInsetBottom);i}}
 panel=LinearLayout(this).apply{orientation=1;setPadding(dp(16),dp(8),dp(16),dp(12))};scroll=ScrollView(this).apply{addView(panel)};outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));val nav=LinearLayout(this).apply{orientation=0};for(t in listOf("Inicio","Archivos","Conexiones","Ajustes")){nav.addView(TextView(this).apply{text=t;textSize=12f;setTypeface(null,android.graphics.Typeface.BOLD);gravity=17;setTextColor(if(page==t)Color.parseColor("#60A5FA") else fg());setOnClickListener{go(t)}},LinearLayout.LayoutParams(0,dp(46),1f))};outer.addView(nav);setContentView(outer);outer.requestApplyInsets()
 panel.addView(label(if(page=="Inicio")"Local Server Pro" else page,22f));status=label("",13f).apply{background=card()}
 when(page){
 "Inicio"->{panel.addView(label("3.0.0-alpha4",12f));panel.addView(status,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)});pair(tile("Almacenamiento","▣"){go("Almacenamiento")},tile("Mis archivos","▤"){go("Archivos")});pair(tile("Dispositivos","▯"){go("Conexiones")},tile("Acceso remoto","◎"){go("Conexiones")});pair(tile("Seguridad","◇"){go("Seguridad")},tile("Actualizaciones","↻"){startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/francisco2002antonio-hub/local-server-pro/actions")))});button("Código QR local"){qr()};button("Copiar enlace"){url()?.let{getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Servidor",it));toast("Enlace copiado")}?:toast("Sin dirección disponible")};button(if(ServerService.running)"Detener servidor" else "Iniciar servidor"){if(ServerService.running)stop() else if(!prefs.contains("folder")&&!prefs.contains("path"))toast("Selecciona una carpeta") else if(prefs.getBoolean("auth",true)&&prefs.getString("password","")!!.length<8)go("Seguridad") else if(ServerService.address(this)==null)toast("Conecta el teléfono a una red") else {startForegroundService(Intent(this,ServerService::class.java));Handler(mainLooper).postDelayed({render()},800)}};if(ServerService.running)(panel.getChildAt(panel.childCount-1) as Button).background=GradientDrawable().apply{setColor(Color.parseColor("#991B1B"));cornerRadius=dp(10).toFloat()}}
 "Almacenamiento"->{panel.addView(label(prefs.getString("path",null)?:prefs.getString("folder","Sin seleccionar")!!));button("Seleccionar carpeta"){stopService(Intent(this,ServerService::class.java));startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(3 or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),1)};button("Explorar almacenamiento interno"){if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager())startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:$packageName"))) else if(Build.VERSION.SDK_INT>=30)browse(Environment.getExternalStorageDirectory()) else toast("Utiliza Seleccionar carpeta")}}
 "Seguridad"->{panel.addView(Switch(this).apply{text="Proteger con contraseña";setTextColor(fg());isChecked=prefs.getBoolean("auth",true);setOnCheckedChangeListener{_,checked->if(checked){prefs.edit().putBoolean("auth",true).apply();stop()}else AlertDialog.Builder(this@MainActivity).setTitle("Desactivar contraseña").setMessage("Cualquier dispositivo de la red local podrá leer los archivos compartidos. Solo usa una red de confianza.").setPositiveButton("Desactivar"){_,_->prefs.edit().putBoolean("auth",false).apply();stop()}.setNegativeButton("Cancelar"){_,_->render()}.setOnCancelListener{render()}.show()}});button("Cambiar contraseña"){edit("Contraseña (mínimo 8 caracteres)","password",true)};panel.addView(label("HTTP local sin cifrado. Los cambios detienen el servidor; vuelve a iniciarlo."))}
 "Archivos"->{panel.addView(label("Explorador web · Gestión de archivos"));button("Abrir archivos"){if(ServerService.running)url()?.let{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(it)))} else toast("Inicia el servidor")};button("Copiar dirección"){url()?.let{getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Servidor",it));toast("Dirección copiada")}}}
 "Conexiones"->{panel.addView(label("Acceso remoto P2P: pendiente de implementación"));button("Código QR local"){qr()}}
 "Ajustes"->{button("Seguridad"){go("Seguridad")};button("Almacenamiento"){go("Almacenamiento")};button("Puerto HTTP"){edit("Puerto (1024–65535)","port",false)};button("Cambiar tema"){dark=!dark;prefs.edit().putBoolean("dark",dark).apply();render()}}
 };refresh()
 }
 private fun qr(){if(!ServerService.running){toast("Inicia el servidor");return};url()?.let{u->val m=QRCodeWriter().encode(u,BarcodeFormat.QR_CODE,512,512);val b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);for(y in 0 until 512)for(x in 0 until 512)b.setPixel(x,y,if(m[x,y])Color.BLACK else Color.WHITE);AlertDialog.Builder(this).setTitle("Escanea para conectar").setView(ImageView(this).apply{setImageBitmap(b);adjustViewBounds=true}).setPositiveButton("Cerrar",null).show()}}
 private fun browse(dir:java.io.File){val dirs=dir.listFiles()?.filter{it.isDirectory&&!it.name.startsWith(".") }?.sortedBy{it.name.lowercase()}?:emptyList();AlertDialog.Builder(this).setTitle(dir.absolutePath).setItems(dirs.map{it.name}.toTypedArray()){_,i->browse(dirs[i])}.setPositiveButton("Compartir esta carpeta"){_,_->if(dir==Environment.getExternalStorageDirectory()){AlertDialog.Builder(this).setTitle("Compartir almacenamiento interno").setMessage("Los dispositivos autenticados podrán leer tus archivos personales accesibles. Usa solo una red de confianza. ¿Continuar?").setPositiveButton("Compartir"){_,_->share(dir)}.setNegativeButton("Cancelar",null).show()}else share(dir)}.setNegativeButton("Cancelar",null).show()}
 private fun share(dir:java.io.File){stopService(Intent(this,ServerService::class.java));prefs.edit().remove("folder").putString("path",dir.absolutePath).apply();refresh()}
 private fun edit(title:String,key:String,secret:Boolean){val e=EditText(this).apply{inputType=if(secret)129 else 2;if(!secret)setText(prefs.getString(key,"8080"))};AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("Guardar"){_,_->val v=e.text.toString();if((secret&&v.length>=8&&v.all{it.code in 33..126})||(!secret&&(v.toIntOrNull() ?: 0) in 1024..65535)){stopService(Intent(this,ServerService::class.java));prefs.edit().putString(key,v).apply();refresh()}else toast("Valor inválido; contraseña ASCII sin espacios")}.setNegativeButton("Cancelar",null).show()}
 private fun url()=ServerService.address(this)?.let{"http://$it:${prefs.getString("port","8080")}"}
 private fun refresh(){val running=ServerService.running;val content="${if(running) "● En funcionamiento" else "● Servidor detenido"}\nDirección local\n${url()?:"Red no disponible"}\nConexión remota P2P\nPendiente de implementación\nContraseña: ${if(prefs.getBoolean("auth",true))"Activada" else "Desactivada"}"
 val styled=android.text.SpannableString(content)
 val end=content.indexOf('\n')
 val flags=android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
 styled.setSpan(android.text.style.ForegroundColorSpan(Color.parseColor(if(running) "#22C55E" else "#EF4444")),0,1,flags)
 styled.setSpan(android.text.style.AbsoluteSizeSpan(20,true),0,1,flags)
 styled.setSpan(android.text.style.AbsoluteSizeSpan(17,true),2,end,flags)
 styled.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),2,end,flags)
 status.text=styled
 }
 private fun toast(t:String)=Toast.makeText(this,t,Toast.LENGTH_SHORT).show()
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==1&&c==RESULT_OK)d?.data?.let{try{contentResolver.takePersistableUriPermission(it,d.flags and 3);prefs.edit().remove("path").putString("folder",it.toString()).apply();refresh()}catch(e:Exception){toast("No se pudo guardar el permiso")}}}
}
