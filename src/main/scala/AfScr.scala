import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink}
import config.{NetworkDef, TomlBasedAppConfig}
import io.{RequestHandler, RequestHandlerImpl}
import org.slf4j.LoggerFactory
import org.tomlj.Toml
import providers.{Deal, DealsProvider, Store, StoresProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object AfScr {

  private val logger = LoggerFactory.getLogger("src-af")

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      logger.error("Config path supposed to be the first argument")
      System.exit(1)
    }

    val config = new TomlBasedAppConfig(Toml.parse(Paths.get(args(0))))
    implicit val system: ActorSystem = ActorSystem()
    implicit val requestHandler: RequestHandler = new RequestHandlerImpl()

    val networks: Map[String, NetworkDef] = config.networks()
    val count = Future.sequence(networks.map { case (key, networkDef) => loadNetwork(key, networkDef) })

    count.onComplete {
      case Failure(exception) =>
        throw exception
      case Success(value) =>
        logger.info(s"$value deal(s) fetched for store")
        system.terminate()
    }(system.dispatcher)
  }

  def loadNetwork(networkKey: String, networkDef: NetworkDef)(implicit requestHandler: RequestHandler, system: ActorSystem): Future[Long] = {
    val storesFilter: Store => Boolean = networkDef.storesFilter match {
      case Seq() => _ => true
      case whitelisted: Seq[Long] => store => whitelisted.contains(store.internalId)
    }

    val (count, _) =
      new StoresProvider(networkDef, requestHandler, Store(networkKey))
        .stream()
        .filter(storesFilter)
        .map(store => new DealsProvider(store, networkDef, requestHandler, Deal(store.internalId)))
        .flatMapConcat(_.stream())
        .alsoToMat(Sink.fold(0L)((count, _) => count + 1))(Keep.right)
        .toMat(Sink.foreach(println))(Keep.both)
        .run()

    count
  }

}