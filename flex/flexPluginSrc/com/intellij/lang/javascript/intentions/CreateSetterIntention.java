package com.intellij.lang.javascript.intentions;

import com.intellij.lang.javascript.generation.JavaScriptGenerateAccessorHandler;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;

public class CreateSetterIntention extends CreateAccessorIntentionBase {

  protected String getMessageKey() {
    return "intention.create.setter";
  }

  protected boolean isAvailableFor(final JSClass jsClass, final String accessorName) {
    return jsClass.findFunctionByNameAndKind(accessorName, JSFunction.FunctionKind.SETTER) == null;
  }

  protected JavaScriptGenerateAccessorHandler.GenerationMode getGenerationMode() {
    return JavaScriptGenerateAccessorHandler.GenerationMode.Setter;
  }
}
