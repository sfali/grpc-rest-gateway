package com.improving
package grpc_rest_gateway
package runtime
package handlers

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.apache.pekko
import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.testkit.ScalatestRouteTest
import pekko.http.scaladsl.server.*
import pekko.http.scaladsl.server.Directives.*

import scala.concurrent.ExecutionContext.Implicits.global

class SwaggerHandlerTest extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  // Simple mock implementation for testing
  class MockGatewayHandler(val specName: String) extends GrpcGatewayHandler {
    override val specificationName: String = specName
    override val route: Route = complete("mock response")
  }

  "SwaggerHandler" should "be created with services" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    swaggerHandler should not be null
  }

  it should "handle empty services list" in {
    val services = Seq.empty[MockGatewayHandler]
    val swaggerHandler = SwaggerHandler(services)
    
    swaggerHandler should not be null
  }

  it should "handle multiple services with duplicate names" in {
    val services = Seq(
      new MockGatewayHandler("duplicate-service"),
      new MockGatewayHandler("unique-service"),
      new MockGatewayHandler("duplicate-service") // Duplicate
    )
    val swaggerHandler = SwaggerHandler(services)
    
    swaggerHandler should not be null
  }

  it should "handle services with special characters in names" in {
    val services = Seq(
      new MockGatewayHandler("service-with-dashes"),
      new MockGatewayHandler("service_with_underscores"),
      new MockGatewayHandler("service.with.dots")
    )
    val swaggerHandler = SwaggerHandler(services)
    
    swaggerHandler should not be null
  }

  "SwaggerHandler routes" should "redirect root to docs landing page" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    Get("/") ~> swaggerHandler.route ~> check {
      status shouldBe StatusCodes.PermanentRedirect
      header("location").get.value() shouldBe "/docs/index.html"
    }
  }

  it should "redirect docs prefix to docs landing page" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    Get("/docs") ~> swaggerHandler.route ~> check {
      status shouldBe StatusCodes.PermanentRedirect
      header("location").get.value() shouldBe "/docs/index.html"
    }
  }

  it should "return swagger index page for docs landing page" in {
    val services = Seq(new MockGatewayHandler("test-service"), new MockGatewayHandler("another-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      status shouldBe StatusCodes.OK
      contentType shouldBe ContentTypes.`text/html(UTF-8)`
      
      val content = responseAs[String]
      content should include("test-service")
      content should include("another-service")
      content should include("Swagger UI")
    }
  }

  it should "return 404 for non-existent swagger resources" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    Get("/docs/non-existent.css") ~> swaggerHandler.route ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "return 404 for non-existent spec files" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    Get("/specs/non-existent.yml") ~> swaggerHandler.route ~> check {
      status shouldBe StatusCodes.NotFound
    }
  }

  it should "pass through unsupported paths" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)
    
    Get("/unsupported/path") ~> swaggerHandler.route ~> check {
      handled shouldBe false
    }
  }
}
