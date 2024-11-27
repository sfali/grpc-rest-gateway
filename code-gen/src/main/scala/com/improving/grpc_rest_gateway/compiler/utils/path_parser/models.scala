package com.improving
package grpc_rest_gateway
package compiler
package utils
package path_parser

import com.google.api.HttpRule.PatternCase

import java.util.Objects
import scala.annotation.tailrec

case class RawPath[Source](path: String, methodInfos: List[MethodInfo[Source]] = Nil)

object RawPath {
  implicit def pathOrdering[Source]: Ordering[RawPath[Source]] = (x: RawPath[Source], y: RawPath[Source]) =>
    x.path.compareTo(y.path)
}

case class MethodInfo[Source](patternCase: PatternCase, body: String, source: Source)

object MethodInfo {
  implicit def methodOrdering[Source]: Ordering[MethodInfo[Source]] =
    (x: MethodInfo[Source], y: MethodInfo[Source]) => x.patternCase.name().compareTo(y.patternCase.name())
}

class TreeNode[Source] {

  private var _path: String = ""
  private var _methodInfos: List[MethodInfo[Source]] = Nil
  private var _children: Map[String, TreeNode[Source]] = Map.empty[String, TreeNode[Source]]

  def path: String = _path
  def path_=(value: String): Unit = _path = value

  def methodInfos: List[MethodInfo[Source]] = _methodInfos
  def methodInfos_=(value: List[MethodInfo[Source]]): Unit = _methodInfos = value
  def addMethodInfo(value: MethodInfo[Source]): Unit = _methodInfos = (_methodInfos :+ value).sorted

  def children: Map[String, TreeNode[Source]] = _children
  def children_=(value: Map[String, TreeNode[Source]]): Unit = _children = value
  def addOrUpdateChildren(value: TreeNode[Source]): Unit = _children = _children + (value.path -> value)

  def isEmpty: Boolean = path.isBlank && methodInfos.isEmpty && children.isEmpty

  def updateTree(rawPath: RawPath[Source]): Unit = {
    val pathElements = rawPath.path.split("/").filterNot(_.isBlank)
    val leafNode = TreeNode(path = pathElements.last, methodInfos = rawPath.methodInfos)
    val parentNodeNames = pathElements.dropRight(1).toList
    if (parentNodeNames.isEmpty || parentNodeNames.head != path)
      throw new IllegalArgumentException(s"root is not ${parentNodeNames.head}.")
    else {
      val parentNode = getParentNode(parentNodeNames.tail, Some(this))
      parentNode.addOrUpdateChildren(leafNode)
    }
  }

  @tailrec
  private def getParentNode(
    parentNodeNames: List[String],
    currentParent: Option[TreeNode[Source]]
  ): TreeNode[Source] =
    parentNodeNames match {
      case Nil => currentParent.getOrElse(throw new IllegalArgumentException("Unable to find parentNode."))
      case name :: tail =>
        currentParent match {
          case Some(parent) =>
            parent.children.get(name) match {
              case Some(parent) => getParentNode(tail, Some(parent))
              case None =>
                val childNode = TreeNode[Source](name)
                parent.addOrUpdateChildren(childNode)
                getParentNode(tail, Some(childNode))
            }
          case None => throw new IllegalArgumentException("Unable to find parentNode.")
        }
    }

  override def hashCode(): Int = Objects.hash(path, methodInfos, children)

  override def equals(obj: Any): Boolean =
    if (obj.isInstanceOf[TreeNode[Source]]) {
      val other = obj.asInstanceOf[TreeNode[Source]]
      path == other._path && methodInfos == other.methodInfos && children == other.children
    } else super.equals(obj)

  override def toString: String = s"TreeNode(path = $path, methodInfos = $methodInfos, children = $children)"
}

object TreeNode {
  def apply[Source](
    path: String,
    methodInfos: List[MethodInfo[Source]] = Nil,
    children: Map[String, TreeNode[Source]] = Map.empty[String, TreeNode[Source]]
  ): TreeNode[Source] = {
    val node = new TreeNode[Source]()
    node.path = path
    node.methodInfos = methodInfos
    node.children = children
    node
  }

  def apply[Source](rawPath: RawPath[Source]): TreeNode[Source] = rawPathToTreeNode(rawPath)

  def empty[Source]: TreeNode[Source] = TreeNode(path = "")
}
