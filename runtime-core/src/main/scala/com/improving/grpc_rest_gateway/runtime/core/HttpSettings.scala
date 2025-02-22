package com.improving
package grpc_rest_gateway
package runtime
package core

import com.typesafe.config.Config

import scala.concurrent.duration.*

case class HttpSettings(host: String, port: Int, hardTerminationDeadline: FiniteDuration)

object HttpSettings {
  def apply(config: Config): HttpSettings =
    HttpSettings(
      host = config.getString("host"),
      port = config.getInt("port"),
      hardTerminationDeadline = FiniteDuration(config.getDuration("hard-termination-deadline").toSeconds, SECONDS)
    )
}
