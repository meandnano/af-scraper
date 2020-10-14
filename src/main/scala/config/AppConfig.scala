package config

import org.slf4j.LoggerFactory
import org.tomlj.TomlTable

import scala.jdk.CollectionConverters._

case class NetworkDef(title: String, storesLink: String, dealsLink: String, storesFilter: Seq[Long] = Seq.empty)

class ConfigException(reason: String) extends RuntimeException(reason)

trait AppConfig {
  def mongoDbUri(): Option[String]

  def networks(): Map[String, NetworkDef]

  def network(networkKey: String): Option[NetworkDef]
}


class TomlBasedAppConfig(private val toml: TomlTable) extends AppConfig {
  private final val KEY_NETWORKS = "networks"

  private lazy val logger = LoggerFactory.getLogger(getClass.getSimpleName)
  private lazy val allNetworksTable = toml.getTable(KEY_NETWORKS)

  override def mongoDbUri(): Option[String] = Option(toml.getString("mongo_uri"))
    .filter(_.nonEmpty)

  override def networks(): Map[String, NetworkDef] = {
    if (!toml.isTable(KEY_NETWORKS)) {
      logger.error("No networks found")
      Map.empty
    } else {
      parseNetworks()
    }
  }

  override def network(networkKey: String): Option[NetworkDef] = parseNetworks(Seq(networkKey)).get(networkKey)

  private def parseNetworks(keys: Seq[String] = Seq.empty): Map[String, NetworkDef] = {
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
        val maybeTable = Option(allNetworksTable.getTable(key))
        val storesFilter: Option[Seq[Long]] = for {
          table <- maybeTable
          tomlArray <- Option(table.getArray("stores_filter"))
          if !tomlArray.isEmpty && tomlArray.containsLongs()
          values: Seq[Long] = (0 until tomlArray.size()).map(tomlArray.getLong)
        } yield values

        for {
          table <- maybeTable
          title <- Option(table.getString("title"))
          storesLink <- Option(table.getString("stores"))
          dealsLink <- Option(table.getString("deals"))
        } yield key -> NetworkDef(title, storesLink, dealsLink, storesFilter.getOrElse(Seq.empty))
      }
    )
      .filter(_.nonEmpty)
      .map(_.get)
      .toMap
  }

}
