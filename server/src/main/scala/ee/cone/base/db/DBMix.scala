package ee.cone.base.db

import java.util.UUID
import java.util.concurrent.ExecutorService

import ee.cone.base.connection_api._

trait DBAppMix extends AppMixBase {
  def mainDB: DBEnv[MainEnvKey]
  def instantDB: DBEnv[InstantEnvKey]
  def createMergerConnection: LifeCycle=>CoMixBase
  override def toStart =
    new Merger(executionManager,createMergerConnection) :: super.toStart
}

trait InMemoryDBAppMix extends DBAppMix {
  lazy val mainDB = new InMemoryEnv[MainEnvKey](1L)
  lazy val instantDB = new InMemoryEnv[InstantEnvKey](0L)
}

trait DBConnectionMix extends CoMixBase {
  def dbAppMix: DBAppMix
  def lifeCycle: LifeCycle

  // L0/L1
  lazy val rawConverter = new RawConverterImpl
  lazy val rawVisitor = new RawVisitorImpl
  // L2
  lazy val noObj = new NoObjImpl(handlerLists)

  lazy val asDefined = new AttrValueType[Boolean]
  lazy val asDBObjId = new AttrValueType[ObjId]
  lazy val dbWrapType = new WrapType[ObjId]

  lazy val attrFactory = new AttrFactoryImpl(handlerLists,objIdFactory)
  lazy val nodeAttrs = new NodeAttrsImpl(attrFactory, asDBObjId)()

  lazy val objIdFactory = new ObjIdFactoryImpl
  lazy val dbObjIdValueConverter = new DBObjIdValueConverter(asDBObjId,rawConverter,objIdFactory)

  lazy val zeroNode = ObjIdImpl(0L,0L)
  lazy val factIndex =
    new FactIndexImpl(rawConverter, dbObjIdValueConverter, rawVisitor, handlerLists, nodeAttrs, attrFactory, dbWrapType, objIdFactory, zeroNode, asDefined)
  lazy val onUpdate = new OnUpdateImpl(attrFactory)
  lazy val searchIndex =
    new SearchIndexImpl(handlerLists, rawConverter, dbObjIdValueConverter, rawVisitor, attrFactory, nodeAttrs, objIdFactory, onUpdate)

  // L3
  lazy val instantTx = new CurrentTxImpl[InstantEnvKey](dbAppMix.instantDB)
  lazy val mainTx = new CurrentTxImpl[MainEnvKey](dbAppMix.mainDB)
  lazy val txSelector = new TxSelectorImpl(nodeAttrs, instantTx, mainTx)

  lazy val asString = new AttrValueType[String]
  lazy val findAttrs = new FindAttrsImpl(attrFactory,asDefined,asString)()
  lazy val findNodes = new FindNodesImpl(findAttrs, handlerLists, nodeAttrs, noObj, attrFactory, factIndex, objIdFactory, dbObjIdValueConverter, dbWrapType)()

  lazy val preCommitCheckCheckAll = new PreCommitCheckAllOfConnectionImpl(txSelector)
  lazy val mandatory = new MandatoryImpl(attrFactory, preCommitCheckCheckAll)
  lazy val unique = new UniqueImpl(attrFactory, txSelector, preCommitCheckCheckAll, searchIndex, findNodes)

  lazy val asDBObj = new AttrValueType[Obj]
  lazy val asUUID = new AttrValueType[Option[UUID]]
  lazy val asBoolean = new AttrValueType[Boolean]
  lazy val labelFactory = new LabelFactoryImpl(attrFactory,asDBObj)

  lazy val instantTxManager =
    new DefaultTxManagerImpl[InstantEnvKey](lifeCycle, dbAppMix.instantDB, instantTx, preCommitCheckCheckAll)

  // Sys
  lazy val eventSourceAttrs =
    new EventSourceAttrsImpl(objIdFactory,attrFactory,labelFactory,asDBObj,asDBObjId,asUUID,asString)()
  lazy val eventSourceOperations =
    new EventSourceOperationsImpl(eventSourceAttrs,nodeAttrs,findAttrs,factIndex,handlerLists,findNodes,instantTx,mainTx,searchIndex,mandatory)

  lazy val alienAccessAttrs = new AlienAccessAttrs(objIdFactory, attrFactory, asDBObj, asString)()
  lazy val alienWrapType = new WrapType[Unit] {}
  lazy val demandedWrapType = new WrapType[DemandedNode] {}
  lazy val alienCanChange = new Alien(alienAccessAttrs,nodeAttrs,attrFactory,handlerLists,findNodes,mainTx,factIndex,alienWrapType,demandedWrapType,objIdFactory)




  override def handlers =
      txSelector.handlers :::
      findNodes.handlers :::
      eventSourceOperations.handlers :::
      alienCanChange.handlers :::
      new DefinedValueConverter(asDefined, rawConverter).handlers :::
      new BooleanValueConverter(asBoolean, rawConverter).handlers :::
      dbObjIdValueConverter.handlers :::
      new DBObjValueConverter(asDBObj,dbObjIdValueConverter,findNodes,nodeAttrs).handlers :::
      new UUIDValueConverter(asUUID,rawConverter).handlers :::
      new StringValueConverter(asString,rawConverter).handlers :::
      super.handlers
}

trait MergerDBConnectionMix extends DBConnectionMix {
  lazy val mainTxManager =
    new DefaultTxManagerImpl[MainEnvKey](lifeCycle, dbAppMix.mainDB, mainTx, preCommitCheckCheckAll)
  override def handlers =
    new MergerEventSourceOperationsImpl(eventSourceOperations, findAttrs, instantTxManager, mainTxManager, findNodes)().handlers :::
    super.handlers
}

trait SessionDBConnectionMix extends DBConnectionMix {
  lazy val muxFactory = new MuxFactoryImpl
  lazy val mainTxManager =
    new SessionMainTxManagerImpl(lifeCycle, dbAppMix.mainDB, mainTx, preCommitCheckCheckAll, muxFactory)
  override def handlers =
    new SessionEventSourceOperationsImpl(
      eventSourceOperations, eventSourceAttrs, nodeAttrs, findAttrs, instantTxManager, mainTxManager, findNodes
    )().handlers :::
    super.handlers
}

/*
class MixedReadModelContext(attrInfoList: =>List[AttrInfo], rawIndex: RawIndex) {
  private lazy val lazyAttrInfoList = attrInfoList
  private lazy val attrCalcExecutor = new AttrCalcExecutor(lazyAttrInfoList)
  private lazy val attrCalcInfo = new AttrInfoRegistry(lazyAttrInfoList)

  /*private lazy val muxIndex =
    new MuxUnmergedIndex(new EmptyUnmergedIndex, rawIndex)*/
  private lazy val innerIndex =
    new MixedInnerIndex(2L, 3L, rawIndex, attrCalcInfo.indexed)
  private lazy val index =
    new RewritableTriggeringIndex(innerIndex.innerIndex, attrCalcExecutor)

  private lazy val preCommitCalcCollector = new PreCommitCalcCollectorImpl
  private lazy val sysAttrCalcContext =
    new SysAttrCalcContext(index, innerIndex.indexSearch, ThrowValidateFailReaction/*IgnoreValidateFailReaction*/)
  private lazy val sysPreCommitCheckContext =
    new SysPreCommitCheckContext(index, innerIndex.indexSearch, preCommitCalcCollector, ThrowValidateFailReaction)
  lazy val labelIndexAttrInfoList = new LabelIndexAttrInfoList(SearchAttrInfoFactoryImpl)
  lazy val labelPropIndexAttrInfoList =
    new LabelPropIndexAttrInfoList(sysAttrCalcContext, SearchAttrInfoFactoryImpl)
  lazy val relSideAttrInfoList =
    new RelSideAttrInfoList(sysAttrCalcContext, sysPreCommitCheckContext, SearchAttrInfoFactoryImpl)
  lazy val mandatoryPreCommitCheckList =
    new MandatoryPreCommitCheckList(sysPreCommitCheckContext)
  lazy val uniqueAttrCalcList = new UniqueAttrCalcList(sysAttrCalcContext)
  lazy val deleteAttrCalcList = new DeleteAttrCalcList(sysAttrCalcContext)
}
*/

/*
connection
longTx
invalidate +
frame/dbTx
event
 */

/*
in:
MuxUnmergedTx
NonEmptyUnmergedTx

?:
Replay
AllOriginalFactExtractor
BlockIterator
*/
