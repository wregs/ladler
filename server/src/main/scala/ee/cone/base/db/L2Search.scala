
package ee.cone.base.db

import ee.cone.base.connection_api.{BaseCoHandler, Obj, Attr, CoHandler}
import ee.cone.base.util.{Hex, HexDebug, Never}

// minKey/merge -> filterRemoved -> takeWhile -> toId

class SearchIndexImpl(
  converter: RawSearchConverter,
  rawKeyExtractor: RawKeyExtractor,
  rawVisitor: RawVisitor,
  attrFactory: AttrFactory,
  nodeFactory: NodeFactory
) extends SearchIndex {
  private def execute[Value](attr: RawAttr[Value])(in: SearchRequest[Value]) = {
    //println("SS",attr)
    val minKey = converter.keyWithoutObjId(attr, attr.converter.convertEmpty())
    val whileKey = converter.keyWithoutObjId(attr, in.value) // not protected from empty
    val fromKey = if(in.objId.isEmpty) whileKey
      else converter.key(attr, in.value, in.objId.get)
    val tx = in.tx.asInstanceOf[ProtectedBoundToTx[_]]
    val rawIndex = if(tx.enabled) tx.rawIndex else throw new Exception("tx is disabled")
    rawIndex.seek(fromKey)
    rawVisitor.execute(rawIndex, rawKeyExtractor, whileKey, minKey.length, in.feed)
  }
  def handlers[Value](labelAttr: Attr[_], propAttr: Attr[Value]) = {
    val labelRawAttr = labelAttr.asInstanceOf[RawAttr[_]]
    val propRawAttr = propAttr.asInstanceOf[RawAttr[Value]]
    if(labelRawAttr.propId.value != 0L)
      throw new Exception(s"bad index on label: $labelAttr")
    if(propRawAttr.labelId.value != 0L)
      throw new Exception(s"bad index on prop: $propAttr")
    val attr = attrFactory(labelRawAttr.labelId, propRawAttr.propId, propRawAttr.converter)
    def setter(on: Boolean, node: Obj) = {
      val key = converter.key(attr, node(propAttr), node(nodeFactory.objId))
      val rawIndex = node(nodeFactory.rawIndex)
      val value = converter.value(on)
      rawIndex.set(key, value)
      //println(s"set index $labelAttr -- $propAttr -- $on -- ${Hex(key)} -- ${Hex(value)}")
    }
    val searchKey = SearchByLabelProp[Value](labelAttr.defined, propAttr.defined)
    CoHandler(searchKey)(execute[Value](attr)) ::
      OnUpdateImpl.handlers(labelAttr :: propAttr :: Nil, setter)
  }
}

object OnUpdateImpl extends OnUpdate {
  def handlers(attrs: List[Attr[_]], invoke: (Boolean,Obj) ⇒ Unit) = {
    val definedAttrs = attrs.map(_.defined)
    def setter(on: Boolean)(node: Obj) =
      if (definedAttrs.forall(node(_))) invoke(on, node)
    definedAttrs.flatMap{ a => List(
      CoHandler(BeforeUpdate(a))(setter(on=false)),
      CoHandler(AfterUpdate(a))(setter(on=true))
    )}
  }
}


/*
case class SearchAttrCalcImpl[Value](searchAttrId: Attr[Value])(
  val on: List[Attr[Boolean]], set: (DBNode,Boolean)=>Unit
) extends SearchAttrCalc[Value] {
  def beforeUpdate(node: DBNode) = set(node, false)
  def handle(node: DBNode) = set(node, true)
}
*/


/*

// was LabelIndexAttrInfoList / LabelPropIndexAttrInfoList
// direct ruled may be composed or labelAttr

class AllFactExtractor(
  rawFactConverter: RawFactConverter, matcher: RawKeyExtractor, to: CalcIndex
)(
  whileKeyPrefix: RawKey = rawFactConverter.keyHeadOnly
) extends KeyPrefixMatcher {
  def from(tx: RawIndex) = execute(tx, rawFactConverter.keyHeadOnly)
  def from(tx: RawIndex, objId: DBNode) =
    execute(tx, rawFactConverter.keyWithoutAttrId(objId))
  protected def feed(keyPrefix: RawKey, ks: KeyStatus): Boolean = {
    if(!matcher.matchPrefix(whileKeyPrefix, ks.key)){ return false }
    val(objId,attrId) = ??? //rawFactConverter.keyFromBytes(ks.key)
    ??? //to(objId, attrId) = rawFactConverter.valueFromBytes(ks.value)
    true
  }
}
*/

/*
class InnerIndexSearch(
  rawFactConverter: RawFactConverter,
  rawIndexConverter: RawIndexConverter,
  matcher: RawKeyMatcher,
  tx: RawTx
) {
  private var selectKey = Array[Byte]()
  private def seek(key: RawKey) = { selectKey = key; tx.seek(key) }
  def seek(): Unit = seek(rawFactConverter.keyHeadOnly)
  def seek(objId: Long): Unit = seek(rawFactConverter.keyWithoutAttrId(objId))
  def seek(attrId: Long, value: DBValue): Unit =
    seek(rawIndexConverter.keyWithoutObjId(attrId,value))
}
*/