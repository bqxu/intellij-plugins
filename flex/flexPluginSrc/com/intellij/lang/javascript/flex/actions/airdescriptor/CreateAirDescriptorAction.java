package com.intellij.lang.javascript.flex.actions.airdescriptor;

import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.lang.javascript.flex.FlexBundle;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class CreateAirDescriptorAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final CreateAirDescriptorDialog dialog = new CreateAirDescriptorDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      try {
        final VirtualFile descriptorFile = createAirDescriptor(dialog.getAirDescriptorParameters());

        final ToolWindowManager manager = ToolWindowManager.getInstance(project);
        manager.notifyByBalloon(ToolWindowId.PROJECT_VIEW, MessageType.INFO,
                                FlexBundle.message("file.created", descriptorFile.getName()), null,
                                new HyperlinkListener() {
                                  public void hyperlinkUpdate(HyperlinkEvent e) {
                                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && descriptorFile.isValid()) {
                                      FileEditorManager.getInstance(project)
                                        .openTextEditor(new OpenFileDescriptor(project, descriptorFile), true);
                                    }
                                  }
                                });
      }
      catch (IOException ex) {
        Messages.showErrorDialog(project, FlexBundle.message("air.descriptor.creation.failed", ex.getMessage()),
                                 FlexBundle.message("error.title"));
      }
    }
  }


  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ModuleManager.getInstance(project).getModules().length > 0);
  }


  public static VirtualFile createAirDescriptor(final AirDescriptorParameters parameters) throws IOException {
    final Ref<IOException> exceptionRef = new Ref<IOException>();

    final VirtualFile file = ApplicationManager.getApplication().runWriteAction(new NullableComputable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          final String template =
            StringUtil.compareVersionNumbers(parameters.getAirVersion(), "2.5") >= 0 ? "air-2.5.xml.ft" : "air-1.0.xml.ft";
          final InputStream stream = CreateAirDescriptorAction.class.getResourceAsStream(template);
          assert stream != null;
          // noinspection IOResourceOpenedButNotSafelyClosed
          final String airDescriptorContentTemplate = FileUtil.loadTextAndClose(new InputStreamReader(stream));
          final Properties attributes = new Properties();
          attributes.setProperty("air_version", parameters.getAirVersion());
          attributes.setProperty("id", parameters.getApplicationId());
          attributes.setProperty("filename", parameters.getApplicationFileName());
          attributes.setProperty("name", parameters.getApplicationName());
          attributes.setProperty("version", parameters.getApplicationVersion());
          attributes.setProperty("content", parameters.getApplicationContent());
          attributes.setProperty("title", parameters.getApplicationTitle());
          attributes.setProperty("visible", "true");
          attributes.setProperty("width", String.valueOf(parameters.getApplicationWidth()));
          attributes.setProperty("height", String.valueOf(parameters.getApplicationHeight()));

          final String airDescriptorContent = FileTemplateUtil.mergeTemplate(attributes, airDescriptorContentTemplate);
          final VirtualFile descriptorFolder = VfsUtil.createDirectories(parameters.getDescriptorFolderPath());
          return FlexUtils.addFileWithContent(parameters.getDescriptorFileName(), airDescriptorContent, descriptorFolder);
        }
        catch (IOException e) {
          exceptionRef.set(e);
          return null;
        }
      }
    });

    if (!exceptionRef.isNull()) {
      throw exceptionRef.get();
    }

    return file;
  }

}
