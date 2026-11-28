package mx.localserver.pro
import android.app.*
import android.content.*
import android.net.*
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.URLEncoder
import java.io.File
import android.util.Base64
class ServerService:Service(){
 companion object { @Volatile var running=false
 fun address(c:Context):String? {val cm=c.getSystemService(ConnectivityManager::class.java);return cm.allNetworks.firstNotNullOfOrNull{n->if(cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true)cm.getLinkProperties(n)?.linkAddresses?.firstOrNull{it.address is Inet4Address}?.address?.hostAddress else null}} }
 private var server:NanoHTTPD?=null
 override fun onBind(i:Intent?):IBinder?=null
 private fun enc(s:String)=URLEncoder.encode(s,"UTF-8").replace("+","%20")
 private fun esc(s:String)=android.text.TextUtils.htmlEncode(s)
 override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int{
 val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("server","Servidor local",NotificationManager.IMPORTANCE_LOW));startForeground(1,Notification.Builder(this,"server").setContentTitle("Local Server Pro").setContentText("Servidor local activo").setSmallIcon(R.drawable.ic_server).build());if(server!=null)return START_NOT_STICKY
 val p=getSharedPreferences("server",0);val ip=address(this);val auth=p.getBoolean("auth",true);val password=p.getString("password","")!!;val port=p.getString("port","8080")!!.toIntOrNull()?:8080
 val root=try{p.getString("path",null)?.let{DocumentFile.fromFile(File(it))}?:p.getString("folder",null)?.let{DocumentFile.fromTreeUri(this,Uri.parse(it))}}catch(e:Exception){null}
 if((p.contains("path") && android.os.Build.VERSION.SDK_INT>=30 && !android.os.Environment.isExternalStorageManager())||root==null||ip==null||(auth&&password.length<8)||!root.isDirectory){stopSelf();return START_NOT_STICKY}
 server=object:NanoHTTPD(ip,port){
 val token=java.util.UUID.randomUUID().toString()+java.util.UUID.randomUUID().toString()
 fun reply(status:Response.Status,t:String)=newFixedLengthResponse(status,"text/plain; charset=utf-8",t)
 override fun serve(s:IHTTPSession):Response{try{
 if(auth){
 val cookie=s.headers["cookie"]?.split(';')?.map{it.trim()}?.firstOrNull{it.startsWith("lsp=")}?.substringAfter('=')
 if(s.uri=="/login"&&s.method==Method.POST){val body=HashMap<String,String>();s.parseBody(body);val supplied=s.parameters["password"]?.firstOrNull()?:"";if(java.security.MessageDigest.isEqual(password.toByteArray(),supplied.toByteArray()))return newFixedLengthResponse(Response.Status.REDIRECT,"text/plain", "").apply{addHeader("Location","/");addHeader("Set-Cookie","lsp=$token; Path=/; HttpOnly; SameSite=Strict");addHeader("Cache-Control","no-store")}}
 if(cookie!=token)return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8","""<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><style>body{background:#101827;color:white;font:18px system-ui;padding:30px}input,button{display:block;padding:14px;margin:12px 0;border-radius:10px;max-width:90%}</style><h1>Local Server Pro</h1><form method="post" action="/login"><label>Contraseña<input type="password" name="password" required></label><button>Entrar</button></form>""").apply{addHeader("Cache-Control","no-store")}
 }
 if(s.method!=Method.GET)return reply(Response.Status.METHOD_NOT_ALLOWED,"Solo lectura")
 val path=s.parameters["p"]?.firstOrNull()?:"";val parts=path.split('/').filter{it.isNotEmpty()};var node:DocumentFile=root!!
 for(part in parts){if(part==".."||part=="."||part.startsWith("."))return reply(Response.Status.FORBIDDEN,"Ruta no permitida");node=node.listFiles().firstOrNull{it.name==part}?:return reply(Response.Status.NOT_FOUND,"No encontrado");if(p.contains("path")){val base=File(p.getString("path","")!!).canonicalFile;val actual=File(node.uri.path!!).canonicalFile;if(!actual.path.startsWith(base.path+File.separator))return reply(Response.Status.FORBIDDEN,"Ruta no permitida")}}
 if(node.isFile){val stream=contentResolver.openInputStream(node.uri)?:return reply(Response.Status.NOT_FOUND,"No encontrado");return newChunkedResponse(Response.Status.OK,"application/octet-stream",stream).apply{addHeader("Content-Disposition","attachment; filename*=UTF-8''"+enc(node.name?:"archivo"));addHeader("X-Content-Type-Options","nosniff")}}
 val rows=node.listFiles().filter{!(it.name?:"").startsWith(".")}.sortedWith(compareBy<DocumentFile>{!it.isDirectory}.thenBy{it.name?.lowercase()}).joinToString(""){f->val name=f.name?:"archivo";val next=(parts+name).joinToString("/");"<a class='item' href='/?p=${enc(next)}'>${if(f.isDirectory) "📁" else "📄"} <span>${esc(name)}</span><small>${if(f.isDirectory) "Carpeta" else "${f.length()} bytes"}</small></a>"}
 val back=if(parts.isEmpty()) "" else "<a href='/?p=${enc(parts.dropLast(1).joinToString("/"))}'>← Volver</a>"
 return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8","""<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local Server Pro</title><style>body{font:16px system-ui;background:#101827;color:#e5efff;max-width:850px;margin:auto;padding:24px}a{color:#93c5fd;text-decoration:none}.item{display:flex;gap:12px;background:#22334a;margin:10px 0;padding:16px;border-radius:12px;align-items:center}.item span{flex:1;overflow-wrap:anywhere}small{color:#aabbd1}input{box-sizing:border-box;width:100%;padding:14px;border-radius:10px;border:1px solid #64748b;background:#172539;color:white}</style><h1>Local Server Pro</h1><p>Archivos compartidos · Solo lectura</p><p>${esc(path.ifEmpty{"Inicio"})}</p>$back<p><input id="q" placeholder="Buscar archivos" oninput="document.querySelectorAll('.item').forEach(e=>e.hidden=!e.textContent.toLowerCase().includes(this.value.toLowerCase()))"></p><style>.item[hidden]{display:none}</style>$rows</html>""").apply{addHeader("Cache-Control","no-store")}
 }catch(e:Exception){return reply(Response.Status.INTERNAL_ERROR,"Error de acceso")}}
 };try{server!!.start();running=true}catch(e:Exception){stopSelf()};return START_NOT_STICKY
 }
 override fun onDestroy(){server?.stop();server=null;running=false;super.onDestroy()}
 override fun onTimeout(startId:Int,fgsType:Int){stopSelf()}
}
