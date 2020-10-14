package io

import providers.{Deal, PointLocation, Store}
import reactivemongo.api.bson.{BSONArray, BSONDocument, BSONDocumentHandler, Macros}

import scala.util.{Success, Try}

/**
 * BSON <-> Scala converters ("Handlers") for data classes
 */
object PersistentHandlers {

  /**
   * `PointLocation` handler converting it to\from GeoJSON representation of type Point
   */
  implicit def pointerHandler: BSONDocumentHandler[PointLocation] = new BSONDocumentHandler[PointLocation] {
    override def writeTry(t: PointLocation): Try[BSONDocument] =
      Success(BSONDocument("type" -> "point", "coordinates" -> BSONArray(t.longitude, t.latitude)))

    override def readDocument(doc: BSONDocument): Try[PointLocation] = for {
      geoType <- doc.getAsTry[String]("type")
      if geoType == "point"
      coords <- doc.getAsTry[BSONArray]("coordinates")
      if coords.size == 2
      lon <- coords.getAsTry[Double](0)
      lat <- coords.getAsTry[Double](1)
    } yield PointLocation(latitude = lat, longitude = lon)

  }

  implicit def storeHandler: BSONDocumentHandler[Store] = Macros.handler[Store]

  implicit def dealHandler: BSONDocumentHandler[Deal] = Macros.handler[Deal]

}