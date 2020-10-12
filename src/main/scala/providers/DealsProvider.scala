package providers

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.{Sink, Source}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import config.NetworkDef
import io.RequestHandler
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{ExecutionContextExecutor, Future}

object DealsProvider {
  val PAGE_SIZE = 10
}

class DealsProvider(private val store: Store,
                    private val networkDef: NetworkDef,
                    private val requestHandler: RequestHandler,
                    private val makeADeal: ObjectNode => Deal)(implicit val actorSystem: ActorSystem) {

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private val mapper = new ObjectMapper()

  def stream(): Source[Deal, NotUsed] = {
    implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

    Source.unfoldAsync(0) { offset =>
      val nextPage: Future[HttpResponse] = requestHandler.handle(buildRequest(store.internalId, offset, DealsProvider.PAGE_SIZE))

      nextPage.flatMap { response =>
        if (!response.status.isSuccess()) {
          logger.error(s"Server returned non-successful status ${response.status}")
          Future.successful(None)
        } else {
          response.entity
            .dataBytes
            .via(JsonReader.select("$.results[*]"))
            .map(_.utf8String)
            .map(mapper.readTree(_).asInstanceOf[ObjectNode])
            .map(makeADeal)
            .runWith(Sink.seq)
            .map(results => if (results.isEmpty) None else Some((results.size + offset, results)))
        }
      }
    }.mapConcat(identity)
  }

  private def buildRequest(storeId: Long, offset: Int, pageSize: Int): HttpRequest = {
    val params: Map[String, String] = Map(
      "q" -> storeId,
      "avoidCache" -> System.currentTimeMillis(),
      "size" -> pageSize,
      "offlineSize" -> offset,
    )
      .view
      .mapValues(_.toString)
      .toMap

    val originalUri = Uri(networkDef.dealsLink)
    val modifiedQuery = mutable.Map.from(originalUri.query().toMap).addAll(params).toMap
    val targetUri = originalUri.withQuery(Uri.Query(modifiedQuery))
    logger.info(s"Requesting deals from $targetUri")
    HttpRequest(uri = targetUri)
  }
}
