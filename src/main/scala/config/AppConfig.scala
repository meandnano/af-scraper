package config

import org.slf4j.LoggerFactory
import org.tomlj.TomlTable

import scala.jdk.CollectionConverters._

case class NetworkDef(title: String, storesLink: String, dealsLink: String)

class ConfigException(reason: String) extends RuntimeException(reason)

trait AppConfig {
  def networks(): Seq[NetworkDef]

  def network(networkKey: String): Option[NetworkDef]
}


class TomlBasedAppConfig(private val toml: TomlTable) extends AppConfig {
  private final val KEY_NETWORKS = "networks"

  private lazy val logger = LoggerFactory.getLogger(getClass.getSimpleName)
  private lazy val allNetworksTable = toml.getTable(KEY_NETWORKS)

  override def networks(): Seq[NetworkDef] = {
    if (!toml.isTable(KEY_NETWORKS)) {
      logger.error("No networks found")
      Seq.empty
    } else {
      parseNetworks()
    }
  }

  override def network(networkKey: String): Option[NetworkDef] = parseNetworks(Seq(networkKey)).headOption

  private def parseNetworks(keys: Seq[String] = Seq.empty): Seq[NetworkDef] = {
    val allNetworkKeys = allNetworksTable.keySet()
      .asScala

    val sourceKeys: Iterable[String] = keys match {
      case Seq() => allNetworkKeys
      case _ => allNetworkKeys.intersect(keys.toSet)
    }

    sourceKeys.map(key =>
      if (!allNetworksTable.isTable(key)) {
        throw new ConfigException(s"Sub-key $key of $KEY_NETWORKS should be a table")
      } else {
        val netDesc = allNetworksTable.getTable(key)
        Seq(Option(netDesc.getString("title")),
          Option(netDesc.getString("stores")),
          Option(netDesc.getString("deals"))).view
      }
    )
      .filter(_.forall(_.nonEmpty))
      .map(_.map(_.get))
      .map(list => NetworkDef(list(0), list(1), list(2)))
      .toSeq
  }
}
