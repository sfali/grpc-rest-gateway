package com.improving
package grpc_rest_gateway
package runtime
package core

import com.typesafe.config.Config

case class OpenApiSettings(enabled: Boolean, specsDirectory: String)

object OpenApiSettings {
  def apply(config: Config): OpenApiSettings =
    OpenApiSettings(
      enabled = config.getBoolean("enabled"),
      specsDirectory = config.getString("specs-dir")
    )
}
