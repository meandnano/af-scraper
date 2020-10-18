package persistence

import com.fasterxml.jackson.databind.node.ObjectNode

case class Store(internalId: Long,
                 networkKey: String,
                 location: Option[PointLocation],
                 address: Option[String])

object Store {
  def apply(networkKey: String)(node: ObjectNode): Option[Store] = {
    val internalId = Option(node.get("storeId"))
      .filter(_.isTextual)
      .map(_.asText)
      .flatMap(_.toLongOption)

    if (internalId.isEmpty) return None

    val location = for {
      geoObj <- Option(node.get("geoPoint"))
      lat <- Option(geoObj.get("latitude"))
        .filter(_.isNumber)
        .map(_.asDouble())
      lon <- Option(geoObj.get("longitude"))
        .filter(_.isNumber)
        .map(_.asDouble())
    } yield PointLocation(latitude = lat, longitude = lon)

    val address: Option[String] = Option(node.get("address"))
      .flatMap(obj => Option(obj.get("formattedAddress")))
      .filter(_.isTextual)
      .map(_.textValue)

    Some(new Store(internalId.get, networkKey, location, address))
  }

}
