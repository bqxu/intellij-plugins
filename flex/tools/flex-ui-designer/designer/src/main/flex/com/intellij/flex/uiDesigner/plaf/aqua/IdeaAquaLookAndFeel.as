package com.intellij.flex.uiDesigner.plaf.aqua {
import cocoa.plaf.LookAndFeelUtil;
import cocoa.text.TextFormat;

import com.intellij.flex.uiDesigner.plaf.CustomTextFormatId;
import com.intellij.flex.uiDesigner.plaf.IdeaLookAndFeel;

import flash.text.engine.ElementFormat;

public class IdeaAquaLookAndFeel extends IdeaLookAndFeel {
  [Embed(source="/../../../target/assets", mimeType="application/octet-stream")]
  private static var assetsDataClass:Class;
  
  private static const SIDE_PANE_GROUP_ITEM_LABEL_FONT:TextFormat = new TextFormat(new ElementFormat(fontBoldDescription, 11, 0x38393b));

  override protected function initialize():void {
    super.initialize();
    
    LookAndFeelUtil.initAssets(data, assetsDataClass);
    assetsDataClass = null;
    
    data[CustomTextFormatId.SIDE_PANE_GROUP_ITEM_LABEL] = SIDE_PANE_GROUP_ITEM_LABEL_FONT;
  }
}
}