package com.improving
package grpc_rest_gateway
package runtime
package server

import runtime.handlers.GrpcGatewayHandler
import com.typesafe.config.Config
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
    toHandlers: ManagedChannel => Seq[GrpcGatewayHandler],
    executor: Option[Executor],
    usePlainText: Boolean = true
  ): GatewayServer = {
    val channelBuilder = ManagedChannelBuilder.forAddress(serviceHost, servicePort)
    if (usePlainText) channelBuilder.usePlaintext()
    executor.map(channelBuilder.executor)
    val channel = channelBuilder.build()

    new GatewayServerImpl(GrpcGatewayServerBuilder(gatewayPort, toHandlers(channel)).build(), gatewayPort)
  }

  def apply(
    config: Config,
    toHandlers: ManagedChannel => Seq[GrpcGatewayHandler],
    executor: Option[Executor]
  ): GatewayServer =
    GatewayServer(
      serviceHost = config.getString("host"),
      servicePort = config.getInt("service-port"),
      gatewayPort = config.getInt("gateway-port"),
      toHandlers = toHandlers,
      usePlainText = config.getBoolean("use-plain-text"),
      executor = executor
    )
}
