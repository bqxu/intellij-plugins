package com.intellij.lang.javascript.generation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.flex.AnnotationBackedDescriptor;
import com.intellij.lang.javascript.flex.FlexBundle;
import com.intellij.lang.javascript.flex.ImportUtils;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecmal4.JSAttribute;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeNameValuePair;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.impl.JSChangeUtil;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.ui.JSClassChooserDialog;
import com.intellij.lang.javascript.validation.fixes.BaseCreateFix;
import com.intellij.lang.javascript.validation.fixes.BaseCreateMethodsFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;

public class JavaScriptGenerateEventHandler extends BaseJSGenerateHandler {

  private static final String EVENT_BASE_CLASS_FQN = "flash.events.Event";

  protected String getTitleKey() {
    return ""; // not used in this action
  }

  protected BaseCreateMethodsFix createFix(final JSClass jsClass) {
    return new GenerateEventHandlerFix(jsClass);
  }

  protected boolean collectCandidatesAndShowDialog() {
    return false;
  }

  protected boolean canHaveEmptySelectedElements() {
    return true;
  }


  @Nullable
  public static XmlAttribute getXmlAttribute(final PsiFile psiFile, final Editor editor) {
    PsiElement context = null;
    if (psiFile instanceof JSFile) {
      context = psiFile.getContext();
    }
    else if (psiFile instanceof XmlFile) {
      context = psiFile.findElementAt(editor.getCaretModel().getOffset());
    }

    return PsiTreeUtil.getParentOfType(context, XmlAttribute.class);
  }

  @Nullable
  public static String getEventType(final XmlAttribute xmlAttribute) {
    final XmlAttributeDescriptor descriptor = xmlAttribute == null ? null : xmlAttribute.getDescriptor();
    final PsiElement declaration = descriptor instanceof AnnotationBackedDescriptor ? descriptor.getDeclaration() : null;
    final PsiElement declarationParent = declaration == null ? null : declaration.getParent();

    if (declaration instanceof JSAttributeNameValuePair &&
        (((JSAttributeNameValuePair)declaration).getName() == null ||
         "name".equals(((JSAttributeNameValuePair)declaration).getName())) &&
        declarationParent instanceof JSAttribute &&
        "Event".equals(((JSAttribute)declarationParent).getName())) {
      return ((AnnotationBackedDescriptor)descriptor).getType();
    }

    return null;
  }

  @Nullable
  public static JSCallExpression getEventListenerCallExpression(final PsiFile psiFile, final Editor editor) {
    if (!(psiFile instanceof JSFile)) {
      return null;
    }

    final PsiElement elementAtCursor = psiFile.findElementAt(editor.getCaretModel().getOffset());
    final JSCallExpression callExpression = PsiTreeUtil.getParentOfType(elementAtCursor, JSCallExpression.class);

    if (callExpression == null || !JSResolveUtil.isEventListenerCall(callExpression)) {
      return null;
    }

    final JSArgumentList argumentList = callExpression.getArgumentList();
    final JSExpression[] params = argumentList != null ? argumentList.getArguments() : JSExpression.EMPTY_ARRAY;

    if (params.length > 0 &&
        ((params[0] instanceof JSReferenceExpression && ((JSReferenceExpression)params[0]).getQualifier() != null) ||
         (params[0] instanceof JSLiteralExpression && ((JSLiteralExpression)params[0]).isQuotedLiteral()))) {
      return callExpression;
    }

    return null;
  }

  /**
   * Trinity.first is JSExpressionStatement (if it looks like ButtonEvent.CLICK),
   * Trinity.second is event class FQN (like "flash.events.MouseEvent"),
   * Trinity.third is event name (like "click")
   */
  @Nullable
  public static Trinity<JSExpressionStatement, String, String> getEventConstantInfo(final PsiFile psiFile, final Editor editor) {
    if (!(psiFile instanceof JSFile)) {
      return null;
    }

    final JSClass jsClass = BaseJSGenerateHandler.findClass(psiFile, editor);
    if (jsClass == null || !JavaScriptGenerateAccessorHandler.isEventDispatcher(jsClass)) {
      return null;
    }

    final PsiElement elementAtCursor = psiFile.findElementAt(editor.getCaretModel().getOffset());
    final JSExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(elementAtCursor, JSExpressionStatement.class);
    final PsiElement expressionStatementParent = expressionStatement == null ? null : expressionStatement.getParent();
    final JSFunction jsFunction = PsiTreeUtil.getParentOfType(expressionStatement, JSFunction.class);

    final JSExpression expression = expressionStatement == null ? null : expressionStatement.getExpression();
    final JSReferenceExpression refExpression = expression instanceof JSReferenceExpression ? (JSReferenceExpression)expression : null;
    final JSExpression qualifier = refExpression == null ? null : refExpression.getQualifier();
    final PsiReference qualifierReference = qualifier == null ? null : qualifier.getReference();
    final PsiElement referenceNameElement = refExpression == null ? null : refExpression.getReferenceNameElement();

    JSAttributeList functionAttributes;
    if (jsFunction == null ||
        ((functionAttributes = jsFunction.getAttributeList()) != null &&
         functionAttributes.hasModifier(JSAttributeList.ModifierType.STATIC)) ||
        qualifierReference == null ||
        !(referenceNameElement instanceof LeafPsiElement) ||
        (!(expressionStatementParent instanceof JSFunction) && !(expressionStatementParent instanceof JSBlockStatement))
      ) {
      return null;
    }

    final PsiElement qualifierResolve = qualifierReference.resolve();
    if (!(qualifierResolve instanceof JSClass) || !isEventClass((JSClass)qualifierResolve)) {
      return null;
    }

    final PsiElement expressionResolve = refExpression.resolve();
    if (expressionResolve instanceof JSVariable) {
      final JSAttributeList varAttributes = ((JSVariable)expressionResolve).getAttributeList();
      final String text = ((JSVariable)expressionResolve).getInitializerText();
      if (varAttributes != null &&
          varAttributes.hasModifier(JSAttributeList.ModifierType.STATIC) &&
          varAttributes.getAccessType() == JSAttributeList.AccessType.PUBLIC &&
          StringUtil.isQuotedString(text)) {
        return Trinity.create(expressionStatement, ((JSClass)qualifierResolve).getQualifiedName(), StringUtil.stripQuotesAroundValue(text));
      }
    }

    return null;
  }

  public static boolean isEventClass(final JSClass jsClass) {
    final PsiElement eventClass = JSResolveUtil.unwrapProxy(JSResolveUtil.findClassByQName(EVENT_BASE_CLASS_FQN, jsClass));
    if (!(eventClass instanceof JSClass)) return false;
    if (JSResolveUtil.checkClassHasParentOfAnotherOne(jsClass, (JSClass)eventClass, null)) {
      return true;
    }
    return false;
  }

  private static String initializerToPartialMethodName(final String initializerText) {
    return initializerText.replace("'", "").replace("\"", "").replace(" ", "");
  }

  private static void moveCursorInsideMethod(final Editor someEditor, final PsiElement addedElement) {
    final Editor topEditor = InjectedLanguageUtil.getTopLevelEditor(someEditor);

    final PsiElement lastElement = PsiTreeUtil.getDeepestLast(addedElement);
    final PsiElement prevElement = lastElement.getPrevSibling();

    final int offset = (prevElement != null ? prevElement : lastElement).getTextOffset();
    final int offsetToNavigate = InjectedLanguageManager.getInstance(addedElement.getProject()).injectedToHost(addedElement, offset);

    final PsiFile psiFile = addedElement.getContainingFile();
    final PsiElement context = psiFile.getContext();
    final PsiFile baseFile = context == null ? psiFile : context.getContainingFile();

    BaseCreateFix.navigate(addedElement.getProject(), topEditor, offsetToNavigate, baseFile.getVirtualFile());
  }

  public static class GenerateEventHandlerFix extends BaseCreateMethodsFix {
    private boolean inMxmlEventAttributeValue;
    private boolean inEventListenerCall;
    private PsiElement handlerCallerAnchorInArgumentList;
    private boolean inEventConstantExpression;
    private JSExpressionStatement eventConstantExpression;
    private String eventHandlerName;
    private String methodBody;
    private String eventClassFqn;
    private boolean userCancelled;

    private static final String METHOD_NAME_PATTERN = "{0}_{1}Handler";
    private final JSClass myJsClass;

    public GenerateEventHandlerFix(final JSClass jsClass) {
      super(jsClass);
      myJsClass = jsClass;
      inMxmlEventAttributeValue = false;
      inEventListenerCall = false;
      handlerCallerAnchorInArgumentList = null;
      eventHandlerName = "eventHandler";
      methodBody = "";
      eventClassFqn = EVENT_BASE_CLASS_FQN;
      userCancelled = false;
    }

    // called outside of write action - required for class chooser
    public void beforeInvoke(@NotNull final Project project, final Editor editor, final PsiFile psiFile) {
      // keep consistency with CreateEventHandlerIntention.isAvailable()

      final XmlAttribute xmlAttribute = getXmlAttribute(psiFile, editor);
      final String eventType = xmlAttribute == null ? null : getEventType(xmlAttribute);
      if (eventType != null) {
        inMxmlEventAttributeValue = true;
        prepareForMxmlEventAttributeValue(xmlAttribute, eventType);
        return;
      }

      final JSCallExpression callExpression = getEventListenerCallExpression(psiFile, editor);
      if (callExpression != null) {
        inEventListenerCall = true;
        prepareForEventListenerCall(callExpression);
        return;
      }

      final Trinity<JSExpressionStatement, String, String> eventConstantInfo = getEventConstantInfo(psiFile, editor);
      if (eventConstantInfo != null) {
        inEventConstantExpression = true;
        eventConstantExpression = eventConstantInfo.first;
        eventClassFqn = eventConstantInfo.second;
        eventHandlerName = eventConstantInfo.third + "Handler";
        return;
      }

      // no suitable context -> ask for event class and create handler without usage
      final Module module = ModuleUtil.findModuleForPsiElement(psiFile);
      if (module != null && !ApplicationManager.getApplication().isUnitTestMode()) {
        final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
        final JSClassChooserDialog dialog =
          new JSClassChooserDialog(module.getProject(), FlexBundle.message("choose.event.class.title"), scope, getEventBaseClass(),
                                   new JSClassChooserDialog.PublicInheritor(module, EVENT_BASE_CLASS_FQN, false));
        if (dialog.showDialog()) {
          final JSClass selectedClass = dialog.getSelectedClass();
          if (selectedClass != null) {
            eventClassFqn = selectedClass.getQualifiedName();
          }
        }
        else {
          userCancelled = true;
        }
      }
    }

    public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
      if (userCancelled) return;
      insertEventHandlerReference(editor, file);
      evalAnchor(editor, file);
      final String eventClassShortName = StringUtil.getShortName(eventClassFqn);
      final String functionText =
        "private function " + eventHandlerName + "(event:" + eventClassShortName + "):void{" + methodBody + "\n}\n";

      final PsiElement addedElement = doAddOneMethod(project, functionText, anchor);
      moveCursorInsideMethod(editor, addedElement);
      ImportUtils.importAndShortenReference(eventClassFqn, addedElement, true, false);
    }

    private void insertEventHandlerReference(final Editor editor, final PsiFile psiFile) {
      if (inMxmlEventAttributeValue) {
        final XmlAttribute xmlAttribute = getXmlAttribute(psiFile, editor);
        if (xmlAttribute != null) {
          xmlAttribute.setValue(eventHandlerName + "(event)");
        }
      }
      else if (inEventListenerCall && handlerCallerAnchorInArgumentList != null) {
        PsiElement element =
          JSChangeUtil.createJSTreeFromText(psiFile.getProject(), eventHandlerName, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
        if (element != null) {
          handlerCallerAnchorInArgumentList.getParent().addAfter(element, handlerCallerAnchorInArgumentList);
        }

        if (handlerCallerAnchorInArgumentList.getNode().getElementType() != JSTokenTypes.COMMA) {
          final PsiElement psi = JSChangeUtil.createJSTreeFromText(psiFile.getProject(), "a,b").getPsi();
          final JSCommaExpression commaExpression = PsiTreeUtil.getChildOfType(psi, JSCommaExpression.class);
          final LeafPsiElement comma = PsiTreeUtil.getChildOfType(commaExpression, LeafPsiElement.class);
          if (comma != null && comma.getNode().getElementType() == JSTokenTypes.COMMA) {
            handlerCallerAnchorInArgumentList.getParent().addAfter(comma, handlerCallerAnchorInArgumentList);
          }
        }
      }
      else if (inEventConstantExpression) {
        final String text = "addEventListener(" + eventConstantExpression.getExpression().getText() + ", " + eventHandlerName + ");";
        final PsiElement element =
          JSChangeUtil.createJSTreeFromText(psiFile.getProject(), text, JavaScriptSupportLoader.ECMA_SCRIPT_L4).getPsi();
        if (element != null) {
          final PsiElement addedElement = eventConstantExpression.getParent().addBefore(element, eventConstantExpression);
          PsiElement sibling;
          while ((sibling = addedElement.getNextSibling()) != null && sibling != eventConstantExpression) {
            sibling.delete();
          }

          eventConstantExpression.delete();
        }
      }
    }

    @Nullable
    private JSClass getEventBaseClass() {
      final PsiElement eventClass = JSResolveUtil
        .unwrapProxy(JSResolveUtil.findClassByQName(EVENT_BASE_CLASS_FQN, myJsClass));
      if (eventClass instanceof JSClass) return (JSClass)eventClass;
      return null;
    }

    private void prepareForMxmlEventAttributeValue(final XmlAttribute xmlAttribute, final String eventType) {
      eventClassFqn = eventType;
      methodBody = xmlAttribute.getValue().trim();
      if (methodBody.length() > 0 && !methodBody.endsWith(";") && !methodBody.endsWith("}")) methodBody += ";";

      final XmlTag xmlTag = xmlAttribute.getParent();
      final String eventName = xmlAttribute.getName();
      final String id = xmlTag == null ? null : xmlTag.getAttributeValue("id");
      if (xmlTag != null && xmlTag.getParent() instanceof XmlDocument) {
        eventHandlerName = eventName + "Handler";
      }
      else if (id == null) {
        final String name = xmlTag == null ? "" : xmlTag.getLocalName();
        final String idBase = name.isEmpty() ? "" : Character.toLowerCase(name.charAt(0)) + name.substring(1);
        int i = 0;
        do {
          i++;
          eventHandlerName = MessageFormat.format(METHOD_NAME_PATTERN, idBase + i, eventName);
        }
        while (myJsClass.findFunctionByName(eventHandlerName) != null);
      }
      else {
        eventHandlerName = MessageFormat.format(METHOD_NAME_PATTERN, id, eventName);
      }
    }

    private void prepareForEventListenerCall(final JSCallExpression callExpression) {
      final JSExpression[] params = callExpression.getArgumentList().getArguments();
      String eventName = "event";

      if (params.length > 0) {
        handlerCallerAnchorInArgumentList = params[0];

        PsiElement sibling = params[0];
        while ((sibling = sibling.getNextSibling()) != null) {
          final ASTNode node = sibling.getNode();
          if (node != null && node.getElementType() == JSTokenTypes.COMMA) {
            handlerCallerAnchorInArgumentList = sibling;

            if (params.length >= 2 &&
                params[1] instanceof JSReferenceExpression) {
              final PsiElement referenceNameElement = ((JSReferenceExpression)params[1]).getReferenceNameElement();
              final ASTNode nameNode = referenceNameElement == null ? null : referenceNameElement.getNode();
              if (nameNode != null &&
                  nameNode.getElementType() == JSTokenTypes.IDENTIFIER &&
                  ((JSReferenceExpression)params[1]).resolve() == null) {
                handlerCallerAnchorInArgumentList = null;  // use current unresolved reference as event handler name
                eventHandlerName = ((JSReferenceExpression)params[1]).getReferencedName();
              }
            }

            break;
          }
        }

        if (params[0] instanceof JSReferenceExpression) {
          final JSReferenceExpression referenceExpression = (JSReferenceExpression)params[0];

          final JSExpression qualifier = referenceExpression.getQualifier();
          if (qualifier != null) {
            final PsiReference[] references = qualifier.getReferences();
            PsiElement resolveResult;
            if (references.length == 1 &&
                ((resolveResult = references[0].resolve()) instanceof JSClass) &&
                isEventClass((JSClass)resolveResult)) {
              eventClassFqn = ((JSClass)resolveResult).getQualifiedName();
            }
          }

          final PsiReference reference = referenceExpression.getReference();
          final PsiElement resolved = reference == null ? null : reference.resolve();
          if (resolved instanceof JSVariable && ((JSVariable)resolved).hasInitializer()) {
            eventName = initializerToPartialMethodName(((JSVariable)resolved).getInitializerText());
          }
        }
        else if (params[0] instanceof JSLiteralExpression) {
          eventName = initializerToPartialMethodName(params[0].getText());
        }
      }

      if (handlerCallerAnchorInArgumentList != null) {
        final JSExpression qualifier =
          ((JSReferenceExpression)((JSCallExpression)callExpression).getMethodExpression()).getQualifier();
        if (qualifier != null &&
            LanguageNamesValidation.INSTANCE.forLanguage(JavaScriptSupportLoader.JAVASCRIPT.getLanguage())
              .isIdentifier(qualifier.getText(), null)) {
          eventHandlerName = MessageFormat.format(METHOD_NAME_PATTERN, qualifier.getText(), eventName);
        }
        else {
          eventHandlerName = eventName + "Handler";
        }
      }
    }
  }
}
