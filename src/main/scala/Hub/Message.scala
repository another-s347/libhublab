package Hub
import com.google.protobuf.ByteString

object Message{
    case class ExternalMessage(index:Int, response:Option[String], request:Option[String], body:Option[ByteString], sessionID:String)
    case class UriMessage(hubName:String,action:String,target:String,query:Option[String],body:Option[String])
}