package com.urdnot.iot.processor

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import com.paulgoldbaum.influxdbclient.Parameter.Precision
import com.paulgoldbaum.influxdbclient.{Database, InfluxDB, Point}
import com.typesafe.config.Config
import com.typesafe.scalalogging.{LazyLogging, Logger}
import com.urdnot.iot.processor.DataObjects.beerReading
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContextExecutor, Future}

object KafkaBeerReader extends Directives with utils with LazyLogging {
  implicit val system: ActorSystem = ActorSystem("beer_processor")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = materializer.executionContext
  val log: Logger = Logger("beer")

  val consumerConfig: Config = system.settings.config.getConfig("akka.kafka.consumer")
  val envConfig: Config = system.settings.config.getConfig("env")
  val bootstrapServers: String = consumerConfig.getString("kafka-clients.bootstrap.servers")
  val consumerSettings: ConsumerSettings[String, Array[Byte]] =
    ConsumerSettings(consumerConfig, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(bootstrapServers)

  //Influxdb
  val influxdb: InfluxDB = InfluxDB.connect(envConfig.getString("influx.host"), envConfig.getInt("influx.port"))
  val database: Database = influxdb.selectDatabase(envConfig.getString("influx.database"))

  Consumer.committableSource(consumerSettings, Subscriptions.topics(envConfig.getString("kafka.topic")))
    .runForeach { x =>
      val record = x.record.value()
      val rawJson: String = record.map(_.toChar).mkString.replace("\'", "\"").replace("L", "")
      val parsedJson: JsValue = Json.parse(rawJson)
      //{"timestamp": 1568937379528, "beer": "2", "temperature_f": 39.875, "pour": "0.33", "temperature_c": 4.375, "ticks": 45}

      val sensor = "flow-meter"
      val host = "pi-flow-meter"

      val jsonRecord: beerReading = jsonDataValidation(parsedJson)

      val beerPoint = Point(sensor, jsonRecord.timeStamp.get)
        .addTag("sensor", sensor)
        .addTag("host", host)
        .addField("tempF", jsonRecord.temperatureFarenheit.get)
        .addField("tempC", jsonRecord.temperatureCelcius.get)
        .addField("beer", jsonRecord.beer.get)
        .addField("pour", jsonRecord.pour.get)
        .addField("ticks", jsonRecord.ticks.get)
      Future(database.write(beerPoint, precision = Precision.MILLISECONDS))

    }
  def jsonDataValidation(jsonData: JsValue): beerReading = {
    log.info(jsonData.toString())
    val timeStamp: Option[Long] = Option((jsonData \ "timestamp").asOpt[Long].getOrElse(0L))
    val beer: Option[Int] = Option((jsonData \ "beer").asOpt[String].getOrElse("0").toInt)
    val pour: Option[Double] = Option((jsonData \ "pour").asOpt[String].getOrElse("0.0").toDouble)
    val ticks: Option[Int] = Option((jsonData \ "ticks").asOpt[Int].getOrElse(0))
    val tempF: Option[Double] = Option((jsonData \ "temperature_f").asOpt[Double].getOrElse(0.0))
    val tempC: Option[Double] = Option((jsonData \ "temperature_c").asOpt[Double].getOrElse(0.0))
    beerReading(
      timeStamp = timeStamp,
      beer = beer,
      pour = pour,
      ticks = ticks,
      temperatureFarenheit = tempF,
      temperatureCelcius = tempC
    )
  }
}
