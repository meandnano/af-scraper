package io

import org.slf4j.LoggerFactory
import providers.Store
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.indexes.{Index, IndexType}

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
}

class DaoImpl(private val storageManager: StorageManager) extends Dao {

  import io.PersistentHandlers._

  private val logger = LoggerFactory.getLogger(getClass.getSimpleName)

  private def storesCollection: Future[BSONCollection] = storageManager.collection("stores")

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

}