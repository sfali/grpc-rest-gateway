package rest_gateway_test

import rest_gateway_test.api.model.common.{Color, UUID => ProtoUUID}

import java.util.UUID
import scala.util.Random

package object service {

  implicit class RequestIdOps(src: Long) {
    def toProtoUUID: ProtoUUID = UUID.nameUUIDFromBytes(src.toString.getBytes).toProtoUUID
  }

  implicit class UUIDOps(src: UUID) {
    def toProtoUUID: ProtoUUID = ProtoUUID(src.getMostSignificantBits, src.getLeastSignificantBits)
  }

  def getRandomColor: Color = Color.fromValue(Random.nextInt(100) % Color.values.size)
}
