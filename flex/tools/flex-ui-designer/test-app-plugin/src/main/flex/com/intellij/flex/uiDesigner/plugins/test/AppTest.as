package com.intellij.flex.uiDesigner.plugins.test {
import com.intellij.flex.uiDesigner.ProjectManager;

import flash.desktop.NativeApplication;

import flash.display.NativeWindow;

import flash.events.Event;

import org.hamcrest.assertThat;
import org.hamcrest.collection.arrayWithSize;
import org.hamcrest.object.nullValue;

public class AppTest extends BaseTestCase {

  public function AppTest() {
    // disable unused inspection
    //noinspection ConstantIfStatementJS
    if (false) {
      close();
    }
  }

  override public function setUp(projectManager:ProjectManager):void {
    this.projectManager = projectManager;
  }
  
  [Test(async)]
  public function close():void {
    assertThat(projectManager.project, nullValue());
    
    var openedWindows:Array = NativeApplication.nativeApplication.openedWindows;
    // может не успеть закрыться
    if (openedWindows.length == 1) {
      assertThat(NativeApplication.nativeApplication.activeWindow, nullValue());
      _asyncSuccessHandler();
    }
    else {
      assertThat(openedWindows, arrayWithSize(2));
      NativeWindow(openedWindows[1]).addEventListener(Event.CLOSE, function (event:Event):void {
      assertThat(NativeApplication.nativeApplication.activeWindow, nullValue());
      assertThat(NativeApplication.nativeApplication.openedWindows, arrayWithSize(1));

      asyncSuccess(event, arguments.callee);
    });
    }
  }
}
}