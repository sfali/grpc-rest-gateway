package com.improving
package grpc_rest_gateway
package runtime
package core

import com.typesafe.config.Config

import scala.concurrent.duration.*

case class HttpSettings(host: String, port: Int, hardTerminationDeadline: FiniteDuration)

object HttpSettings {
  def apply(config: Config): HttpSettings = {
    val hardTerminationDeadline =
      if (config.hasPath("hard-termination-deadline"))
        FiniteDuration(config.getDuration("hard-termination-deadline").toSeconds, SECONDS)
      else 10.seconds
    HttpSettings(
      host = config.getString("host"),
      port = config.getInt("port"),
      hardTerminationDeadline = hardTerminationDeadline
    )
  }
}
