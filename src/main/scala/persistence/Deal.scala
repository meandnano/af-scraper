package persistence

import java.time._
import java.time.format.DateTimeFormatter

import com.fasterxml.jackson.databind.node.{ArrayNode, ObjectNode}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class Deal(store: Store,
                title: String,
                imgUrl: Option[String],
                priceUnit: Option[String],
                priceOriginal: Option[Double],
                priceDisc: Option[Double],
                dateStart: ZonedDateTime,
                dateEnd: ZonedDateTime)

object Deal {
  private lazy val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM-yyyy")

  private lazy val LOGGER = LoggerFactory.getLogger("DealsParser")

  def apply(store: Store, homeTimeZone: ZoneId)(node: ObjectNode): Deal = {
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

    val (dealBeginsAt, dealEndsAt) = parseTimeBoundaries(
      Option(promoObj.get("startDate")).map(_.asText()),
      Option(promoObj.get("endDate")).map(_.asText()),
      homeTimeZone
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

  // TODO test it
  private def parseTimeBoundaries(maybeDateBegin: Option[String], maybeDateEnd: Option[String], tz: ZoneId): (ZonedDateTime, ZonedDateTime) = {
    val timeBegins: ZonedDateTime = parseDate(maybeDateBegin, tz, isDealsEnd = false)
      .getOrElse(ZonedDateTime.now(tz))

    val timeEnds = parseDate(maybeDateEnd, tz, isDealsEnd = true)
      .getOrElse {
        val dayOfWeek = timeBegins.getDayOfWeek
        timeBegins.plusDays(DayOfWeek.values().length - dayOfWeek.getValue)
      }

    timeBegins -> timeEnds
  }

  private def parseDate(text: Option[String], tz: ZoneId, isDealsEnd: Boolean): Option[ZonedDateTime] = text.flatMap(value =>
    Try(LocalDate.parse(value, DATE_FORMAT)) match {
      case Failure(exception) =>
        LOGGER.error(s"Unable to parse date $text", exception)
        None
      case Success(value) =>
        // Deals begins at 00:00:00 of the specified date and ends at 23:59:59
        val time = if (isDealsEnd) LocalTime.of(23, 59, 59, 0) else LocalTime.of(0, 0, 0, 0)
        Some(value.atTime(time).atZone(tz))
    }
  )
}
