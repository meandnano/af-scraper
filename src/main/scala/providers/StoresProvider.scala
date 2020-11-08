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
import util.Logging

class StoresProvider(networkDef: NetworkDef,
                     requestHandler: RequestHandler,
                     makeAStore: ObjectNode => Option[Store])(implicit val actorSystem: ActorSystem) extends Logging {

  /**
   * Filters stores by their internal ID based on the configured sores filter
   */
  val storesFilter: Store => Boolean = networkDef.storesFilter match {
    case Seq() => _ => true
    case whitelisted: Seq[Long] => store => whitelisted.contains(store.internalId)
  }

  private val mapper = new ObjectMapper()

  def stream(): Source[Store, NotUsed] = {
    val request = HttpRequest(uri = Uri(networkDef.storesLink))
    Source.future(requestHandler.handle(request))
      .filter(_.status.isSuccess)
      .flatMapConcat(_.entity.dataBytes)
      .via(JsonReader.select("$[*]"))
      .map(_.utf8String)
      .map(mapper.readTree(_).asInstanceOf[ObjectNode])
      .map(makeAStore)
      .filter(_.nonEmpty)
      .map(_.get)
      .filter(storesFilter)
      .filter(store => {
        logger.info(s"Fetched store ${store.internalId}. ${if (store.onlineOnly) "Skipping as it is online only" else ""}")
        !store.onlineOnly
      })
  }

}
