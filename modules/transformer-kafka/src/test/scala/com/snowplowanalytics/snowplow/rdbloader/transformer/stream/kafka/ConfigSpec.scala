/*
 * Copyright (c) 2012-2023 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.rdbloader.transformer.stream.kafka

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.snowplowanalytics.snowplow.badrows.Processor
import com.snowplowanalytics.snowplow.rdbloader.common.config.TransformerConfig
import com.snowplowanalytics.snowplow.rdbloader.common.config.TransformerConfig.Validations
import com.snowplowanalytics.snowplow.rdbloader.common.telemetry.Telemetry
import com.snowplowanalytics.snowplow.rdbloader.generated.BuildInfo
import com.snowplowanalytics.snowplow.rdbloader.transformer.stream.common.Config
import com.snowplowanalytics.snowplow.rdbloader.transformer.stream.common.ConfigUtils._
import org.specs2.mutable.Specification

import java.net.URI
import java.time.Instant
import scala.concurrent.duration._

class ConfigSpec extends Specification {
  import ConfigSpec._

  "config fromString" should {
    "be able to parse extended transformer-kafka config" in {
      val result =
        getConfigFromResource("/transformer/azure/transformer.kafka.config.reference.hocon", c => Config.parse[IO](c).value.unsafeRunSync())
      val expected = Config(
        exampleStreamInput,
        exampleWindowPeriod,
        exampleOutput,
        exampleQueueConfig,
        TransformerConfig.Formats.WideRow.JSON,
        exampleMonitoringStream,
        exampleTelemetry,
        exampleDefaultFeatureFlags,
        exampleValidations
      )
      result must beRight(expected)
    }

    "be able to parse minimal transformer-kafka config" in {
      val result = getConfigFromResource("/transformer/azure/transformer.kafka.config.minimal.hocon", testParseStreamConfig)
      val expected = Config(
        exampleStreamInput,
        exampleWindowPeriod,
        exampleDefaultOutput,
        exampleQueueConfig,
        TransformerConfig.Formats.WideRow.JSON,
        exampleDefaultMonitoringStream,
        defaultTelemetry,
        exampleDefaultFeatureFlags,
        emptyValidations
      )
      result must beRight(expected)
    }
  }

}

object ConfigSpec {
  val exampleStreamInput = Config.StreamInput.Kafka(
    "enriched",
    "localhost:9092",
    Map(
      "enable.auto.commit" -> "false",
      "auto.offset.reset" -> "latest",
      "group.id" -> "transformer",
      "allow.auto.create.topics" -> "false"
    )
  )
  val exampleWindowPeriod = 10.minutes
  val exampleOutput = Config.Output.AzureBlobStorage(
    URI.create("https://accountName.blob.core.windows.net/transformed/"),
    TransformerConfig.Compression.Gzip,
    4096,
    10000,
    Config.Output.Bad.Queue.Kafka(
      "bad",
      "localhost:9092",
      Map("acks" -> "all")
    )
  )
  val exampleDefaultOutput = exampleOutput.copy(bad = Config.Output.Bad.File)

  val exampleQueueConfig = Config.QueueConfig.Kafka(
    "loaderTopic",
    "localhost:9092",
    Map("acks" -> "all")
  )
  val exampleFormats = TransformerConfig.Formats.WideRow.JSON
  val exampleMonitoringStream = Config.Monitoring(
    Some(TransformerConfig.Sentry(URI.create("http://sentry.acme.com"))),
    Config.MetricsReporters(
      Some(Config.MetricsReporters.StatsD("localhost", 8125, Map("app" -> "transformer"), 1.minute, None)),
      Some(Config.MetricsReporters.Stdout(1.minutes, None)),
      true
    )
  )
  val exampleDefaultMonitoringStream = Config.Monitoring(
    None,
    Config.MetricsReporters(None, Some(Config.MetricsReporters.Stdout(1.minutes, None)), true)
  )
  val exampleTelemetry =
    Telemetry.Config(
      false,
      15.minutes,
      "POST",
      "collector-g.snowplowanalytics.com",
      443,
      true,
      Some("my_pipeline"),
      Some("hfy67e5ydhtrd"),
      Some("665bhft5u6udjf"),
      Some("transformer-kafka-ce"),
      Some("1.0.0")
    )
  val defaultTelemetry =
    Telemetry.Config(
      false,
      15.minutes,
      "POST",
      "collector-g.snowplowanalytics.com",
      443,
      true,
      None,
      None,
      None,
      None,
      None
    )
  val exampleDefaultFeatureFlags = TransformerConfig.FeatureFlags(false, None, true, false)
  val exampleValidations = Validations(Some(Instant.parse("2021-11-18T11:00:00.00Z")))
  val emptyValidations = Validations(None)
  val TestProcessor = Processor(BuildInfo.name, BuildInfo.version)
}
