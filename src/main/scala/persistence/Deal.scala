package persistence

import java.time._

import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import org.slf4j.LoggerFactory
import util.DealDateUtil

case class Deal(store: Store,
                title: String,
                imgUrl: Option[String],
                priceUnit: Option[String],
                priceOriginal: Option[Double],
                priceDisc: Option[Double],
                dateStart: ZonedDateTime,
                dateEnd: ZonedDateTime)

object Deal {

  private lazy val LOGGER = LoggerFactory.getLogger("DealsParser")

  def apply(store: Store, dateTimeParser: DealDateUtil)(node: ObjectNode): Deal = {
    LOGGER.debug(s"Parsing persistence.Deal from $node for store ${store.internalId}")

    val name: String = Seq(
      node.get("name"),
      node.get("manufacturer"),
      node.get("displayVolume"),
    ).map(Option(_).map(_.asText()))
      .map(_.getOrElse(""))
      .mkString(" ")

    val imgUrl = Seq(
      "originalImage",
      "image",
      "thumbnail"
    ).view
      .map(node.get)
      .map(Option(_).flatMap(imgObj => Option(imgObj.get("url")).map(_.asText())))
      .filter(_.nonEmpty)
      .map(_.get)
      .headOption

    val promoObj = node.withArray[ArrayNode]("potentialPromotions").get(0)

    val (dealBeginsAt, dealEndsAt) = dateTimeParser.parseTimeBoundaries(
      Option(promoObj.get("startDate")).map(_.asText()),
      Option(promoObj.get("endDate")).map(_.asText()),
    )

    new Deal(
      store = store,
      title = name,
      imgUrl = imgUrl,
      priceUnit = Option(node.get("priceUnit")).map(_.asText()),
      priceOriginal = Option(node.get("price")).flatMap(_.asText().toDoubleOption),
      priceDisc = Option(promoObj.get("price")).flatMap(_.asText().toDoubleOption),
      dateStart = dealBeginsAt,
      dateEnd = dealEndsAt,
    )
  }
}
