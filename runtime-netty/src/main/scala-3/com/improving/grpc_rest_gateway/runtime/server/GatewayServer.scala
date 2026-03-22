package com.improving
package grpc_rest_gateway
package runtime
package server

import runtime.handlers.GrpcGatewayHandler
import com.typesafe.config.ConfigFactory
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import org.slf4j.LoggerFactory

import java.util.concurrent.Executor

/** REST gateway for a gRPC service instance that can be started and stopped */
sealed trait GatewayServer {
  def start(): Unit
  def stop(): Unit
}

final class GatewayServerImpl(server: GrpcGatewayServer, port: Int) extends GatewayServer {
  private val logger = LoggerFactory.getLogger(classOf[GatewayServer])

  override def start(): Unit =
    try {
      server.start()
      logger.info("Started " + this)
    } catch {
      case e: Exception =>
        throw new RuntimeException("Could not start server", e)
    }

  override def stop(): Unit =
    try {
      logger.info("Stopping " + this)
      server.shutdown()
      logger.info("Stopped " + this)
    } catch {
      case _: Exception =>
        logger.warn("Interrupted while shutting down " + this)
    }

  override def toString: String = s"GatewayServer @ port: $port"
}

/** Create a Netty-backed REST Gateway for a given gRPC server with the request handlers created by a given factory
  * method. Bind the gateway to a given port. Perform request redirection on a given thread pool.
  */
object GatewayServer {
  def apply(
    serviceHost: String,
    servicePort: Int,
    gatewayPort: Int,
    enableSwagger: Boolean,
    specsPrefix: String,
    toHandlers: ManagedChannel => Seq[GrpcGatewayHandler],
    executor: Option[Executor],
    usePlainText: Boolean = true
  ): GatewayServer = {
    val channelBuilder = ManagedChannelBuilder.forAddress(serviceHost, servicePort)
    Option.when(usePlainText)(channelBuilder).foreach(_.usePlaintext())
    executor.map(channelBuilder.executor)
    val channel = channelBuilder.build()

    new GatewayServerImpl(
      GrpcGatewayServerBuilder(gatewayPort, enableSwagger, specsPrefix, toHandlers(channel)).build(),
      gatewayPort
    )
  }

  def apply(
    toHandlers: ManagedChannel => Seq[GrpcGatewayHandler],
    executor: Option[Executor]
  ): GatewayServer = {
    val rootConfig = ConfigFactory.load()
    val serviceConfig = rootConfig.getConfig("rest-gateway")
    val openApiConfig = rootConfig.getConfig("openapi")
    GatewayServer(
      serviceHost = serviceConfig.getString("host"),
      servicePort = serviceConfig.getInt("service-port"),
      gatewayPort = serviceConfig.getInt("gateway-port"),
      enableSwagger = openApiConfig.getBoolean("enabled"),
      specsPrefix = openApiConfig.getString("specs-dir"),
      toHandlers = toHandlers,
      usePlainText = serviceConfig.getBoolean("use-plain-text"),
      executor = executor
    )
  }
}
