// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.sshd;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Atomics;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.DynamicOptions;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.git.ProjectRunnable;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.EndOfOptionsHandler;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.channel.exception.SshChannelClosedException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

public abstract class BaseCommand implements Command {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final Charset ENC = UTF_8;

  private static final int PRIVATE_STATUS = 1 << 30;
  static final int STATUS_CANCEL = PRIVATE_STATUS | 1;
  static final int STATUS_NOT_FOUND = PRIVATE_STATUS | 2;
  public static final int STATUS_NOT_ADMIN = PRIVATE_STATUS | 3;

  @SuppressWarnings("unused") // unused here, but triggers logic in EndOfOptionsHandler
  @Option(name = "--", usage = "end of options", handler = EndOfOptionsHandler.class)
  private boolean endOfOptions;

  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;

  protected ExitCallback exit;

  @Inject protected CurrentUser user;

  @Inject private SshScope sshScope;

  @Inject private CmdLineParser.Factory cmdLineParserFactory;

  @Inject protected RequestCleanup cleanup;

  @Inject @CommandExecutor private ScheduledThreadPoolExecutor executor;

  @Inject private PermissionBackend permissionBackend;

  @Inject private SshScope.Context context;

  /** Commands declared by a plugin can be scoped by the plugin name. */
  @Inject(optional = true)
  @PluginName
  private String pluginName;

  @Inject protected Injector injector;

  @Inject protected DynamicMap<DynamicOptions.DynamicBean> dynamicBeans;

  /** The task, as scheduled on a worker thread. */
  private final AtomicReference<Future<?>> task;

  /** Text of the command line which lead up to invoking this instance. */
  private String commandName = "";

  /** Unparsed command line options. */
  private String[] argv;

  /** trimmed command line arguments. */
  private String[] trimmedArgv;

  public BaseCommand() {
    task = Atomics.newReference();
  }

  @Override
  public void setInputStream(InputStream in) {
    this.in = in;
  }

  @Override
  public void setOutputStream(OutputStream out) {
    this.out = out;
  }

  @Override
  public void setErrorStream(OutputStream err) {
    this.err = err;
  }

  @Override
  public void setExitCallback(ExitCallback callback) {
    this.exit = callback;
  }

  @Nullable
  protected String getPluginName() {
    return pluginName;
  }

  protected String getName() {
    return commandName;
  }

  void setName(String prefix) {
    this.commandName = prefix;
  }

  public String[] getArguments() {
    return argv;
  }

  public void setArguments(String[] argv) {
    this.argv = argv;
  }

  /**
   * Trim the argument if it is spanning multiple lines.
   *
   * @return the arguments where all the multiple-line fields are trimmed.
   */
  protected String[] getTrimmedArguments() {
    if (trimmedArgv == null && argv != null) {
      trimmedArgv = new String[argv.length];
      for (int i = 0; i < argv.length; i++) {
        String arg = argv[i];
        int indexOfMultiLine = arg.indexOf("\n");
        if (indexOfMultiLine > -1) {
          arg = arg.substring(0, indexOfMultiLine) + " [trimmed]";
        }
        trimmedArgv[i] = arg;
      }
    }
    return trimmedArgv;
  }

  @Override
  public void destroy(ChannelSession channel) {
    Future<?> future = task.getAndSet(null);
    if (future != null && !future.isDone()) {
      future.cancel(true);
    }
  }

  /**
   * Pass all state into the command, then run its start method.
   *
   * <p>This method copies all critical state, like the input and output streams, into the supplied
   * command. The caller must still invoke {@code cmd.start()} if wants to pass control to the
   * command.
   *
   * @param cmd the command that will receive the current state.
   */
  protected void provideStateTo(Command cmd) {
    cmd.setInputStream(in);
    cmd.setOutputStream(out);
    cmd.setErrorStream(err);
    cmd.setExitCallback(exit);
  }

  /**
   * Parses the command line argument, injecting parsed values into fields.
   *
   * <p>This method must be explicitly invoked to cause a parse.
   *
   * @param pluginOptions which helps to define and parse options provided from plugins
   * @throws UnloggedFailure if the command line arguments were invalid.
   * @see Option
   * @see Argument
   */
  protected void parseCommandLine(DynamicOptions pluginOptions) throws UnloggedFailure {
    parseCommandLine(this, pluginOptions);
  }

  /**
   * Parses the command line argument, injecting parsed values into fields.
   *
   * <p>This method must be explicitly invoked to cause a parse.
   *
   * @param options object whose fields declare Option and Argument annotations to describe the
   *     parameters of the command. Usually {@code this}.
   * @param pluginOptions which helps to define and parse options provided from plugins
   * @throws UnloggedFailure if the command line arguments were invalid.
   * @see Option
   * @see Argument
   */
  protected void parseCommandLine(Object options, DynamicOptions pluginOptions)
      throws UnloggedFailure {
    final CmdLineParser clp = newCmdLineParser(options);
    pluginOptions.setBean(options);
    pluginOptions.startLifecycleListeners();
    pluginOptions.parseDynamicBeans(clp);
    pluginOptions.setDynamicBeans();
    pluginOptions.onBeanParseStart();
    try {
      clp.parseArgument(argv);
    } catch (IllegalArgumentException | CmdLineException err) {
      if (!clp.wasHelpRequestedByOption()) {
        throw new UnloggedFailure(1, "fatal: " + err.getMessage());
      }
    }

    if (clp.wasHelpRequestedByOption()) {
      StringWriter msg = new StringWriter();
      clp.printDetailedUsage(commandName, msg);
      msg.write(usage());
      throw new UnloggedFailure(1, msg.toString());
    }
    pluginOptions.onBeanParseEnd();
  }

  protected String usage() {
    return "";
  }

  /** Construct a new parser for this command's received command line. */
  protected CmdLineParser newCmdLineParser(Object options) {
    return cmdLineParserFactory.create(options);
  }

  /**
   * Spawn a function into its own thread.
   *
   * <p>Typically this should be invoked within {@link Command#start(ChannelSession, Environment)},
   * such as:
   *
   * <pre>
   * startThread(new CommandRunnable() {
   *   public void run() throws Exception {
   *     runImp();
   *   }
   * },
   * accessPath);
   * </pre>
   *
   * <p>If the function throws an exception, it is translated to a simple message for the client, a
   * non-zero exit code, and the stack trace is logged.
   *
   * @param thunk the runnable to execute on the thread, performing the command's logic.
   * @param accessPath the path used by the end user for running the SSH command
   */
  protected void startThread(final CommandRunnable thunk, AccessPath accessPath) {
    final TaskThunk tt = new TaskThunk(thunk, accessPath);

    if (isAdminHighPriorityCommand()) {
      // Admin commands should not block the main work threads (there
      // might be an interactive shell there), nor should they wait
      // for the main work threads.
      //
      new Thread(tt, tt.toString()).start();
    } else {
      task.set(executor.submit(tt));
    }
  }

  private boolean isAdminHighPriorityCommand() {
    if (getClass().getAnnotation(AdminHighPriorityCommand.class) != null) {
      try {
        permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
        return true;
      } catch (AuthException | PermissionBackendException e) {
        return false;
      }
    }
    return false;
  }

  /**
   * Terminate this command and return a result code to the remote client.
   *
   * <p>Commands should invoke this at most once. Once invoked, the command may lose access to
   * request based resources as any callbacks previously registered with {@link RequestCleanup} will
   * fire.
   *
   * @param rc exit code for the remote client.
   */
  protected void onExit(int rc) {
    exit.onExit(rc);
    if (cleanup != null) {
      cleanup.run();
    }
  }

  /** Wrap the supplied output stream in a UTF-8 encoded PrintWriter. */
  protected static PrintWriter toPrintWriter(OutputStream o) {
    return new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, ENC)));
  }

  private int handleError(Throwable e) {
    if ((e.getClass() == IOException.class && "Pipe closed".equals(e.getMessage()))
        || //
        (e.getClass() == SshException.class && "Already closed".equals(e.getMessage()))
        || //
        e.getClass() == InterruptedIOException.class) {
      // This is sshd telling us the client just dropped off while
      // we were waiting for a read or a write to complete. Either
      // way its not really a fatal error. Don't log it.
      //
      return 127;
    }

    if (!(e instanceof UnloggedFailure)) {
      final StringBuilder m = new StringBuilder();
      m.append("Internal server error");
      if (user.isIdentifiedUser()) {
        final IdentifiedUser u = user.asIdentifiedUser();
        m.append(" (user ");
        m.append(u.getUserName().orElse(null));
        m.append(" account ");
        m.append(u.getAccountId());
        m.append(")");
      }
      m.append(" during ");
      m.append(context.getCommandLine());
      logCauseIfRelevant(e, m);
    }

    if (e instanceof Failure) {
      final Failure f = (Failure) e;
      try {
        err.write((f.getMessage() + "\n").getBytes(ENC));
        err.flush();
      } catch (IOException e2) {
        // Ignored
      } catch (RuntimeException e2) {
        logger.atWarning().withCause(e2).log("Cannot send failure message to client");
      }
      return f.exitCode;
    }

    try {
      err.write("fatal: internal server error\n".getBytes(ENC));
      err.flush();
    } catch (IOException e2) {
      // Ignored
    } catch (RuntimeException e2) {
      logger.atWarning().withCause(e2).log("Cannot send internal server error message to client");
    }
    return 128;
  }

  private void logCauseIfRelevant(Throwable e, StringBuilder message) {
    String zeroLength = "length=0";
    String streamAlreadyClosed = "stream is already closed";
    boolean isZeroLength = false;

    if (streamAlreadyClosed.equals(e.getMessage())) {
      StackTraceElement[] stackTrace = e.getStackTrace();
      isZeroLength = Arrays.stream(stackTrace).anyMatch(s -> s.toString().contains(zeroLength));
    }
    if (!isZeroLength) {
      logger.atSevere().withCause(e).log("%s", message);
    }
  }

  protected UnloggedFailure die(String msg) {
    return new UnloggedFailure(1, "fatal: " + msg);
  }

  protected UnloggedFailure die(String msg, Throwable why) {
    return new UnloggedFailure(1, "fatal: " + msg, why);
  }

  protected UnloggedFailure die(Throwable why) {
    return new UnloggedFailure(1, "fatal: " + why.getMessage(), why);
  }

  protected void writeError(String type, String msg) {
    try {
      err.write((type + ": " + msg + "\n").getBytes(ENC));
    } catch (IOException e) {
      // Ignored
    }
  }

  protected void enableGracefulStop() {
    context.getSession().setGracefulStop(true);
  }

  protected String getTaskDescription() {
    String[] ta = getTrimmedArguments();
    if (ta != null) {
      return commandName + " " + Joiner.on(" ").join(ta);
    }
    return commandName;
  }

  private String getTaskName() {
    StringBuilder m = new StringBuilder();
    m.append(getTaskDescription());
    if (user.isIdentifiedUser()) {
      IdentifiedUser u = user.asIdentifiedUser();
      if (u.getUserName().isPresent()) {
        m.append(" (").append(u.getUserName().get()).append(")");
      }
    }
    return m.toString();
  }

  private final class TaskThunk implements CancelableRunnable, ProjectRunnable {
    private final CommandRunnable thunk;
    private final String taskName;
    private final AccessPath accessPath;

    private Project.NameKey projectName;

    private TaskThunk(final CommandRunnable thunk, AccessPath accessPath) {
      this.thunk = thunk;
      this.taskName = getTaskName();
      this.accessPath = accessPath;
    }

    @Override
    public void cancel() {
      synchronized (this) {
        final Context old = sshScope.set(context);
        try {
          onExit(STATUS_CANCEL);
        } finally {
          sshScope.set(old);
        }
      }
    }

    @Override
    public void run() {
      synchronized (this) {
        final Thread thisThread = Thread.currentThread();
        final String thisName = thisThread.getName();
        int rc = 0;
        context.getSession().setAccessPath(accessPath);
        final Context old = sshScope.set(context);
        try {
          context.start();
          thisThread.setName("SSH " + taskName);

          try {
            if (thunk instanceof ProjectCommandRunnable) {
              try (DynamicOptions pluginOptions = new DynamicOptions(injector, dynamicBeans)) {
                ((ProjectCommandRunnable) thunk).executeParseCommand(pluginOptions);
                projectName = ((ProjectCommandRunnable) thunk).getProjectName();
                thunk.run();
              }
            } else {
              thunk.run();
            }
          } catch (NoSuchProjectException e) {
            throw new UnloggedFailure(1, e.getMessage());
          } catch (NoSuchChangeException e) {
            throw new UnloggedFailure(1, e.getMessage() + " no such change");
          }

          flushIgnoreSCCE(out);
          flushIgnoreSCCE(err);
        } catch (Throwable e) {
          flushIgnoreException(out);
          flushIgnoreException(err);
          rc = handleError(e);
        } finally {
          try {
            onExit(rc);
          } finally {
            sshScope.set(old);
            thisThread.setName(thisName);
          }
        }
      }
    }

    private void flushIgnoreSCCE(OutputStream os) throws IOException {
      try {
        os.flush();
      } catch (SshChannelClosedException e) {
        // Ignore - command implementation flushed stream already
      }
    }

    private void flushIgnoreException(OutputStream os) {
      try {
        os.flush();
      } catch (Exception e) {
        // Ignore
      }
    }

    @Override
    public String toString() {
      return taskName;
    }

    @Override
    public Project.NameKey getProjectNameKey() {
      return projectName;
    }

    @Override
    @Nullable
    public String getRemoteName() {
      return null;
    }

    @Override
    public boolean hasCustomizedPrint() {
      return false;
    }
  }

  /** Runnable function which can throw an exception. */
  @FunctionalInterface
  public interface CommandRunnable {
    void run() throws Exception;
  }

  /** Runnable function which can retrieve a project name related to the task */
  public interface ProjectCommandRunnable extends CommandRunnable {
    // execute parser command before running, in order to be able to retrieve
    // project name
    void executeParseCommand(DynamicOptions pluginOptions) throws Exception;

    Project.NameKey getProjectName();
  }

  /** Thrown from {@link CommandRunnable#run()} with client message and code. */
  public static class Failure extends Exception {
    private static final long serialVersionUID = 1L;

    final int exitCode;

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the failure status of this
     *     command. Should be between 1 and 255, inclusive.
     * @param msg message to also send to the client's stderr.
     */
    public Failure(int exitCode, String msg) {
      this(exitCode, msg, null);
    }

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the failure status of this
     *     command. Should be between 1 and 255, inclusive.
     * @param msg message to also send to the client's stderr.
     * @param why stack trace to include in the server's log, but is not sent to the client's
     *     stderr.
     */
    public Failure(int exitCode, String msg, Throwable why) {
      super(msg, why);
      this.exitCode = exitCode;
    }
  }

  /** Thrown from {@link CommandRunnable#run()} with client message and code. */
  public static class UnloggedFailure extends Failure {
    private static final long serialVersionUID = 1L;

    /**
     * Create a new failure.
     *
     * @param msg message to also send to the client's stderr.
     */
    public UnloggedFailure(String msg) {
      this(1, msg);
    }

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the failure status of this
     *     command. Should be between 1 and 255, inclusive.
     * @param msg message to also send to the client's stderr.
     */
    public UnloggedFailure(int exitCode, String msg) {
      this(exitCode, msg, null);
    }

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the failure status of this
     *     command. Should be between 1 and 255, inclusive.
     * @param msg message to also send to the client's stderr.
     * @param why stack trace to include in the server's log, but is not sent to the client's
     *     stderr.
     */
    public UnloggedFailure(int exitCode, String msg, Throwable why) {
      super(exitCode, msg, why);
    }
  }
}
