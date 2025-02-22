package grpc_rest_gateway

sealed trait ImplementationType

object ImplementationType {
  case object Netty extends ImplementationType {
    override def toString: String = "netty"
  }

  case object Akka extends ImplementationType {
    override def toString: String = "akka"
  }

  case object Pekko extends ImplementationType {
    override def toString: String = "pekko"
  }
}
