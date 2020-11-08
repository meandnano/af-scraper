import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import config.{NetworkDef, TomlBasedAppConfig}
import org.tomlj.Toml
import persistence._
import providers.{DealsProvider, RequestHandler, RequestHandlerImpl, StoresProvider}
import util.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Random, Success}

object AfScr extends Logging {

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

    val stores = new StoresProvider(networkDef, requestHandler, Store(networkKey))
      .stream()
      .runWith(Sink.seq)

    val rand = new Random(System.nanoTime())
    val delayer = () => (rand.nextInt(8) + 3).seconds // delay before request will be 3-10 seconds

    stores.flatMap(dao.saveStores)
      .transformWith {
        case Failure(exception) => logger.error("Unable to save stores", exception)
          Future.successful(0L)
        case Success(value) => logger.info(s"Successfully save stores: $value")
          Source.future(stores)
            .mapConcat(identity)
            .map(store => new DealsProvider(store, networkDef, requestHandler, delayer, Deal(store)))
            .flatMapMerge(3, _.stream())
            .alsoToMat(Sink.fold(0L)((count, _) => count + 1))(Keep.right)
            .toMat(dao.dealsSink())(Keep.left)
            .run()
      }
  }

}