package providers

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId, ZonedDateTime}

import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class Deal(source: String,
                store: Long,
                title: String,
                imgUrl: Option[String],
                priceUnit: Option[String],
                priceOriginal: Option[Double],
                priceDisc: Option[Double],
                dateStart: Option[ZonedDateTime],
                dateEnd: Option[ZonedDateTime])

object Deal {
  private lazy val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM-yyyy")

  private lazy val LOGGER = LoggerFactory.getLogger("DealsParser")

  def apply(storeID: Long)(node: ObjectNode): Deal = {
    LOGGER.debug(s"Parsing providers.Deal from $node for store $storeID")

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
    new Deal(source = "hk",
      store = storeID,
      title = name,
      imgUrl = imgUrl,
      priceUnit = Option(node.get("priceUnit")).map(_.asText()),
      priceOriginal = Option(node.get("price")).flatMap(_.asText().toDoubleOption),
      priceDisc = Option(promoObj.get("price")).flatMap(_.asText().toDoubleOption),
      dateStart = Option(promoObj.get("startDate")).map(_.asText()).flatMap(parseDate),
      dateEnd = Option(promoObj.get("endDate")).map(_.asText()).flatMap(parseDate),
    )
  }

  private def parseDate(text: String): Option[ZonedDateTime] =
    Try(LocalDate.parse(text, DATE_FORMAT)) match {
      case Failure(exception) =>
        LOGGER.error(s"Unable to parse date $text", exception)
        None
      case Success(value) =>
        Some(value).map(_.atTime(23, 59, 59).atZone(ZoneId.of("Europe/Stockholm")))
    }
}
