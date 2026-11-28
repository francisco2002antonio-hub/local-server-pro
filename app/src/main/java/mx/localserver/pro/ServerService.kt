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
 fun address(c:Context):String? {val cm=c.getSystemService(ConnectivityManager::class.java);val networks=cm.allNetworks.toList();for(t in listOf(NetworkCapabilities.TRANSPORT_WIFI,NetworkCapabilities.TRANSPORT_ETHERNET,NetworkCapabilities.TRANSPORT_CELLULAR)){for(n in networks){if(cm.getNetworkCapabilities(n)?.hasTransport(t)==true){val ip=cm.getLinkProperties(n)?.linkAddresses?.firstOrNull{it.address is Inet4Address}?.address?.hostAddress;if(ip!=null)return ip}}};return java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.filter{it.isUp&&!it.isLoopback}?.flatMap{it.inetAddresses.toList()}?.firstOrNull{it is Inet4Address&&it.isSiteLocalAddress}?.hostAddress} }
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
 if(s.method!=Method.GET && s.method!=Method.POST)return reply(Response.Status.METHOD_NOT_ALLOWED,"Método no permitido")
 val uploads=HashMap<String,String>()
 if(s.method==Method.POST){if(!auth)return reply(Response.Status.FORBIDDEN,"Activa la contraseña para modificar archivos");if((s.headers["content-length"]?.toLongOrNull()?:0L)>268435456L)return reply(Response.Status.BAD_REQUEST,"Máximo 256 MiB por archivo");s.parseBody(uploads)}
 val path=s.parameters["p"]?.firstOrNull()?:"";val parts=path.split('/').filter{it.isNotEmpty()};var node:DocumentFile=root!!
 for(part in parts){if(part==".."||part=="."||part.startsWith("."))return reply(Response.Status.FORBIDDEN,"Ruta no permitida");node=node.listFiles().firstOrNull{it.name==part}?:return reply(Response.Status.NOT_FOUND,"No encontrado");if(p.contains("path")){val base=File(p.getString("path","")!!).canonicalFile;val actual=File(node.uri.path!!).canonicalFile;if(!actual.path.startsWith(base.path+File.separator))return reply(Response.Status.FORBIDDEN,"Ruta no permitida")}}
 if(s.method==Method.POST){
 if(s.parameters["csrf"]?.firstOrNull()!=token)return reply(Response.Status.FORBIDDEN,"Solicitud no autorizada")
 if(!node.isDirectory || !node.canWrite())return reply(Response.Status.FORBIDDEN,"Carpeta sin permiso de escritura")
 val name=s.parameters["name"]?.firstOrNull()?.trim()?:""
 fun valid(n:String)=n.isNotEmpty() && n.length<=200 && !n.startsWith(".") && n.none{it=='/' || it.code==92 || it.code<32}
 val op=s.parameters["op"]?.firstOrNull()
 if(op in listOf("mkdir","rename","upload","copy","move") && (!valid(name)||node.listFiles().any{it.name==name}))return reply(Response.Status.BAD_REQUEST,"Nombre inválido o existente")
 fun child(n:String):DocumentFile? {if(!valid(n))return null;val f=node.listFiles().firstOrNull{it.name==n}?:return null;if(p.contains("path") && File(f.uri.path!!).canonicalPath!=File(node.uri.path!!,n).absolutePath)return null;return f}
 fun copyFrom(input:java.io.InputStream, target:String, mime:String):Boolean {val out=if(p.contains("path")){val dest=File(node.uri.path!!,target);if(!dest.createNewFile())return false;DocumentFile.fromFile(dest)}else node.createFile(mime,target)?:return false;return try{input.use{src->contentResolver.openOutputStream(out.uri,"w")!!.use{dst->src.copyTo(dst)}};true}catch(e:Exception){out.delete();false}}
 val ok=when(op){
 "upload"->{val temp=uploads["file"]?.let{File(it)};if(temp==null||!temp.isFile)false else copyFrom(temp.inputStream(),name,"application/octet-stream")}
 "delete"->{val f=child(s.parameters["old"]?.firstOrNull()?:"");f!=null && (f.isFile || (f.isDirectory && f.listFiles().isEmpty())) && f.delete()}
 "copy"->{val f=child(s.parameters["old"]?.firstOrNull()?:"");if(f==null||!f.isFile)false else contentResolver.openInputStream(f.uri)?.let{copyFrom(it,name,"application/octet-stream")}?:false}

 "mkdir"->node.createDirectory(name)!=null
 "rename"->{val old=s.parameters["old"]?.firstOrNull()?:"";if(!valid(old))false else {val child=node.listFiles().firstOrNull{it.name==old};if(child==null)false else if(p.contains("path") && File(child.uri.path!!).canonicalPath!=File(node.uri.path!!,old).absolutePath)false else child.renameTo(name)}}
 else->false}
 if(!ok)return reply(Response.Status.BAD_REQUEST,"No se pudo completar la operación")
 return newFixedLengthResponse(Response.Status.REDIRECT,"text/plain", "").apply{addHeader("Location","/?p="+enc(path))}
 }
 if(node.isFile){val preview=s.parameters["preview"]?.firstOrNull()=="1";val mime=when(node.name?.substringAfterLast('.')?.lowercase()){"jpg","jpeg"->"image/jpeg";"png"->"image/png";"gif"->"image/gif";"webp"->"image/webp";"mp4"->"video/mp4";"webm"->"video/webm";else->null};val stream=contentResolver.openInputStream(node.uri)?:return reply(Response.Status.NOT_FOUND,"No encontrado");return newChunkedResponse(Response.Status.OK,if(preview && mime!=null)mime else "application/octet-stream",stream).apply{addHeader("Content-Disposition",(if(preview && mime!=null)"inline" else "attachment")+"; filename*=UTF-8''"+enc(node.name?:"archivo"));addHeader("X-Content-Type-Options","nosniff")}}
 val rows=node.listFiles().filter{!(it.name?:"").startsWith(".")}.sortedWith(compareBy<DocumentFile>{!it.isDirectory}.thenBy{it.name?.lowercase()}).joinToString(""){f->val name=f.name?:"archivo";val next=(parts+name).joinToString("/");"<a class='item' href='/?p=${enc(next)}'>${if(f.isDirectory) "📁" else "📄"} <span>${esc(name)}</span><small>${if(f.isDirectory) "Carpeta" else "${f.length()} bytes"}</small></a>"+ (if(f.isFile && name.substringAfterLast('.').lowercase() in listOf("jpg","jpeg","png","gif","webp","mp4","webm")) "<a href='/?p=${enc(next)}&amp;preview=1' target='_blank' rel='noopener'>Vista previa</a>" else "")}
 val options=node.listFiles().filter{!(it.name?:"").startsWith(".")}.joinToString(""){f->"<option value='${esc(f.name?:"")}'>${esc(f.name?:"")}</option>"}
 val tools=if(auth && node.canWrite()) """<details><summary>Crear carpeta</summary><form method="post" action="/?p=${enc(path)}"><input type="hidden" name="csrf" value="$token"><input type="hidden" name="op" value="mkdir"><input name="name" placeholder="Nombre de carpeta" required><button>Crear</button></form></details><details><summary>Renombrar archivo o carpeta</summary><form method="post" action="/?p=${enc(path)}"><input type="hidden" name="csrf" value="$token"><input type="hidden" name="op" value="rename"><select name="old">$options</select><input name="name" placeholder="Nuevo nombre" required><button>Renombrar</button></form></details>""" else ""
 val extra=if(auth && node.canWrite()) """<details><summary>Subir archivos</summary><input id="files" type="file" multiple><button onclick="uploadAll()">Subir seleccionados</button><progress id="progress" max="100" value="0"></progress><p id="result"></p></details><details><summary>Copiar archivo en esta carpeta</summary><form method="post" action="/?p=${enc(path)}"><input type="hidden" name="csrf" value="$token"><input type="hidden" name="op" value="copy"><select name="old">$options</select><input name="name" placeholder="Nombre de la copia" required><button>Copiar</button></form></details><details><summary>Eliminar archivo o carpeta vacía</summary><form method="post" action="/?p=${enc(path)}" onsubmit="return confirm('¿Eliminar definitivamente?')"><input type="hidden" name="csrf" value="$token"><input type="hidden" name="op" value="delete"><select name="old">$options</select><button>Eliminar</button></form></details><script>async function uploadAll(){const files=document.getElementById('files').files;const result=document.getElementById('result');let done=0;for(const f of files){const data=new FormData();data.append('csrf','$token');data.append('op','upload');data.append('name',f.name);data.append('file',f);try{await new Promise((resolve,reject)=>{const x=new XMLHttpRequest();x.open('POST','/?p=${enc(path)}');x.upload.onprogress=e=>{if(e.lengthComputable)document.getElementById('progress').value=100*(done+e.loaded/e.total)/files.length};x.onload=()=>x.status===200?resolve():reject();x.onerror=reject;x.send(data)});done++;result.textContent=done+' / '+files.length+' completados'}catch(e){result.textContent='Error al subir '+f.name;return}}if(done)location.reload()}</script>""" else ""
 val space=if(p.contains("path")){val disk=File(p.getString("path", "")!!);"<p>Espacio disponible: ${disk.usableSpace/1048576} MiB · Total: ${disk.totalSpace/1048576} MiB</p>"}else ""
 val back=if(parts.isEmpty()) "" else "<a href='/?p=${enc(parts.dropLast(1).joinToString("/"))}'>← Volver</a>"
 return newFixedLengthResponse(Response.Status.OK,"text/html; charset=utf-8","""<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Local Server Pro</title><style>body{font:16px system-ui;background:#101827;color:#e5efff;max-width:850px;margin:auto;padding:24px}a{color:#93c5fd;text-decoration:none}.item{display:flex;gap:12px;background:#22334a;margin:10px 0;padding:16px;border-radius:12px;align-items:center}.item span{flex:1;overflow-wrap:anywhere}small{color:#aabbd1}details{margin:12px 0;background:#22334a;padding:12px;border-radius:10px}button,select{padding:10px;margin:6px 0;max-width:100%}input{box-sizing:border-box;width:100%;padding:14px;border-radius:10px;border:1px solid #64748b;background:#172539;color:white}</style><h1>Local Server Pro</h1><p>Archivos compartidos · Gestión local</p><p>${esc(path.ifEmpty{"Inicio"})}</p>$back$space$tools$extra<p><input id="q" placeholder="Buscar archivos" oninput="document.querySelectorAll('.item').forEach(e=>e.hidden=!e.textContent.toLowerCase().includes(this.value.toLowerCase()))"></p><style>.item[hidden]{display:none}</style>$rows</html>""").apply{addHeader("Cache-Control","no-store")}
 }catch(e:Exception){return reply(Response.Status.INTERNAL_ERROR,"Error de acceso")}}
 };try{server!!.start();running=true}catch(e:Exception){stopSelf()};return START_NOT_STICKY
 }
 override fun onDestroy(){server?.stop();server=null;running=false;super.onDestroy()}
 override fun onTimeout(startId:Int,fgsType:Int){stopSelf()}
}
