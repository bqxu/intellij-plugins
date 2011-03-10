package com.intellij.javascript.flex.refactoring.moveClass;

import com.intellij.ide.util.PlatformPackageUtil;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.javascript.JSBundle;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.flex.FlexBundle;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.lang.javascript.psi.resolve.JSNamedElementKind;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.refactoring.ui.JSReferenceEditor;
import com.intellij.lang.javascript.refactoring.util.JSRefactoringUtil;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public class FlexMoveClassDialog extends RefactoringDialog {

  private static final Logger LOG = Logger.getInstance(FlexMoveClassDialog.class.getName());

  private JLabel myElementLabel;
  private JSReferenceEditor myTargetPackageField;
  private JPanel myContentPane;
  private NonFocusableCheckBox myCbSearchInComments;
  private NonFocusableCheckBox myCbSearchTextOccurences;
  private NonFocusableCheckBox myCbMoveToAnotherSourceFolder;
  private JLabel myPackageLabel;
  private JTextField myClassNameField;
  private JLabel myClassNameLabel;
  private final Project myProject;
  private final Collection<JSQualifiedNamedElement> myElements;
  @Nullable private final PsiElement myTargetContainer;
  @Nullable private final MoveCallback myCallback;
  private final boolean myFileLocal;

  public FlexMoveClassDialog(Project project,
                             Collection<JSQualifiedNamedElement> elements,
                             @Nullable PsiElement targetContainer,
                             @Nullable MoveCallback callback) {
    super(project, true);
    myProject = project;
    myElements = elements;
    myTargetContainer = targetContainer;
    myCallback = callback;

    final JSQualifiedNamedElement firstElement = myElements.iterator().next();
    myFileLocal = JSResolveUtil.isFileLocalSymbol(firstElement);

    setSize(500, 130);

    String labelText;
    if (myFileLocal) {
      LOG.assertTrue(myElements.size() == 1);
      myClassNameLabel.setVisible(true);
      myClassNameLabel.setText(
        FlexBundle.message("element.name", JSBundle.message(JSNamedElementKind.kind(firstElement).humanReadableKey())));
      myClassNameField.setVisible(true);
      myClassNameField.setText(myElements.iterator().next().getName());
      myClassNameField.selectAll();
      myPackageLabel.setText(FlexBundle.message("package.name.title"));
      setTitle(RefactoringBundle.message("move.inner.to.upper.level.title"));
    }
    else {
      myClassNameLabel.setVisible(false);
      myClassNameField.setVisible(false);
      myPackageLabel.setText(FlexBundle.message("to.package.title"));
      setTitle(RefactoringBundle.message("move.title"));
    }
    myElementLabel.setLabelFor(myTargetPackageField.getChildComponent());

    if (elements.size() == 1) {
      labelText = FlexBundle.message(myFileLocal ? "move.file.local.0" : "move.0",
                                     StringUtil.decapitalize(JSBundle.message(JSNamedElementKind.kind(firstElement).humanReadableKey())),
                                     firstElement.getQualifiedName());
    }
    else {
      labelText = FlexBundle.message("move.elements");
    }
    myElementLabel.setText(labelText);
    myPackageLabel.setLabelFor(myTargetPackageField.getChildComponent());
    init();

    boolean canMoveToDifferentSourceRoot = !myFileLocal && ProjectRootManager.getInstance(myProject).getContentSourceRoots().length > 1;
    if (canMoveToDifferentSourceRoot) {
      myCbMoveToAnotherSourceFolder.setEnabled(true);
    }
    else {
      myCbMoveToAnotherSourceFolder.setEnabled(false);
      myCbMoveToAnotherSourceFolder.setSelected(false);
    }
  }

  private void createUIComponents() {
    String initialPackage;
    if (myTargetContainer instanceof PsiDirectoryContainer) {
      initialPackage = DirectoryIndex.getInstance(myProject)
        .getPackageName(((PsiDirectoryContainer)myTargetContainer).getDirectories()[0].getVirtualFile());
    }
    else if (myTargetContainer instanceof PsiDirectory) {
      initialPackage = DirectoryIndex.getInstance(myProject)
        .getPackageName(((PsiDirectory)myTargetContainer).getVirtualFile());
    }
    else {
      if (myFileLocal) {
        initialPackage = StringUtil.getPackageName(myElements.iterator().next().getQualifiedName());
      }
      else {
        initialPackage =
          JSResolveUtil.getExpectedPackageNameFromFile(myElements.iterator().next().getContainingFile().getVirtualFile(), myProject);
      }
    }
    myTargetPackageField =
      JSReferenceEditor.forPackageName(initialPackage, myProject, FlexMoveClassDialog.class.getName() + ".target_package",
                                       GlobalSearchScope.projectScope(myProject),
                                       RefactoringBundle.message("choose.destination.package"));
  }

  @Override
  protected JComponent createCenterPanel() {
    return myContentPane;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myClassNameField.isVisible() ? myClassNameField : myTargetPackageField.getChildComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return FlexMoveClassDialog.class.getName();
  }

  @Override
  protected void doAction() {
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(JavaScriptSupportLoader.JAVASCRIPT.getLanguage());

    if (myFileLocal) {
      final String className = myClassNameField.getText();
      if (StringUtil.isEmpty(className)) {
        setErrorText(FlexBundle.message("element.name.empty",
                                        JSBundle.message(JSNamedElementKind.kind(myElements.iterator().next()).humanReadableKey())));
        return;
      }
      if (!namesValidator.isIdentifier(className, myProject)) {
        setErrorText(FlexBundle.message("invalid.element.name", StringUtil
          .decapitalize(JSBundle.message(JSNamedElementKind.kind(myElements.iterator().next()).humanReadableKey())), className));
        return;
      }
    }

    final String packageName = myTargetPackageField.getText();
    for (final String s : StringUtil.split(packageName, ".")) {
      if (!namesValidator.isIdentifier(s, myProject)) {
        setErrorText(FlexBundle.message("invalid.package", packageName));
        return;
      }
    }
    myTargetPackageField.updateRecents();

    PsiElement firstElement = myElements.iterator().next();
    PsiDirectory baseDir;
    if (myTargetContainer instanceof PsiDirectory) {
      baseDir = (PsiDirectory)myTargetContainer;
    }
    else {
      baseDir = PlatformPackageUtil.getDirectory(firstElement);
    }

    String nameToCheck = myFileLocal ? myClassNameField.getText() : null;
    PsiDirectory targetDirectory =
      JSRefactoringUtil.chooseOrCreateDirectoryForClass(myProject, ModuleUtil.findModuleForPsiElement(firstElement),
                                                        GlobalSearchScope.projectScope(myProject), packageName,
                                                        nameToCheck, baseDir,
                                                        myCbMoveToAnotherSourceFolder.isSelected() ? ThreeState.YES : ThreeState.NO);
    if (targetDirectory == null) {
      return;
    }

    // file-local case already checked by JSRefactoringUtil.chooseOrCreateDirectoryForClass (see nameToCheck)
    if (!myFileLocal) {
      PsiManager manager = PsiManager.getInstance(myProject);
      try {
        for (PsiElement element : myElements) {
          manager.checkMove(element.getContainingFile(), targetDirectory);
        }
      }
      catch (IncorrectOperationException e) {
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), e.getMessage(), getHelpId(), myProject);
        return;
      }
    }
    BaseRefactoringProcessor processor;
    if (myFileLocal) {
      processor = new FlexMoveInnerClassProcessor(myElements.iterator().next(), targetDirectory, myClassNameField.getText(),
                                                  myTargetPackageField.getText(), myCbSearchInComments.isSelected(),
                                                  myCbSearchTextOccurences.isSelected(), myCallback);
    }
    else {
      processor = new FlexMoveClassProcessor(myElements, targetDirectory, myTargetPackageField.getText(), myCbSearchInComments.isSelected(),
                                             myCbSearchTextOccurences.isSelected(), myCallback);
    }
    invokeRefactoring(processor);
  }
}
