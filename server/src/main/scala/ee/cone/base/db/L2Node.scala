
package ee.cone.base.db

import ee.cone.base.connection_api._

class DBWrapType extends WrapType[ObjId]

class NodeAttrsImpl(
  attr: AttrFactory,
  asDefined: AttrValueType[Boolean],
  asDBNode: AttrValueType[ObjId]
)(
  val objId: Attr[ObjId] = attr("848ca1e3-e36b-4f9b-a39d-bd6b1d9bad98", asDBNode),
  val nonEmpty: Attr[Boolean] = attr("1cc81826-a1c0-4045-ab2a-e2501b4a71fc", asDefined)
) extends NodeAttrs

class NodeFactoryImpl(
  at: NodeAttrsImpl,
  noObj: Obj,
  dbWrapType: WrapType[ObjId]
)(
  val noNode: Obj = noObj.wrap(dbWrapType, NoObjId)
) extends NodeFactory with CoHandlerProvider {
  def toNode(objId: ObjId) = noObj.wrap(dbWrapType, objId)
  def handlers = List(
    CoHandler(GetValue(dbWrapType, at.objId))((obj,innerObj)⇒innerObj.data),
    CoHandler(GetValue(dbWrapType, at.nonEmpty))((obj,innerObj)⇒innerObj.data.nonEmpty)
  )
}
