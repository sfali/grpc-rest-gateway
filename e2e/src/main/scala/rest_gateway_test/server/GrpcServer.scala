package rest_gateway_test.server

import com.improving.grpc_rest_gateway.runtime.server.GatewayServer
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Server, ServerBuilder}
import org.slf4j.LoggerFactory
import rest_gateway_test.api.scala_api.{TestServiceAGatewayHandler, TestServiceAGrpc}
import rest_gateway_test.api.scala_api.{TestServiceBGatewayHandler, TestServiceBGrpc}
import rest_gateway_test.server.GrpcServer.GrpcPort
import rest_gateway_test.service.{TestServiceAImpl, TestServiceBImpl}

import java.util.concurrent.ExecutorService
import scala.concurrent.ExecutionContext

class GrpcServer(port: Int = GrpcPort) {
  private val logger = LoggerFactory.getLogger(classOf[GrpcServer])
  private[this] var server: Server = _

  private def start(executionContext: ExecutionContext): Server = {
    server = ServerBuilder
      .forPort(port)
      .addService(TestServiceAGrpc.bindService(new TestServiceAImpl, executionContext))
      .asInstanceOf[ServerBuilder[_]]
      .addService(TestServiceBGrpc.bindService(new TestServiceBImpl, executionContext))
      .asInstanceOf[ServerBuilder[_]]
      .build()
      .start()

    logger.info("Server started, listening on {}", port)
    server
  }

  def stop(): Unit = server.shutdown()

  private def blockUntilShutdown(): Unit = server.awaitTermination()
}

object GrpcServer {
  private val host = "0.0.0.0"
  private val GrpcPort = 8080
  private val GatewayPort = 7070

  def startGrpcServer(
    executorService: ExecutorService,
    port: Int = GrpcPort,
    blockUntilShutdown: Boolean = true
  ): GrpcServer = {
    val server = new GrpcServer(port)
    server.start(ExecutionContext.fromExecutor(executorService))
    if (blockUntilShutdown) server.blockUntilShutdown()
    server
  }

  def getGrpcClient(port: Int): ManagedChannel = {
    val channelBuilder = ManagedChannelBuilder.forAddress(host, port)
    channelBuilder.usePlaintext()
    channelBuilder.build()
  }

  def startGateWayServer(
    executorService: ExecutorService,
    grpcPort: Int = GrpcPort,
    gatewayPort: Int = GatewayPort
  ): GatewayServer = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executorService)
    val server = GatewayServer(
      serviceHost = host,
      servicePort = grpcPort,
      gatewayPort = gatewayPort,
      toHandlers = channel =>
        Seq(
          TestServiceAGatewayHandler(channel),
          TestServiceBGatewayHandler(channel)
        ),
      executor = Some(executorService)
    )
    server.start()
    server
  }
}
