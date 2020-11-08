package persistence

import akka.Done
import akka.stream.scaladsl.Sink
import org.slf4j.LoggerFactory
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}
import util.Logging

import scala.concurrent.Future
// TODO switch to IO bound EC
import scala.concurrent.ExecutionContext.Implicits.global

trait Dao {
  /**
   * Ensures collections constraints (e.g. indices) are set. Should be called before any
   * other collection interactions.
   */
  def initialize(): Future[_]

  /**
   * Saves passed stores in the corresponding collection. Stores are distinguished by
   * their unique combination of internal ID and network key.
   *
   * @param stores a sequence of stores to save
   * @return Future-wrapper number of modified documents
   */
  def saveStores(stores: Seq[Store]): Future[Int]

  def dealsSink(): Sink[Deal, Future[Future[Done]]]
}

class DaoImpl(private val storageManager: StorageManager) extends Dao with Logging {

  import persistence.PersistentHandlers._

  private def storesCollection: Future[BSONCollection] = storageManager.collection("stores")

  private def dealsCollection: Future[BSONCollection] = storageManager.collection("deals")

  def initialize(): Future[_] = {
    // Create unique compound index for Stores collections
    storesCollection.map(_.indexesManager)
      .flatMap(_.create(Index(Seq("internalId" -> IndexType.Ascending, "networkKey" -> IndexType.Ascending), unique = true)))
  }

  override def saveStores(stores: Seq[Store]): Future[Int] = {
    // Saving store there's no need to ensure uniqueness as it is enforced by indices
    // inserting duplicates won't lead to a failure in the `Future` so we can safely use `flatMap`
    logger.info(s"Saving ${stores.size} stores...")
    storesCollection.flatMap(_.insert(ordered = false).many(stores))
      .map(_.nModified)
  }

  def dealsSink(): Sink[Deal, Future[Future[Done]]] = {
    val futureSink = dealsCollection.map(col =>
      Sink.foreachAsync[Deal](5)(deal => col.insert(ordered = false).one(deal).map(_ => ())))

    Sink.futureSink(futureSink)
  }

}