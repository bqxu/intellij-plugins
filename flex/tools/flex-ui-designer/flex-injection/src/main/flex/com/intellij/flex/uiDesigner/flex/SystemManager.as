package com.intellij.flex.uiDesigner.flex {
import com.intellij.flex.uiDesigner.css.RootStyleManager;

import flash.display.DisplayObject;
import flash.display.DisplayObjectContainer;
import flash.display.LoaderInfo;
import flash.display.Shape;
import flash.display.Sprite;
import flash.geom.Point;
import flash.geom.Rectangle;
import flash.geom.Transform;
import flash.system.ApplicationDomain;
import flash.text.TextFormat;
import flash.utils.Dictionary;

import mx.core.FlexGlobals;
import mx.core.IChildList;
import mx.core.IFlexDisplayObject;
import mx.core.IFlexModule;
import mx.core.IFlexModuleFactory;
import mx.core.IRawChildrenContainer;
import mx.core.IUIComponent;

flex::v4_5
import mx.core.RSLData;

import mx.core.Singleton;
import mx.core.UIComponent;
import mx.core.UIComponentGlobals;
import mx.core.mx_internal;
import mx.effects.EffectManager;
import mx.events.FlexEvent;
import mx.managers.DragManagerImpl;
import mx.managers.FocusManager;
import mx.managers.IFocusManager;
import mx.managers.IFocusManagerContainer;
import mx.managers.ILayoutManagerClient;
import mx.managers.ISystemManager;
import mx.managers.PopUpManagerImpl;
import mx.managers.SystemManagerGlobals;
import mx.managers.ToolTipManagerImpl;
import mx.managers.systemClasses.ActiveWindowManager;
import mx.modules.ModuleManagerGlobals;
import mx.styles.ISimpleStyleClient;
import mx.styles.IStyleClient;

use namespace mx_internal;

public class SystemManager extends Sprite implements ISystemManager, SystemManagerSB, IFocusManagerContainer {
  // offset due: 0 child of system manager is application
  internal static const OFFSET:int = 1;

  private static const LAYOUT_MANAGER_FQN:String = "mx.managers::ILayoutManager";
  private static const POP_UP_MANAGER_FQN:String = "mx.managers::IPopUpManager";
  private static const TOOL_TIP_MANAGER_FQN:String = "mx.managers::IToolTipManager2";

  private var flexModuleFactory:IFlexModuleFactory;

  private const implementations:Dictionary = new Dictionary();

  public function SystemManager(moduleFactory:IFlexModuleFactory) {
    init(moduleFactory);
  }

  private function init(moduleFactory:IFlexModuleFactory):void {
    SystemManagerGlobals.topLevelSystemManagers.push(this);

    if (UIComponentGlobals.layoutManager == null) {
      Singleton.registerClass(LAYOUT_MANAGER_FQN, LayoutManagerImpl);
      UIComponentGlobals.layoutManager = new LayoutManagerImpl();
    }

    flexModuleFactory = moduleFactory;

    //  if not null — ModuleManagerGlobals class is shareable for this Document
    if (ModuleManagerGlobals.managerSingleton == null) {
      ModuleManagerGlobals.managerSingleton = new ModuleManager(moduleFactory);
    }

    Singleton.registerClass(POP_UP_MANAGER_FQN, PopUpManagerImpl);
    Singleton.registerClass(TOOL_TIP_MANAGER_FQN, ToolTipManagerImpl);

    implementations["mx.managers::IActiveWindowManager"] = new ActiveWindowManager();

    Singleton.registerClass("mx.styles::IStyleManager2", RootStyleManager);
    Singleton.registerClass("mx.managers::IDragManager", DragManagerImpl);
    Singleton.registerClass("mx.managers::IHistoryManager", HistoryManagerImpl);
    Singleton.registerClass("mx.managers::IBrowserManager", BrowserManagerImpl);

    if (ApplicationDomain.currentDomain.hasDefinition("mx.core::TextFieldFactory")) {
      Singleton.registerClass("mx.core::ITextFieldFactory", Class(getDefinitionByName("mx.core::TextFieldFactory")));
    }

    // investigate, how we can add support for custom components — patch EffectManager or use IntellIJ IDEA index for effect annotations (the same as compiler — CompilationUnit)
    EffectManager.registerEffectTrigger("addedEffect", "added");
    EffectManager.registerEffectTrigger("creationCompleteEffect", "creationComplete");
    EffectManager.registerEffectTrigger("focusInEffect", "focusIn");
    EffectManager.registerEffectTrigger("focusOutEffect", "focusOut");
    EffectManager.registerEffectTrigger("hideEffect", "hide");
    EffectManager.registerEffectTrigger("mouseDownEffect", "mouseDown");
    EffectManager.registerEffectTrigger("mouseUpEffect", "mouseUp");
    EffectManager.registerEffectTrigger("moveEffect", "move");
    EffectManager.registerEffectTrigger("removedEffect", "removed");
    EffectManager.registerEffectTrigger("resizeEffect", "resize");
    EffectManager.registerEffectTrigger("rollOutEffect", "rollOut");
    EffectManager.registerEffectTrigger("rollOverEffect", "rollOver");
    EffectManager.registerEffectTrigger("showEffect", "show");
  }

  private var _document:DisplayObject;
  public function get document():Object {
    return _document;
  }

  public function set document(value:Object):void {
    throw new Error("forbidden");
  }

  private var _toolTipChildren:SystemChildList;
  public function get toolTipChildren():IChildList {
    if (_toolTipChildren == null) {
      _toolTipChildren = new SystemChildList(this, "topMostIndex", "toolTipIndex");
    }

    return _toolTipChildren;
  }

  private var _popUpChildren:SystemChildList;
  public function get popUpChildren():IChildList {
    if (_popUpChildren == null) {
      _popUpChildren = new SystemChildList(this, "noTopMostIndex", "topMostIndex");
    }

    return _popUpChildren;
  }

  private var _cursorChildren:SystemChildList;
  public function get cursorChildren():IChildList {
    if (_cursorChildren == null) {
      _cursorChildren = new SystemChildList(this, "toolTipIndex", "cursorIndex");
    }

    return _cursorChildren;
  }

  // The index of the highest child that is a cursor
  private var _cursorIndex:int = 0;
  internal function get cursorIndex():int {
    return _cursorIndex;
  }

  internal function set cursorIndex(value:int):void {
    _cursorIndex = value;
  }

  override public function setChildIndex(child:DisplayObject, index:int):void {
    super.setChildIndex(child, OFFSET + index);
  }
  
  public function $setChildIndex(child:DisplayObject, index:int):void {
    super.setChildIndex(child, index);
  }

  override public function getChildIndex(child:DisplayObject):int {
    return super.getChildIndex(child) - OFFSET;
  }

  public function $getChildIndex(child:DisplayObject):int {
    return super.getChildIndex(child);
  }

  override public function addChild(child:DisplayObject):DisplayObject {
    var addIndex:int = numChildren;
    if (child.parent == this) {
      addIndex--;
    }

    return addChildAt(child, addIndex);
  }

  override public function addChildAt(child:DisplayObject, index:int):DisplayObject {
    noTopMostIndex++;

    var oldParent:DisplayObjectContainer = child.parent;
    if (oldParent) {
      oldParent.removeChild(child);
    }

    return addRawChildAt(child, index + OFFSET);
  }

  override public function getObjectsUnderPoint(point:Point):Array {
    var children:Array = [];
    // Get all the children that aren't tooltips and cursors.
    var n:int = _topMostIndex;
    for (var i:int = 0; i < n; i++) {
      var child:DisplayObject = super.getChildAt(i);
      if (child is DisplayObjectContainer) {
        var temp:Array = DisplayObjectContainer(child).getObjectsUnderPoint(point);
        if (temp != null) {
          children = children.concat(temp);
        }
      }
    }

    return children;
  }

  internal function $getObjectsUnderPoint(point:Point):Array {
    return super.getObjectsUnderPoint(point);
  }

  override public function contains(child:DisplayObject):Boolean {
    if (super.contains(child)) {
      if (child.parent == this) {
        var childIndex:int = super.getChildIndex(child);
        if (childIndex < _noTopMostIndex) {
          return true;
        }
      }
      else {
        for (var i:int = 0; i < _noTopMostIndex; i++) {
          var myChild:DisplayObject = super.getChildAt(i);
          if (myChild is IRawChildrenContainer) {
            if (IRawChildrenContainer(myChild).rawChildren.contains(child)) {
              return true;
            }
          }
          if (myChild is DisplayObjectContainer && DisplayObjectContainer(myChild).contains(child)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  internal function $contains(child:DisplayObject):Boolean {
    return super.contains(child);
  }

  private var _toolTipIndex:int = 1; // see comment for _noTopMostIndex init value
  internal function get toolTipIndex():int {
    return _toolTipIndex;
  }

  internal function set toolTipIndex(value:int):void {
    var delta:int = value - _toolTipIndex;
    _toolTipIndex = value;
    cursorIndex += delta;
  }

  private var _topMostIndex:int;
  internal function get topMostIndex():int {
    return _topMostIndex;
  }

  internal function set topMostIndex(value:int):void {
    var delta:int = value - _topMostIndex;
    _topMostIndex = value;
    toolTipIndex += delta;
  }

  private var _noTopMostIndex:int = 1; // flex sdk preloader set it as 1 for mouse catcher (missed in our case) and 2 as app (we add app directly)
  internal function get noTopMostIndex():int {
    return _noTopMostIndex;
  }

  //noinspection JSUnusedGlobalSymbols
  internal function set noTopMostIndex(value:int):void {
    var delta:int = value - _noTopMostIndex;
    _noTopMostIndex = value;
    topMostIndex += delta;
  }

  override public function get numChildren():int {
    return noTopMostIndex - OFFSET;
  }

  internal function addRawChildAt(child:DisplayObject, index:int):DisplayObject {
    addingChild(child);
    super.addChildAt(child, index);

    if (child.hasEventListener(FlexEvent.ADD)) {
      child.dispatchEvent(new FlexEvent(FlexEvent.ADD));
    }

    if (child is IUIComponent) {
      IUIComponent(child).initialize();
    }

    return child;
  }

  override public function removeChild(child:DisplayObject):DisplayObject {
    _noTopMostIndex--;
    return removeRawChild(child);
  }

  override public function removeChildAt(index:int):DisplayObject {
    _noTopMostIndex--;
    return $removeChildAt(index + OFFSET);
  }

  internal function removeRawChild(child:DisplayObject):DisplayObject {
    if (child.hasEventListener(FlexEvent.REMOVE)) {
      child.dispatchEvent(new FlexEvent(FlexEvent.REMOVE));
    }

    super.removeChild(child);

    if (child is IUIComponent) {
      IUIComponent(child).parentChanged(null);
    }

    return child;
  }

  internal function $removeChildAt(index:int):DisplayObject {
    return removeRawChild(super.getChildAt(index));
  }

  internal function $getChildAt(index:int):DisplayObject {
    return super.getChildAt(index);
  }

  public function setUserDocument(object:DisplayObject):void {
    if (_document != null) {
      removeRawChild(_document);
    }
    
    _document = object;
    // мы не можем оставлять FlexGlobals.topLevelApplication пустым, тот же VideoPlayer при открытии использует FlexGlobals.topLevelApplication как parent при открытии fullscreen
    // но мы не можем и установить его в SystemManager, так как оно хочет UIComponent (для стилей)
    FlexGlobals.topLevelApplication = object;

    if (object is IUIComponent) {
      var documentUI:IUIComponent = IUIComponent(_document);
      _explicitDocumentSize.width = documentUI.explicitWidth;
      _explicitDocumentSize.height = documentUI.explicitHeight;
    }

    addRawChildAt(object, 0);
  }

  private const _explicitDocumentSize:Rectangle = new Rectangle();

  public function get explicitDocumentSize():Rectangle {
    return _explicitDocumentSize;
  }

  public function setActualDocumentSize(w:Number, h:Number):void {
    // первоначально устанавливалось посредством setLayoutBoundsSize, но Application без explicit size вешается на Stage и слушает resize — изменить это поведение без инжектирования байт-кода мы не можем 
    _document.width = w;
    _document.height = h;
  }

  internal function addingChild(object:DisplayObject):void {
    var uiComponent:IUIComponent = object as IUIComponent;
    if (uiComponent != null) {
      uiComponent.systemManager = this;
      if (uiComponent.document == null) {
        uiComponent.document = _document;
      }
    }

    if (object is IFlexModule && IFlexModule(object).moduleFactory == null) {
      IFlexModule(object).moduleFactory = flexModuleFactory;
    }

    // skip font context, not need for us

    if (object is ILayoutManagerClient) {
      ILayoutManagerClient(object).nestLevel = 2;
    }

		// skip doubleClickEnabled

    if (uiComponent != null) {
      uiComponent.parentChanged(this);
    }

    if (object is ISimpleStyleClient) {
      var isStyleClient:Boolean = object is IStyleClient;
      if (isStyleClient) {
        IStyleClient(object).regenerateStyleCache(true);
      }
      ISimpleStyleClient(object).styleChanged(null);
      if (isStyleClient) {
        IStyleClient(object).notifyStyleChangeInChildren(null, true);
      }

      if (object is UIComponent) {
        var ui:UIComponent = UIComponent(uiComponent);
        ui.initThemeColor();
        ui.stylesInitialized();
      }
    }
	}

  public function get preloadedRSLs():Dictionary {
    return null;
  }

  public function allowDomain(... rest):void {
  }

  public function allowInsecureDomain(... rest):void {
  }

  public function callInContext(fn:Function, thisArg:Object, argArray:Array, returns:Boolean = true):* {
    return null;
  }

  public function create(... params):Object {
    return flexModuleFactory.create(params);
  }

  public function getImplementation(interfaceName:String):Object {
    return implementations[interfaceName];
  }

  public function info():Object {
    return null;
  }

  public function registerImplementation(interfaceName:String, impl:Object):void {
    throw new Error("");
  }

  public function get embeddedFontList():Object {
    return null;
  }

  public function get focusPane():Sprite {
    return null;
  }

  public function set focusPane(value:Sprite):void {
  }

  public function get isProxy():Boolean {
    return false;
  }

  public function get numModalWindows():int {
    return 0;
  }

  public function set numModalWindows(value:int):void {
  }

  public function get rawChildren():IChildList {
    return null;
  }

  private var _screen:Rectangle;
  public function get screen():Rectangle {
    if (_screen == null) {
      _screen = new Rectangle();
    }

    _screen.width = super.parent.width;
    _screen.height = super.parent.height;
    return _screen;
  }

  public function get topLevelSystemManager():ISystemManager {
    return this;
  }

  public function getDefinitionByName(name:String):Object {
    return ApplicationDomain.currentDomain.getDefinition(name);
  }

  public function isTopLevel():Boolean {
    return true;
  }

  public function isFontFaceEmbedded(tf:TextFormat):Boolean {
    return false;
  }

  public function isTopLevelRoot():Boolean {
    return true;
  }

  public function getTopLevelRoot():DisplayObject {
    return this;
  }

  public function getSandboxRoot():DisplayObject {
    return this;
  }

  flex::v4_5
  public function getVisibleApplicationRect(bounds:Rectangle = null, skipToSandboxRoot:Boolean = false):Rectangle {
    return commonGetVisibleApplicationRect(bounds);
  }

  flex::v4_1
  public function getVisibleApplicationRect(bounds:Rectangle = null):Rectangle {
    return commonGetVisibleApplicationRect(bounds);
  }
  
  private function commonGetVisibleApplicationRect(bounds:Rectangle):Rectangle {
    if (bounds == null) {
      bounds = getBounds(stage);
    }

    return bounds;
  }

  public function deployMouseShields(deploy:Boolean):void {
  }

  public function invalidateParentSizeAndDisplayList():void {
  }

  override public function get parent():DisplayObjectContainer {
    return null;
  }

  private static var fakeTransform:Transform;

  override public function get transform():Transform {
    if (fakeTransform == null) {
      fakeTransform = new Transform(new Shape());
    }
    return fakeTransform;
  }

  private var _focusManager:FocusManager;
  public function get focusManager():IFocusManager {
    if (_focusManager == null) {
      _focusManager = new FocusManager(this);
    }
    
    return _focusManager;
  }

  public function set focusManager(value:IFocusManager):void {
  }

  public function get defaultButton():IFlexDisplayObject {
    return null;
  }

  public function set defaultButton(value:IFlexDisplayObject):void {
  }

  public function get systemManager():ISystemManager {
    return this;
  }

  flex::v4_5
  public function addPreloadedRSL(loaderInfo:LoaderInfo, rsl:Vector.<RSLData>):void {
    throw new Error("forbidden");
  }
}
}