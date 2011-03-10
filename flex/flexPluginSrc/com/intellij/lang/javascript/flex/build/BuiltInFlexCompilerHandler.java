package com.intellij.lang.javascript.flex.build;

import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.sdk.FlexSdkUtils;
import com.intellij.lang.javascript.flex.sdk.FlexmojosSdkType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;

public class BuiltInFlexCompilerHandler {

  private static final Logger LOG = Logger.getInstance(BuiltInFlexCompilerHandler.class.getName());
  private static final String CONNECTION_SUCCESSFUL = "Connection successful";
  public static final String COMPILATION_FINISHED = "Compilation finished";

  private final Project myProject;

  private String myFlexSdkVersion;
  private ServerSocket myServerSocket;
  private DataInputStream myDataInputStream;
  private DataOutputStream myDataOutputStream;

  private int commandNumber = 1;
  private Map<String, Listener> myActiveListeners = new THashMap<String, Listener>();

  public BuiltInFlexCompilerHandler(final Project project) {
    myProject = project;
  }

  public interface Listener {
    void textAvailable(String text);

    void compilationFinished();
  }

  public synchronized void startCompilerIfNeeded(final @NotNull Sdk flexSdk, final CompileContext context) throws IOException {
    if (!Comparing.equal(flexSdk.getVersionString(), myFlexSdkVersion)) {
      stopCompilerProcess();
    }

    if (myServerSocket == null) {
      try {
        context.getProgressIndicator().setText("Starting Flex compiler");
        myServerSocket = new ServerSocket(0);
        myServerSocket.setSoTimeout(10000);
        final int port = myServerSocket.getLocalPort();

        startCompilerProcess(flexSdk, port, context);

        final Socket socket = myServerSocket.accept();
        myDataInputStream = new DataInputStream(socket.getInputStream());
        myDataOutputStream = new DataOutputStream(socket.getOutputStream());
        myFlexSdkVersion = flexSdk.getVersionString();
        scheduleInputReading();
      }
      catch (IOException e) {
        stopCompilerProcess();
        throw e;
      }
    }
  }

  private void startCompilerProcess(final Sdk flexSdk, final int port, final CompileContext context) throws IOException {
    final StringBuilder classpath = new StringBuilder();

    final String sdkVersion = flexSdk.getVersionString();
    if (!StringUtil.isEmpty(sdkVersion) &&
        StringUtil.compareVersionNumbers(sdkVersion, "3.2") >= 0 &&
        StringUtil.compareVersionNumbers(sdkVersion, "4") < 0) {

      classpath.append(FileUtil.toSystemDependentName(PathManager.getHomePath() + "/plugins/flex/lib/idea-flex-compiler-fix.jar"));
      classpath.append(File.pathSeparatorChar);
    }

    classpath.append(FileUtil.toSystemDependentName(PathManager.getHomePath() + "/plugins/flex/lib/flex-compiler.jar"));

    if (!(flexSdk.getSdkType() instanceof FlexmojosSdkType)) {
      classpath.append(File.pathSeparator).append(FileUtil.toSystemDependentName(flexSdk.getHomePath() + "/lib/flex-compiler-oem.jar"));
    }

    final List<String> commandLine =
      FlexSdkUtils.getCommandLineForSdkTool(myProject, flexSdk, classpath.toString(), "com.intellij.flex.compiler.FlexCompiler", null);
    commandLine.add(String.valueOf(port));

    final ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
    processBuilder.redirectErrorStream(true);
    processBuilder.directory(new File(FlexUtils.getFlexCompilerWorkDirPath(myProject, flexSdk)));

    final String plainCommand = StringUtil.join(processBuilder.command(), new Function<String, String>() {
      public String fun(final String s) {
        return s.contains(" ") ? "\"" + s + "\"" : s;
      }
    }, " ");
    context.addMessage(CompilerMessageCategory.INFORMATION, "Starting Flex compiler:\n" + plainCommand, null, -1, -1);

    final Process process = processBuilder.start();
    readInputStreamUntilConnected(process, context);
  }

  private void readInputStreamUntilConnected(final Process process, final CompileContext context) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        final InputStreamReader reader = new InputStreamReader(process.getInputStream());
        try {
          char[] buf = new char[1024];
          int read;
          while ((read = reader.read(buf, 0, buf.length)) >= 0) {
            final String output = new String(buf, 0, read);
            if (output.startsWith(CONNECTION_SUCCESSFUL)) {
              break;
            }
            else {
              closeSocket();
              context.addMessage(CompilerMessageCategory.ERROR, output, null, -1, -1);
            }
          }
        }
        catch (IOException e) {
          closeSocket();
          context.addMessage(CompilerMessageCategory.ERROR, "Failed to start Flex compiler: " + e.toString(), null, -1, -1);
        }
        finally {
          try {
            reader.close();
          }
          catch (IOException e) {/*ignore*/}
        }
      }
    });
  }

  private void scheduleInputReading() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        final StringBuilder buffer = new StringBuilder();
        while (true) {
          final DataInputStream dataInputStream = myDataInputStream;
          if (dataInputStream != null) {
            try {
              buffer.append(dataInputStream.readUTF());

              int index;
              while ((index = buffer.indexOf("\n")) > -1) {
                final String line = buffer.substring(0, index);
                buffer.delete(0, index + 1);
                handleInputLine(line);
              }
            }
            catch (IOException e) {
              if (dataInputStream == myDataInputStream) {
                stopCompilerProcess();
              }
              break;
            }
          }
          else {
            break;
          }
        }
      }
    });
  }

  private synchronized void handleInputLine(final String line) {
    LOG.debug("RECEIVED: [" + line + "]");

    final int colonPos = line.indexOf(":");
    if (colonPos <= 0) {
      LOG.error("Incorrect command: [" + line + "]");
      return;
    }

    final String prefix = line.substring(0, colonPos + 1);
    final Listener listener = myActiveListeners.get(prefix);
    if (listener == null) {
      LOG.warn("No active listener for input line: [" + line + "]");  // could be message from cancelled compilation
    }
    else {
      final String text = line.substring(colonPos + 1);
      if (text.startsWith(COMPILATION_FINISHED)) {
        listener.compilationFinished();
        myActiveListeners.remove(prefix);
      }
      else {
        listener.textAvailable(text);
      }
    }
  }

  public synchronized void sendCompilationCommand(final String command, final Listener listener) {
    if (myDataOutputStream == null) {
      listener.textAvailable("Error: Compiler process is not started.");
      listener.compilationFinished();
      return;
    }

    try {
      final String prefix = String.valueOf(commandNumber++) + ":";
      final String commandToSend = prefix + command + "\n";
      LOG.debug("SENDING: [" + commandToSend + "]");
      myDataOutputStream.writeUTF(commandToSend);
      myActiveListeners.put(prefix, listener);
    }
    catch (IOException e) {
      listener.textAvailable("Error: Can't start compilation: " + e.toString());
      listener.compilationFinished();
    }
  }

  private synchronized void cancelAllCompilations(final boolean reportError) {
    for (final Listener listener : myActiveListeners.values()) {
      if (reportError) {
        listener.textAvailable("Error: Compilation terminated");
      }
      listener.compilationFinished();
    }
    myActiveListeners.clear();
  }

  public void stopCompilerProcess() {
    final Runnable runnable = new Runnable() {
      public void run() {
        cancelAllCompilations(true);
        closeSocket();
      }
    };

    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      application.executeOnPooledThread(runnable);
    }
    else {
      runnable.run();
    }
  }

  private synchronized void closeSocket() {
    // compiler process exits when socket closes, so it's enough just to close streams

    if (myDataInputStream != null) {
      try {
        myDataInputStream.close();
      }
      catch (IOException ignored) {/**/}
    }

    if (myDataOutputStream != null) {
      try {
        myDataOutputStream.close();
      }
      catch (IOException ignored) {/**/}
    }

    if (myServerSocket != null) {
      try {
        myServerSocket.close();
      }
      catch (IOException ignored) {/**/}
    }

    myServerSocket = null;
    myDataInputStream = null;
    myDataOutputStream = null;
  }

  public synchronized void removeListener(final Listener listener) {
    String toRemove = null;
    for (final Map.Entry<String, Listener> entry : myActiveListeners.entrySet()) {
      if (entry.getValue() == listener) {
        toRemove = entry.getKey();
        break;
      }
    }

    if (toRemove != null) {
      myActiveListeners.remove(toRemove);
    }
  }

  public synchronized int getActiveCompilationsNumber() {
    return myActiveListeners.size();
  }
}
