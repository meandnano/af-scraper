import java.nio.file.Paths

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import config.{NetworkDef, TomlBasedAppConfig}
import io._
import org.slf4j.LoggerFactory
import org.tomlj.Toml
import providers.{Deal, DealsProvider, Store, StoresProvider}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object AfScr {

  private val logger = LoggerFactory.getLogger("src-af")

  def main(args: Array[String]): Unit = {
    if (args.length < 1) {
      logger.error("Config path supposed to be the first argument")
      System.exit(1)
    }

    val config = new TomlBasedAppConfig(Toml.parse(Paths.get(args(0))))

    val maybeDbUri = config.mongoDbUri()
    if (maybeDbUri.isEmpty) {
      logger.error("No DB URI configured")
      System.exit(2)
    }

    val dbUri = maybeDbUri.get

    implicit val system: ActorSystem = ActorSystem()
    implicit val requestHandler: RequestHandler = new RequestHandlerImpl()

    val storageManager = new MongoBasedStorageManager(dbUri)
    implicit val dao: Dao = new DaoImpl(storageManager)

    val networks: Map[String, NetworkDef] = config.networks()
    val count = dao.initialize() // make sure indices are created
      .flatMap(_ => // load stores and deals for every network
        Future.sequence(networks.map { case (key, networkDef) => loadNetwork(key, networkDef) })
      )

    count.onComplete {
      case Failure(exception) =>
        throw exception
      case Success(value) =>
        logger.info(s"$value deal(s) fetched for store")
        system.terminate()
    }(system.dispatcher)
  }

  def loadNetwork(networkKey: String, networkDef: NetworkDef)(implicit requestHandler: RequestHandler, dao: Dao, system: ActorSystem): Future[Long] = {
    val storesFilter: Store => Boolean = networkDef.storesFilter match {
      case Seq() => _ => true
      case whitelisted: Seq[Long] => store => whitelisted.contains(store.internalId)
    }

    val stores = new StoresProvider(networkDef, requestHandler, Store(networkKey))
      .stream()
      .filter(storesFilter)
      .runWith(Sink.seq)

    stores.flatMap(dao.saveStores)
      .transformWith {
        case Failure(exception) => logger.error("Unable to save stores", exception)
          Future.successful(0L)
        case Success(value) => logger.info(s"Successfully save stores: $value")
          val (count: Future[Long], _: Future[Done]) =
            Source.future(stores)
              .mapConcat(identity)
              .map(store => new DealsProvider(store, networkDef, requestHandler, 5.seconds, Deal(store)))
              .flatMapConcat(_.stream())
              .alsoToMat(Sink.fold(0L)((count, _) => count + 1))(Keep.right)
              .toMat(Sink.foreach(deal => logger.debug(deal.toString)))(Keep.both)
              .run()

          count
      }
  }

}