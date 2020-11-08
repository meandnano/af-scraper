package util

import java.time.{LocalDate, ZoneId, ZonedDateTime}

import org.scalatest.wordspec.AnyWordSpec

class DealDateUtilImplTest extends AnyWordSpec {

  private val timezone = ZoneId.of("Australia/Darwin")
  private val today = LocalDate.of(2020, 11, 11) // Tuesday, week 46, year 2020
  private val sunday = LocalDate.of(2020, 11, 15) // Sunday, week 46, year 2020
  private val dateNowProvider = { _: ZoneId => today }
  private val parser = new DealDateUtilImpl(timezone, dateNowProvider)

  "parseTimeBoundaries" when {
    "no raw dates passed" should {
      "return today's morning and sunday's evening" in {
        assertResult((beginDateTime(today), endDateTime(sunday)))(parser.parseTimeBoundaries(None, None))
      }
    }

    "wrongly formatted dates passed" should {
      "act the same as no dates passed" in {
        assertResult((beginDateTime(today), endDateTime(sunday)))(parser.parseTimeBoundaries(Some("2020-11-01"), Some("2020-11-02")))
      }
    }

    "only start date passed" should {
      "return start date's morning and sunday's evening" in {
        val expectedDateBegin = LocalDate.of(2020, 12, 5)
        val expectedDateEnd = LocalDate.of(2020, 12, 6)
        assertResult((beginDateTime(expectedDateBegin), endDateTime(expectedDateEnd)))(parser.parseTimeBoundaries(Some("05/12-2020"), None))
      }
    }

    "only start date passed and it is sunday" should {
      "return sunday's morning and sunday's evening" in {
        val expectedDateBegin = LocalDate.of(2020, 12, 6)
        val expectedDateEnd = LocalDate.of(2020, 12, 6)
        assertResult((beginDateTime(expectedDateBegin), endDateTime(expectedDateEnd)))(parser.parseTimeBoundaries(Some("06/12-2020"), None))
      }
    }

    "only end date passed" should {
      "return today's morning and passed date" in {
        val expectedDateEnd = LocalDate.of(2020, 12, 5)
        assertResult((beginDateTime(today), endDateTime(expectedDateEnd)))(parser.parseTimeBoundaries(None, Some("05/12-2020")))
      }
    }

    "both dates passed" should {
      "return both parsed" in {
        val expectedDateBegin = LocalDate.of(2020, 12, 4)
        val expectedDateEnd = LocalDate.of(2020, 12, 5)
        assertResult((beginDateTime(expectedDateBegin), endDateTime(expectedDateEnd)))(parser.parseTimeBoundaries(Some("04/12-2020"), Some("05/12-2020")))
      }
    }

  }

  def beginDateTime(date: LocalDate): ZonedDateTime = date.atTime(0, 0, 0).atZone(timezone)

  def endDateTime(date: LocalDate): ZonedDateTime = date.atTime(23, 59, 59).atZone(timezone)

}
