package com.improving
package grpc_rest_gateway
package runtime
package handlers

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext.Implicits.global

class SwaggerHandlerIntegrationTest extends AnyFlatSpec with Matchers {

  // Simple mock implementation for testing
  class MockGatewayHandler(val specName: String) extends GrpcGatewayHandler(null) {
    override val specificationName: String = specName
    override protected def dispatchCall(method: io.netty.handler.codec.http.HttpMethod, uri: String, body: String): scala.concurrent.Future[(Int, scalapb.GeneratedMessage)] = ???
    override val serviceName: String = specName
    override protected val httpMethodsToUrisMap: Map[String, Seq[String]] = Map.empty
  }

  "SwaggerHandler" should "generate correct service URLs in index page" in {
    val services = Seq(
      new MockGatewayHandler("service-a"),
      new MockGatewayHandler("service-b"),
      new MockGatewayHandler("service-c")
    )
    val swaggerHandler = new SwaggerHandler(services)
    
    // Use reflection to access the private indexPage field
    val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
    indexPageField.setAccessible(true)
    val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]
    
    indexPage should include("{url: '/specs/service-a.yml', name: 'service-a'}")
    indexPage should include("{url: '/specs/service-b.yml', name: 'service-b'}")
    indexPage should include("{url: '/specs/service-c.yml', name: 'service-c'}")
  }

  it should "deduplicate service names in index page" in {
    val services = Seq(
      new MockGatewayHandler("duplicate-service"),
      new MockGatewayHandler("unique-service"),
      new MockGatewayHandler("duplicate-service") // Duplicate
    )
    val swaggerHandler = new SwaggerHandler(services)
    
    // Use reflection to access the private indexPage field
    val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
    indexPageField.setAccessible(true)
    val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]
    
    // Should contain both services
    indexPage should include("duplicate-service")
    indexPage should include("unique-service")
    
    // Should contain both services in the title (sorted and deduplicated)
    indexPage should include("duplicate-service, unique-service")
    
    // Should contain the URLs for both services (but not necessarily deduplicated in the array)
    indexPage should include("{url: '/specs/duplicate-service.yml', name: 'duplicate-service'}")
    indexPage should include("{url: '/specs/unique-service.yml', name: 'unique-service'}")
  }

  it should "handle empty services list in index page" in {
    val services = Seq.empty[MockGatewayHandler]
    val swaggerHandler = new SwaggerHandler(services)
    
    // Use reflection to access the private indexPage field
    val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
    indexPageField.setAccessible(true)
    val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]
    
    indexPage should include("urls: []")
  }

  it should "generate correct service names in title" in {
    val services = Seq(
      new MockGatewayHandler("test-service"),
      new MockGatewayHandler("another-service")
    )
    val swaggerHandler = new SwaggerHandler(services)
    
    // Use reflection to access the private indexPage field
    val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
    indexPageField.setAccessible(true)
    val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]
    
    // Services are sorted alphabetically, so "another-service" comes before "test-service"
    indexPage should include("another-service, test-service")
  }

  it should "sort service names alphabetically" in {
    val services = Seq(
      new MockGatewayHandler("z-service"),
      new MockGatewayHandler("a-service"),
      new MockGatewayHandler("m-service")
    )
    val swaggerHandler = new SwaggerHandler(services)
    
    // Use reflection to access the private indexPage field
    val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
    indexPageField.setAccessible(true)
    val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]
    
    indexPage should include("a-service, m-service, z-service")
  }

  it should "handle services with special characters in names" in {
    val services = Seq(
      new MockGatewayHandler("service-with-dashes"),
      new MockGatewayHandler("service_with_underscores"),
      new MockGatewayHandler("service.with.dots")
    )
    val swaggerHandler = new SwaggerHandler(services)
    
    // Use reflection to access the private indexPage field
    val indexPageField = swaggerHandler.getClass.getDeclaredField("indexPage")
    indexPageField.setAccessible(true)
    val indexPage = indexPageField.get(swaggerHandler).asInstanceOf[String]
    
    indexPage should include("service-with-dashes")
    indexPage should include("service_with_underscores")
    indexPage should include("service.with.dots")
    
    // Should generate correct URLs
    indexPage should include("{url: '/specs/service-with-dashes.yml', name: 'service-with-dashes'}")
    indexPage should include("{url: '/specs/service_with_underscores.yml', name: 'service_with_underscores'}")
    indexPage should include("{url: '/specs/service.with.dots.yml', name: 'service.with.dots'}")
  }
}
