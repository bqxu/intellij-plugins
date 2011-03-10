package com.jetbrains.actionscript.profiler;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.run.FlexRunConfiguration;
import com.intellij.lang.javascript.flex.run.FlexRunner;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.SystemProperties;
import com.jetbrains.profiler.DefaultProfilerExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * User: Maxim
 * Date: 13.07.2010
 * Time: 13:32:04
 */
public class ActionScriptProfileRunner implements ProgramRunner<ProfileSettings> {
  public static final String PROFILE = "Profile";
  private static final String PRELOAD_SWF_OPTION = "PreloadSwf";
  private final FlexRunner myFlexRunner = new FlexRunner();
  static final char DELIMITER = '=';
  private static boolean disableProfilerUnloading = false;

  @NotNull
  public String getRunnerId() {
    return PROFILE;
  }

  public boolean canRun(@NotNull String executorId, @NotNull RunProfile runProfile) {
    return executorId.equals(DefaultProfilerExecutor.EXECUTOR_ID) && runProfile instanceof FlexRunConfiguration;
  }

  public ProfileSettings createConfigurationData(ConfigurationInfoProvider configurationInfoProvider) {
    return new ProfileSettings();
  }

  public void checkConfiguration(RunnerSettings runnerSettings, ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws RuntimeConfigurationException {
    myFlexRunner.checkConfiguration(runnerSettings, configurationPerRunnerSettings);
  }

  public void onProcessStarted(RunnerSettings runnerSettings, ExecutionResult executionResult) {
    myFlexRunner.onProcessStarted(runnerSettings, executionResult);
  }

  public AnAction[] createActions(ExecutionResult executionResult) {
    return myFlexRunner.createActions(executionResult);
  }

  public SettingsEditor<ProfileSettings> getSettingsEditor(Executor executor, RunConfiguration runConfiguration) {
    return new ActionScriptProfileSettings();
  }

  public void execute(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
    final RunnerSettings runnerSettings = executionEnvironment.getRunnerSettings();
    if (runnerSettings == null) {
      return; // TODO: what does this mean?
    }
    startProfiling(
      (FlexRunConfiguration)executionEnvironment.getRunProfile(), 
      (ProfileSettings) runnerSettings.getData()
    );
    Executor executorById = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    myFlexRunner.execute(executorById, executionEnvironment);
  }

  private void startProfiling(FlexRunConfiguration state, ProfileSettings profileSettings) {
    String s = ActionScriptProfileProvider.ACTIONSCRIPT_SNAPSHOT;
    FileEditorManager editorManager = FileEditorManager.getInstance(state.getProject());
    ActionScriptProfileView profileView;

    for(FileEditor fe: editorManager.getAllEditors()) {
      if (fe instanceof ActionScriptProfileView &&
        (profileView = (ActionScriptProfileView)fe).getFile().getName().equals(s)) {
        profileView.disposeNonguiResources();
        editorManager.closeFile(profileView.getFile());
      }
    }

    Module[] modules = state.getModules();
    Sdk sdk = modules.length > 0 ?
      FlexUtils.getFlexSdkForFlexModuleOrItsFlexFacets(modules[0]):
      IdeaFacade.getInstance().getProjectSdk(ProjectRootManager.getInstance(state.getProject()));
    initProfilingAgent(sdk, profileSettings.getHost(), profileSettings.getPort());

    VirtualFile virtualFile = new LightVirtualFile(s, "");
    virtualFile.putUserData(
      ActionScriptProfileView.ourProfilingManagerKey,
      new ProfilingManager(profileSettings.getPort())
    );
    OpenFileDescriptor openFileDescriptor = new OpenFileDescriptor(state.getProject(), virtualFile);
    openFileDescriptor.navigate(true);
  }

  public static void initProfilingAgent(Sdk sdk, String host, int port) {
    try {
      String agentName = detectSuitableAgentNameForSdkUsedToLaunch(sdk);

      URL resource = ActionScriptProfileRunner.class.getResource("/" + agentName);
      File agentFile = null;
      if (resource != null) {
        agentFile = new File(transformEncodedSymbols(resource));
      } else {
        resource = ActionScriptProfileRunner.class.getResource("ActionScriptProfileRunner.class");
        if ("jar".equals(resource.getProtocol())) {
          String filePath = resource.getFile();
          filePath = filePath.substring(0, filePath.indexOf("!/"));

          // skip file:
          filePath = transformEncodedSymbols(new URL(filePath));
          agentFile = new File(new File(filePath).getParentFile().getPath() + File.separator + agentName);
        }
      }

      assert agentFile != null && agentFile.exists():"Have not found "+agentName;
      String pathToAgent = agentFile.getAbsolutePath();

      pathToAgent +="?port=" + port + "&host=" + host;
      ensureFlashPlayerBlessOurProfilerSwf(pathToAgent);
      begFlashPlayerToPreloadProfilerSwf(pathToAgent);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private static String transformEncodedSymbols(URL url) throws MalformedURLException {
    String filePath;
    try { // care of encoded spaces
      filePath = url.toURI().getSchemeSpecificPart();
    } catch (URISyntaxException ex) {
      filePath = url.getPath();
    }
    return filePath;
  }

  private static String detectSuitableAgentNameForSdkUsedToLaunch(Sdk sdk) {
    String agentName = "profiler_agent";
    if (sdk == null || FlexSdkUtils.isFlex4Sdk(sdk)) agentName += "_4";
    else agentName += "_3";
    agentName += ".swf";
    return agentName;
  }

  private static void begFlashPlayerToPreloadProfilerSwf(final String pathToAgent) throws IOException {
    processMmCfg(new ProfilerPathMmCfgFixer() {
      public String additionalOptions(String lineEnd) {
        Logging.log("Added profiler swf reference to mm.cfg");
        return PRELOAD_SWF_OPTION+"="+pathToAgent;
      }
    });
  }

  public static void setDisableProfilerUnloading(boolean disableProfilerUnloading) {
    ActionScriptProfileRunner.disableProfilerUnloading = disableProfilerUnloading;
  }

  static void removePreloadingOfProfilerSwf() {
    if (disableProfilerUnloading) return;

    try {
      processMmCfg(new ProfilerPathMmCfgFixer() {
        public String additionalOptions(String lineEnd) {
          Logging.log("Removed profiler swf reference from mm.cfg");
          return null;
        }
      });
    } catch (IOException e) {
      Logging.log(e);
    }
  }


  interface MmCfgFixer {
    String processOption(String option, String value, String line);
    String additionalOptions(String lineEnd);
  }

  static abstract class ProfilerPathMmCfgFixer implements MmCfgFixer {
    public String processOption(String option, String value, String line) {
      if (!PRELOAD_SWF_OPTION.equals(option)) return line;
      return null;
    }
  }

  public static void processMmCfg(MmCfgFixer mmCfgFixer) throws IOException {
    StringBuilder mmCfgContent = new StringBuilder();
    String mmCfgPath = SystemProperties.getUserHome() + "/mm.cfg";
    final File file = new File(mmCfgPath);
    String lineEnd = System.getProperty("line.separator");

    if (file.exists()) {
      final LineNumberReader lineNumberReader = new LineNumberReader(new InputStreamReader(new BufferedInputStream(new FileInputStream(file))));

      while(true) {
        final String s = lineNumberReader.readLine();
        if (s == null) break;
        final int i = s.indexOf(DELIMITER);
        if (i != -1) {
          String value = s.substring(i + 1);
          String name = s.substring(0, i);
          final String result = mmCfgFixer.processOption(name, value, s);
          if (result != null) mmCfgContent.append(result).append(lineEnd);
        } else {
          mmCfgContent.append(s).append(lineEnd); // TODO
        }
      }
    }

    final String options = mmCfgFixer.additionalOptions(lineEnd);
    if (options != null) mmCfgContent.insert(0, options + lineEnd);

    FileUtil.writeToFile(file, mmCfgContent.toString().getBytes());
  }

  private static void ensureFlashPlayerBlessOurProfilerSwf(String pathToAgent) throws IOException {
    String base = SystemProperties.getUserHome() + File.separator;
    String path = "";
    String last = "ij.cfg";

    if (SystemInfo.isWindows) {
      path = "Application Data/Macromedia/Flash Player";
    } else if (SystemInfo.isMac) {
      path = "Library/Preferences/Macromedia/Flash Player";
    } else {
      path = ".macromedia/Flash_Player";
    }
    path += "/#Security/FlashPlayerTrust";
    File f = new File( base + path + File.separator + last );

    if (!f.exists() || !new String(FileUtil.loadFileBytes(f)).equals(pathToAgent)) { // adding file://
      FileUtil.writeToFile(f, pathToAgent.getBytes());
    }
  }

  public void execute(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment, @Nullable Callback callback) throws ExecutionException {
    final RunnerSettings runnerSettings = executionEnvironment.getRunnerSettings();
    if (runnerSettings == null) {
      return; // TODO: what does this mean ?
    }

    Executor executorById = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    myFlexRunner.execute(executorById, executionEnvironment, callback);
    RunProfileState state = executionEnvironment.getState(executorById);
    startProfiling(
      (FlexRunConfiguration)state,
      (ProfileSettings) runnerSettings.getData()
    );
  }
}
