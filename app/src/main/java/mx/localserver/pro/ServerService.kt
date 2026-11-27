package mx.localserver.pro
import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import fi.iki.elonen.NanoHTTPD
class ServerService: Service(){
 companion object { @Volatile var running=false }
 private var server:NanoHTTPD?=null
 override fun onBind(i:Intent?):IBinder?=null
 override fun onStartCommand(i:Intent?,flags:Int,id:Int):Int {
 val nm=getSystemService(NotificationManager::class.java); nm.createNotificationChannel(NotificationChannel("server","Servidor local",NotificationManager.IMPORTANCE_LOW))
 startForeground(1,Notification.Builder(this,"server").setContentTitle("Local Server Pro").setContentText("Servidor local activo").setSmallIcon(android.R.drawable.stat_sys_upload).build())
 if(server!=null)return START_NOT_STICKY
 val uri=getSharedPreferences("server",0).getString("folder",null)
 if(uri==null){stopSelf();return START_NOT_STICKY}
 val root=DocumentFile.fromTreeUri(this,Uri.parse(uri))
 if(root==null){stopSelf();return START_NOT_STICKY}
 server=object:NanoHTTPD(8080){
 override fun serve(s:IHTTPSession):Response { try {
 if(s.method!=Method.GET)return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED,"text/plain","Solo lectura")
 val files=root.listFiles().filter{it.isFile}; val index=s.parameters["file"]?.firstOrNull()?.toIntOrNull()
 if(s.uri=="/download" && index!=null){val f=files.getOrNull(index)?:return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","No encontrado"); val stream=contentResolver.openInputStream(f.uri)?:return newFixedLengthResponse(Response.Status.NOT_FOUND,"text/plain","No encontrado"); return newChunkedResponse(Response.Status.OK,"application/octet-stream",stream).apply{addHeader("Content-Disposition","attachment; filename*=UTF-8''"+java.net.URLEncoder.encode(f.name?:"archivo","UTF-8").replace("+","%20"))} }
 val html=files.mapIndexed{n,f->"<li><a href='/download?file=$n'>${android.text.TextUtils.htmlEncode(f.name?:"Archivo")}</a></li>"}.joinToString("")
 return newFixedLengthResponse("<!doctype html><html lang='es'><meta charset='utf-8'><meta name='viewport' content='width=device-width'><h1>Local Server Pro</h1><ul>$html</ul></html>")
 }catch(e:Exception){return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain","Error de acceso")}}
 }
 try{server!!.start();running=true}catch(e:Exception){stopSelf()};return START_NOT_STICKY
 }
 override fun onDestroy(){server?.stop();server=null;running=false;super.onDestroy()}
 override fun onTimeout(startId:Int,fgsType:Int){stopSelf()}
}
