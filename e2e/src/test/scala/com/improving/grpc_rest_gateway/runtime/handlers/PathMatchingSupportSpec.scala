package com.improving
package grpc_rest_gateway
package runtime
package handlers

import io.netty.handler.codec.http.{HttpMethod, QueryStringDecoder}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PathMatchingSupportSpec extends AnyWordSpec with Matchers with PathMatchingSupport {
  override protected val httpMethodsToUrisMap: Map[String, Seq[String]] =
    Map(
      "GET" -> Seq(
        "/v1/messages",
        "/v1/messages/{message_id}",
        "/v1/users/{user_id}",
        "/v1/messages/{message_id}/sub/{sub.subfield}",
        "/v1/messages/{message_id}/users/{user_id}",
        "/v1/test/users/{user_id}/messages/{message_id}",
        "/v1/test/messages/{message_id}/users/{user_id}"
      ),
      "POST" -> Seq("/v1/messages", "/v1/test/users/{user_id}/messages/{message_id}"),
      "PUT" -> Seq("/v1/test/users/{user_id}/messages/{message_id}")
    )

  "PathMatchingSupport" should {

    "ignore when http method is not configured" in {
      supportsCall(new HttpMethod("custom"), "/v1/messages") shouldBe false
    }

    "ignore when path is not configured" in {
      supportsCall(HttpMethod.POST, "/v1/messages/1") shouldBe false
    }

    "ignore when path has parameters but not configured" in {
      supportsCall(HttpMethod.GET, "/v1/messages/1/users") shouldBe false
    }

    "match simple path" in {
      supportsCall(HttpMethod.POST, "/v1/messages") shouldBe true
      supportsCall(HttpMethod.GET, "/v1/messages") shouldBe true
    }

    "match path with parameters" in {
      supportsCall(HttpMethod.GET, "/v1/messages/1") shouldBe true
      supportsCall(HttpMethod.GET, "/v1/users/1") shouldBe true
    }

    "supports call for same path for different methods" in {
      supportsCall(HttpMethod.POST, "/v1/test/users/{user_id}/messages/{message_id}") shouldBe true
      supportsCall(HttpMethod.PUT, "/v1/test/users/{user_id}/messages/{message_id}") shouldBe true
    }

    "supported call different scenarios" in {
      isSupportedCall("GET", "/v1/messages/{message_id}/users/{user_id}", "GET", "/v1/messages/1/users/2") shouldBe true
      isSupportedCall("POST", "/v1/messages", "POST", "/v1/messages") shouldBe true
      isSupportedCall(
        "GET",
        "/v1/messages/{message_id}/sub/{sub.subfield}",
        "GET",
        "/v1/messages/1/users/1"
      ) shouldBe false
      isSupportedCall(
        "GET",
        "/v1/test/users/{user_id}/messages/{message_id}",
        "GET",
        "/v1/test/users/abc/messages/16"
      ) shouldBe true
      isSupportedCall(
        "GET",
        "/v1/test/messages/{message_id}/users/{user_id}",
        "GET",
        "/v1/test/messages/16/users/xyz"
      ) shouldBe true
      isSupportedCall(
        "GET",
        "/v1/test/messages/{message_id}/users/{user_id}",
        "GET",
        "/v1/test/users/abc/messages/16"
      ) shouldBe false
      isSupportedCall("POST", "/v1/messages", "GET", "/v1/messages") shouldBe false
      isSupportedCall(
        "POST",
        "/v1/test/users/{user_id}/messages/{message_id}",
        "POST",
        "/v1/test/users/asdf/messages/3"
      )
      isSupportedCall(
        "PUT",
        "/v1/test/users/{user_id}/messages/{message_id}",
        "PUT",
        "/v1/test/users/asdf/messages/3"
      )
    }

    "get empty parameters when uri doesn't have path parameters and no request parameters" in {
      mergeParameters("/v1/messages", new QueryStringDecoder("/v1/messages")) shouldBe empty
    }

    "get parameters when uri have request parameters but no path parameters" in {
      mergeParameters("/v1/messages", new QueryStringDecoder("/v1/messages?message_id=1")) shouldBe
        Map("message_id" -> "1")
    }

    "get parameters when uri doesn't have path parameters but no request parameters" in {
      mergeParameters("/v1/messages/{message_id}", new QueryStringDecoder("/v1/messages/1")) shouldBe
        Map("message_id" -> "1")
    }

    "get parameters when uri have both path parameters and request parameters" in {
      mergeParameters(
        "/v1/messages/{message_id}",
        new QueryStringDecoder("/v1/messages/1?revision=2&date=2024-11-05")
      ) shouldBe
        Map("message_id" -> "1", "revision" -> "2", "date" -> "2024-11-05")
    }
  }
}
