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

class SwaggerHandlerIntegrationTest extends AnyFlatSpec with Matchers with ScalatestRouteTest {

  // Simple mock implementation for testing
  class MockGatewayHandler(val specName: String) extends GrpcGatewayHandler {
    override val specificationName: String = specName
    override val route: Route = complete("mock response")
  }

  "SwaggerHandler" should "generate correct service URLs in index page" in {
    val services = Seq(
      new MockGatewayHandler("service-a"),
      new MockGatewayHandler("service-b"),
      new MockGatewayHandler("service-c")
    )
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]
      content should include("{url: '/specs/service-a.yml', name: 'service-a'}")
      content should include("{url: '/specs/service-b.yml', name: 'service-b'}")
      content should include("{url: '/specs/service-c.yml', name: 'service-c'}")
    }
  }

  it should "deduplicate service names in index page" in {
    val services = Seq(
      new MockGatewayHandler("duplicate-service"),
      new MockGatewayHandler("unique-service"),
      new MockGatewayHandler("duplicate-service") // Duplicate
    )
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]

      // Should contain both services
      content should include("duplicate-service")
      content should include("unique-service")

      // Should contain both services in the title (sorted and deduplicated)
      content should include("duplicate-service, unique-service")

      // Should contain the URLs for both services
      content should include("{url: '/specs/duplicate-service.yml', name: 'duplicate-service'}")
      content should include("{url: '/specs/unique-service.yml', name: 'unique-service'}")
    }
  }

  it should "handle empty services list in index page" in {
    val services = Seq.empty[MockGatewayHandler]
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]
      content should include("urls: []")
    }
  }

  it should "generate correct service names in title" in {
    val services = Seq(
      new MockGatewayHandler("test-service"),
      new MockGatewayHandler("another-service")
    )
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]
      // Services are sorted alphabetically, so "another-service" comes before "test-service"
      content should include("another-service, test-service")
    }
  }

  it should "sort service names alphabetically" in {
    val services = Seq(
      new MockGatewayHandler("z-service"),
      new MockGatewayHandler("a-service"),
      new MockGatewayHandler("m-service")
    )
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]
      content should include("a-service, m-service, z-service")
    }
  }

  it should "handle services with special characters in names" in {
    val services = Seq(
      new MockGatewayHandler("service-with-dashes"),
      new MockGatewayHandler("service_with_underscores"),
      new MockGatewayHandler("service.with.dots")
    )
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]
      content should include("service-with-dashes")
      content should include("service_with_underscores")
      content should include("service.with.dots")

      // Should generate correct URLs
      content should include("{url: '/specs/service-with-dashes.yml', name: 'service-with-dashes'}")
      content should include("{url: '/specs/service_with_underscores.yml', name: 'service_with_underscores'}")
      content should include("{url: '/specs/service.with.dots.yml', name: 'service.with.dots'}")
    }
  }

  it should "generate valid HTML structure" in {
    val services = Seq(new MockGatewayHandler("test-service"))
    val swaggerHandler = SwaggerHandler(services)

    Get("/docs/index.html") ~> swaggerHandler.route ~> check {
      val content = responseAs[String]

      // Should contain basic HTML structure
      content should include("<html")
      content should include("</html>")
      content should include("<head>")
      content should include("</head>")
      content should include("<body>")
      content should include("</body>")

      // Should contain Swagger UI specific elements
      content should include("Swagger UI")
      content should include("swagger-ui")
      content should include("SwaggerUIBundle")
    }
  }
}
