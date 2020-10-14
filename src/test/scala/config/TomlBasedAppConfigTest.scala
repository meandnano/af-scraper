package config

import org.scalatest.wordspec.AnyWordSpec
import org.tomlj.{Toml, TomlInvalidTypeException}

class TomlBasedAppConfigTest extends AnyWordSpec {

  def configFrom(str: String): TomlBasedAppConfig = new TomlBasedAppConfig(Toml.parse(str))

  "mongoUri()" when {
    "presented as string" should {
      "return it" in {
        val toml = """mongo_uri = "mongo://112233" """

        assertResult(Some("mongo://112233"))(configFrom(toml).mongoDbUri())
      }
    }

    "missing" should {
      "return None" in {
        assertResult(None)(configFrom("").mongoDbUri())
      }
    }

    "presented as non-string" should {
      "return it" in {
        val toml = """mongo_uri = 112"""

        assertThrows[TomlInvalidTypeException] {
          configFrom(toml).mongoDbUri()
        }
      }
    }
  }

  "networks()" when {
    "empty input" should {
      "return empty Map" in {
        assertResult(Map())(configFrom("").networks())
      }
    }

    "input contains valid networks" should {
      "return parsed networks" in {
        val strVal =
          """[networks]
            |[networks.one]
            |title = "first"
            |stores = "http://stores.one"
            |deals = "http://deals.one"
            |
            |[networks.two]
            |title = "second"
            |stores = "http://stores.two"
            |deals = "http://deals.two"
            |""".stripMargin

        val parsed = Set(
          "one" -> NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one"),
          "two" -> NetworkDef(title = "second", storesLink = "http://stores.two", dealsLink = "http://deals.two")
        )

        assertResult(parsed)(configFrom(strVal).networks().toSet)
      }
    }

    "input contains filters of wrong type" should {
      "return networks with no filters" in {
        val strVal =
          """[networks]
            |[networks.one]
            |title = "first"
            |stores = "http://stores.one"
            |deals = "http://deals.one"
            |storesFilter = ["1", "2"]
            |""".stripMargin

        val parsed = Set(
          "one" -> NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one"),
        )

        assertResult(parsed)(configFrom(strVal).networks().toSet)
      }
    }

    "input contains filters" should {
      "return networks with filters" in {
        val strVal =
          """[networks]
            |[networks.one]
            |title = "first"
            |stores = "http://stores.one"
            |deals = "http://deals.one"
            |stores_filter = [1, 2]
            |""".stripMargin

        val parsed = Set(
          "one" -> NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one", storesFilter = Seq(1L, 2L)),
        )

        assertResult(parsed)(configFrom(strVal).networks().toSet)
      }
    }

    "input contains partial network" should {
      "skips it" in {
        val strVal =
          """[networks]
            |[networks.one]
            |title = "first"
            |stores = "http://stores.one"
            |deals = "http://deals.one"
            |
            |[networks.two]
            |stores = "http://stores.two"
            |deals = "http://deals.two"
            |
            |[networks.three]
            |title = "third"
            |deals = "http://deals.three"
            |
            |[networks.four]
            |title = "four"
            |stores = "http://stores.four"
            |""".stripMargin

        val parsed = Set(
          "one" -> NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one"),
        )

        assertResult(parsed)(configFrom(strVal).networks().toSet)
      }

      "input contains invalid field value" should {
        "skip network" in {
          val strVal =
            """[networks]
              |[networks.one]
              |title = first
              |stores = "http://stores.one"
              |deals = "http://deals.one"
              |""".stripMargin

          assertResult(Map())(configFrom(strVal).networks())
        }
      }

    }
  }

  "network" when {

    "existing network requested" should {
      "return requested" in {
        val strVal =
          """[networks]
            |[networks.one]
            |title = "first"
            |stores = "http://stores.one"
            |deals = "http://deals.one"
            |
            |[networks.two]
            |title = "second"
            |stores = "http://stores.two"
            |deals = "http://deals.two"
            |""".stripMargin

        val parsed = Some(
          NetworkDef(title = "second", storesLink = "http://stores.two", dealsLink = "http://deals.two"),
        )

        assertResult(parsed)(configFrom(strVal).network("two"))
      }
    }

    "missing network requested" should {
      "return None" in {
        val strVal =
          """[networks]
            |[networks.one]
            |title = "first"
            |stores = "http://stores.one"
            |deals = "http://deals.one"
            |""".stripMargin

        assertResult(None)(configFrom(strVal).network("two"))
      }
    }

  }

}
