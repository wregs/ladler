
package ee.cone.base.db

import ee.cone.base.connection_api._
import ee.cone.base.util.{Hex, HexDebug, Never}

// minKey/merge -> filterRemoved -> takeWhile -> toId

class SearchIndexImpl(
  handlerLists: CoHandlerLists,
  rawConverter: RawConverter,
  nodeValueConverter: RawValueConverter[ObjId],
  rawVisitor: RawVisitor,
  attrFactory: AttrFactory,
  nodeAttributes: NodeAttrs,
  objIdFactory: ObjIdFactory,
  onUpdate: OnUpdate
) extends SearchIndex {
  private type GetConverter[Value] = ()⇒(Value,ObjId)⇒Array[Byte]
  private def txSelector = handlerLists.single(TxSelectorKey, ()⇒Never())
  private def execute[Value](attrId: ObjId, getConverter: GetConverter[Value])(in: SearchRequest[Value]) = {
    //println("SS",attr)
    val valueConverter = getConverter()
    val fromKey = valueConverter(in.value, in.objId)
    val whileKey = if(!in.onlyThisValue) rawConverter.toBytes(attrId, objIdFactory.noObjId)
      else if(in.objId.nonEmpty) valueConverter(in.value, objIdFactory.noObjId) else fromKey
    val rawIndex = txSelector.rawIndex(in.tx)
    rawIndex.seek(fromKey)
    rawVisitor.execute(rawIndex, whileKey, b ⇒ in.feed(rawConverter.fromBytes(b,2,nodeValueConverter,0)))
  }
  def handlers[Value](labelAttr: Attr[_], propAttr: Attr[Value]) = {
    val labelAttrId = attrFactory.attrId(labelAttr)
    val propAttrId = attrFactory.attrId(propAttr)
    val attr = attrFactory.derive(labelAttrId, propAttr)
    val attrId = attrFactory.attrId(attr)
    val getConverter: GetConverter[Value] = { ()⇒
      val converter = attrFactory.converter(propAttr)
      (value, objId) ⇒
        val key = converter.toBytes(attrId, value, objId)
        if(key.length > 0) key else Never()
    }
    def setter(on: Boolean, node: Obj) = {
      val dbNode = node(nodeAttributes.objId)
      val key = getConverter()(node(propAttr), dbNode)
      val value = if(on)
        rawConverter.toBytes(objIdFactory.noObjId,objIdFactory.noObjId)
        else Array[Byte]()
      txSelector.rawIndex(dbNode).set(key, value)
      //println(s"set index $labelAttr -- $propAttr -- $on -- ${Hex(key)} -- ${Hex(value)}")
    }
    val searchKey = SearchByLabelProp[Value](labelAttrId, propAttrId)
    CoHandler(searchKey)(execute[Value](attrId,getConverter)) ::
      onUpdate.handlers(labelAttrId :: propAttrId :: Nil, setter)
  }
}

class OnUpdateImpl(attrFactory: AttrFactory) extends OnUpdate {
  def handlers(attrIds: List[ObjId], invoke: (Boolean,Obj) ⇒ Unit) = {
    def setter(on: Boolean)(node: Obj) =
      if (attrIds.forall(attrId⇒node(attrFactory.defined(attrId)))) invoke(on, node)
    attrIds.flatMap{ a => List(
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