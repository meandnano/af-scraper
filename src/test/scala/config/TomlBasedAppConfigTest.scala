package config

import org.scalatest.wordspec.AnyWordSpec
import org.tomlj.Toml

class TomlBasedAppConfigTest extends AnyWordSpec {

  def configFrom(str: String): TomlBasedAppConfig = new TomlBasedAppConfig(Toml.parse(str))

  "networks()" when {
    "empty input" should {
      "return empty Seq" in {
        assertResult(Seq())(configFrom("").networks())
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
          NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one"),
          NetworkDef(title = "second", storesLink = "http://stores.two", dealsLink = "http://deals.two"),
        )

        assertResult(parsed)(configFrom(strVal).networks().toSet)
      }
    }

    "input contains partial network" should {
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
          NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one"),
          NetworkDef(title = "second", storesLink = "http://stores.two", dealsLink = "http://deals.two"),
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
          NetworkDef(title = "first", storesLink = "http://stores.one", dealsLink = "http://deals.one"),
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

          assertResult(Seq())(configFrom(strVal).networks())
        }
      }

    }
  }

}
