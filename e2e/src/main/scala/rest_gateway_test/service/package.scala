package rest_gateway_test

import rest_gateway_test.api.model.common.{Color, UUID => ProtoUUID}

import java.util.UUID

package object service {

  implicit class RequestIdOps(src: Long) {
    def toProtoUUID: ProtoUUID = UUID.nameUUIDFromBytes(src.toString.getBytes).toProtoUUID

    def getColor: Color = Color.fromValue((src % Color.values.size).toInt)
  }

  implicit class UUIDOps(src: UUID) {
    def toProtoUUID: ProtoUUID = ProtoUUID(src.toString)
  }
}
