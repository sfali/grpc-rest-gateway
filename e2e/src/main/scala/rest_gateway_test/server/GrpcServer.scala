package rest_gateway_test.server

import com.improving.grpc_rest_gateway.runtime.server.GatewayServer
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Server, ServerBuilder}
import org.slf4j.LoggerFactory
import rest_gateway_test.api.scala_api.TestServiceA.{TestServiceAGatewayHandler, TestServiceAGrpc}
import rest_gateway_test.api.scala_api.TestServiceB.{TestServiceBGatewayHandler, TestServiceBGrpc}
import rest_gateway_test.service.{TestServiceAImpl, TestServiceBImpl}

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.ExecutionContext

class GrpcServer {
  private val logger = LoggerFactory.getLogger(classOf[GrpcServer])
  private[this] var server: Server = _

  import GrpcServer._

  private def start(executionContext: ExecutionContext): Server = {
    server = ServerBuilder
      .forPort(GrpcPort)
      .addService(TestServiceAGrpc.bindService(new TestServiceAImpl, executionContext))
      .asInstanceOf[ServerBuilder[_]]
      .addService(TestServiceBGrpc.bindService(new TestServiceBImpl, executionContext))
      .asInstanceOf[ServerBuilder[_]]
      .build()
      .start()

    logger.info("Server started, listening on {}", GrpcPort)
    server
  }

  def stop(): Unit = server.shutdown()

  private def blockUntilShutdown(): Unit = server.awaitTermination()
}

object GrpcServer {
  private val host = "0.0.0.0"
  private val GrpcPort = 8080
  val GatewayPort = 7070

  def startGrpcServer(executorService: ExecutorService, blockUntilShutdown: Boolean = true): GrpcServer = {
    val server = new GrpcServer()
    server.start(ExecutionContext.fromExecutor(executorService))
    if (blockUntilShutdown) server.blockUntilShutdown()
    server
  }

  def getGrpcClient: ManagedChannel = {
    val channelBuilder = ManagedChannelBuilder.forAddress(host, GrpcPort)
    channelBuilder.usePlaintext()
    channelBuilder.build()
  }

  def startGateWayServer(executorService: ExecutorService): GatewayServer = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(executorService)
    val server = GatewayServer(
      serviceHost = host,
      servicePort = GrpcPort,
      gatewayPort = GatewayPort,
      toHandlers = channel =>
        Seq(
          new TestServiceAGatewayHandler(channel),
          new TestServiceBGatewayHandler(channel)
        ),
      executor = Some(Executors.newFixedThreadPool(10))
    )
    server.start()
    server
  }
}
