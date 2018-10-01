package Hub
import ujson.Js.{Obj=>JsonObject}
import scala.concurrent.Future

case class FileObject(fileName:String, prefix:String, data:JsonObject, user:String)

class File(hub:Hub.Hub){
    def GetAll(prefix:String,fileName:String):Future[FileObject]={
        //hub.sendUriRequest("file","get","all",)
        ???
    }
}