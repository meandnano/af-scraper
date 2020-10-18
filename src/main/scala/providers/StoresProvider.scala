package providers

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.Source
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import config.NetworkDef
import persistence.Store

class StoresProvider(networkDef: NetworkDef,
                     requestHandler: RequestHandler,
                     makeAStore: ObjectNode => Option[Store])(implicit val actorSystem: ActorSystem) {

  private val mapper = new ObjectMapper()

  /**
   * Checks whether passed json node is "online store"
   */
  private def isOnlineStores(node: ObjectNode): Boolean =
    Option(node.get("onlineStore"))
      .filter(_.isBoolean)
      .forall(_.asBoolean())

  def stream(): Source[Store, NotUsed] = {
    val request = HttpRequest(uri = Uri(networkDef.storesLink))
    Source.future(requestHandler.handle(request))
      .filter(_.status.isSuccess)
      .flatMapConcat(_.entity.dataBytes)
      .via(JsonReader.select("$[*]"))
      .map(_.utf8String)
      .map(mapper.readTree(_).asInstanceOf[ObjectNode])
      .filterNot(isOnlineStores)
      .map(makeAStore)
      .filter(_.nonEmpty)
      .map(_.get)
  }

}
