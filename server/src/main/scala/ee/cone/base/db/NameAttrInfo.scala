package ee.cone.base.db

import ee.cone.base.util.{Bytes, MD5, LongFits, Never}

import scala.collection.mutable

object AttrIdCompose { // do not use for original facts
  def hexSz = 4
  def idSz = 4 * hexSz
  def apply(a: AttrId, b: AttrId): AttrId = new AttrId(
    LongFits(a.value,idSz, isUnsigned = true, idSz) |
    LongFits(b.value,idSz, isUnsigned = true, 0)
  )

}

case class NoNameAttrInfo(attrId: AttrId) extends BaseNameAttrInfo {
  def nameOpt = None
}

case class NameAttrInfoImpl(attrId: AttrId, name: String) extends NameAttrInfo {
  lazy val nameOpt = Some(name)
}

object SearchAttrInfoFactoryImpl extends SearchAttrInfoFactory {
  def apply(labelOpt: Option[NameAttrInfo], propOpt: Option[NameAttrInfo]) =
    SearchAttrInfoImpl(labelOpt, propOpt)
}

case class SearchAttrInfoImpl(
  labelOpt: Option[NameAttrInfo], propOpt: Option[NameAttrInfo]
) extends SearchAttrInfo with IndexAttrInfo {
  def attrId = composedInfo.attrId
  def nameOpt = composedInfo.nameOpt
  private lazy val composedInfo: BaseNameAttrInfo = (labelOpt,propOpt) match {
    case (None,Some(info)) ⇒ info
    case (Some(info),None) ⇒ info
    case (Some(NameAttrInfoImpl(labelAttrId,_)),Some(NameAttrInfoImpl(propAttrId,_))) ⇒
      NoNameAttrInfo(AttrIdCompose(labelAttrId,propAttrId))
    case (None,None) ⇒ Never()
  }
  lazy val keyForValue: String = propOpt.orElse(labelOpt).flatMap(_.nameOpt).get
  def labelAttrId = labelOpt.get.attrId
}

class PreCommitCalcCollectorImpl extends PreCommitCalcCollector {
  private var calcList: List[()=>Unit] = Nil
  def recalculateAll(): Unit = calcList.reverseIterator.foreach(_.apply())
  def apply(thenDo: Seq[ObjId]=>Unit): ObjId=>Unit = {
    val objIds = mutable.SortedSet[ObjId]()
    calcList = (()=>thenDo(objIds.toSeq)) :: calcList
    objId => objIds += objId
  }
}

////

class AttrInfoRegistry(attrInfoList: List[AttrInfo]) {
  private lazy val indexed: Set[AttrId] =
    attrInfoList.collect { case i: IndexAttrInfo ⇒ i.attrId }.toSet
  private lazy val calcListByAttrId: Map[AttrId, List[AttrCalc]] =
    attrInfoList.collect { case attrCalc: AttrCalc ⇒
      attrCalc.affectedByAttrIds.map(attrId => (attrId, attrCalc))
    }.flatten.groupBy(_._1).mapValues(_.map(_._2))
  def updates(attrId: AttrId) =
    AttrUpdate(attrId, indexed(attrId), rewritable = ???, calcListByAttrId.getOrElse(attrId,Nil))
  lazy val version = MD5(Bytes(attrInfoList.collect {
    case i: IndexAttrInfo ⇒
      //println(s"ai: ${i.attrId.toString}")
      i.attrId.toString
    case i: AttrCalc ⇒
      //println(s"acc:${i.version}:$i")
      s"${i.version}:$i"
  }.sorted.mkString(",")))
}






/*
case class ExtractedFact(objId: Long, attrId: Long, value: DBValue)
class Replay(db: IndexingTx) {
  private lazy val changedOriginalSet = mutable.SortedSet[(Long,Long)]()
  def set(facts: List[ExtractedFact]): Unit =
    facts.foreach(fact⇒set(fact.objId, fact.attrId, fact.value))
  def set(objId: Long, attrId: Long, value: DBValue): Unit =
    if(db.set(objId, attrId, value, db.isOriginal))
      changedOriginalSet += ((objId,attrId))
  def changedOriginalFacts: List[ExtractedFact] = changedOriginalSet.map{
    case (objId, attrId) ⇒ ExtractedFact(objId, attrId, db(objId, attrId))
  }.toList
}
*/