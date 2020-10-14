package io

import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.{AsyncDriver, DB, MongoConnection}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Handles DB connections
 */
trait StorageManager {
  /**
   * Creates if needed and returns a mongofb collection
   *
   * @param name collection name
   */
  def collection(name: String): Future[BSONCollection]
}

class MongoBasedStorageManager(private val uri: String)(private implicit val ec: ExecutionContext) extends StorageManager {

  private lazy val driver = new AsyncDriver()
  private val database: Future[DB] = for {
    parsedUri <- MongoConnection.fromString(uri)
    con <- driver.connect(parsedUri)
    dn <- Future(parsedUri.db.get)
    db <- con.database(dn)
  } yield db

  override def collection(name: String): Future[BSONCollection] =
    database.map(_.collection(name))

}