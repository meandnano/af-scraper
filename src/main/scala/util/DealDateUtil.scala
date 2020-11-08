package util

import java.time._
import java.time.format.DateTimeFormatter

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

trait DealDateUtil {
  protected lazy val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM-yyyy")

  /**
   * Parses both begin and end time of a deal. It processes both of them at once as they might be dependent.
   * The following rule applies:
   * - The only supported date format is dd/MM-yyyy
   * - None value is the same as incorrectly formatted value, implies missing value
   * - Missing begin date is treated as `today` is the begin date
   * - Missing end date is calculated based on the begin date. It will be on Sunday of the same week that start date belong
   * - Deal's begin time will always be 00:00:00
   * - Deal's end time will always be 23:59:59
   *
   * @param maybeDateBegin optional string representation of the begin date.
   * @param maybeDateEnd   optional string representation of the end date
   * @return pair of ZonedDateTime values first of which represent a Deal's begin time, the second - the Deal's end time
   */
  def parseTimeBoundaries(maybeDateBegin: Option[String], maybeDateEnd: Option[String]): (ZonedDateTime, ZonedDateTime)
}

/**
 * Implementation of the DealDateParser
 *
 * @param tz      time zone all dead belong to
 * @param dateNow provider for `today` value in the specified timezone
 */
class DealDateUtilImpl(private val tz: ZoneId, private val dateNow: ZoneId => LocalDate) extends DealDateUtil {

  private val LOGGER = LoggerFactory.getLogger("DateTimeParser")

  override def parseTimeBoundaries(maybeDateBegin: Option[String], maybeDateEnd: Option[String]): (ZonedDateTime, ZonedDateTime) = {
    val dateBegins: LocalDate = parseDate(maybeDateBegin).getOrElse(dateNow(tz))

    val timeEnds: LocalDate = parseDate(maybeDateEnd)
      .getOrElse {
        val dayOfWeek = dateBegins.getDayOfWeek
        dateBegins.plusDays(DayOfWeek.values().length - dayOfWeek.getValue)
      }

    val dateTimeBegins = dateBegins
      .atTime(LocalTime.of(0, 0, 0, 0))
      .atZone(tz)

    val dateTimeEnd = timeEnds
      .atTime(LocalTime.of(23, 59, 59, 0))
      .atZone(tz)

    (dateTimeBegins, dateTimeEnd)
  }

  private def parseDate(text: Option[String]): Option[LocalDate] = text.flatMap(value =>
    Try(LocalDate.parse(value, DATE_FORMAT)) match {
      case Failure(exception) =>
        LOGGER.error(s"Unable to parse date $text", exception)
        None
      case Success(value) => Some(value)
    }
  )

}
