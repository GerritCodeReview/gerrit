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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.git.ProjectRunnable;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.CancelableRunnable;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gerrit.util.cli.EndOfOptionsHandler;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.common.SshException;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Future;

public abstract class BaseCommand implements Command {
  private static final Logger log = LoggerFactory.getLogger(BaseCommand.class);
  public static final String ENC = "UTF-8";

  private static final int PRIVATE_STATUS = 1 << 30;
  static final int STATUS_CANCEL = PRIVATE_STATUS | 1;
  static final int STATUS_NOT_FOUND = PRIVATE_STATUS | 2;
  static final int STATUS_NOT_ADMIN = PRIVATE_STATUS | 3;

  @Option(name = "--help", usage = "display this help text", aliases = {"-h"})
  private boolean help;

  @SuppressWarnings("unused")
  @Option(name = "--", usage = "end of options", handler = EndOfOptionsHandler.class)
  private boolean endOfOptions;

  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;

  private ExitCallback exit;

  @Inject
  private CmdLineParser.Factory cmdLineParserFactory;

  @Inject
  private RequestCleanup cleanup;

  @Inject
  @CommandExecutor
  private WorkQueue.Executor executor;

  @Inject
  private Provider<CurrentUser> userProvider;

  @Inject
  private Provider<SshScope.Context> contextProvider;

  /** The task, as scheduled on a worker thread. */
  private Future<?> task;

  /** Text of the command line which lead up to invoking this instance. */
  private String commandName = "";

  /** Unparsed command line options. */
  private String[] argv;

  public void setInputStream(final InputStream in) {
    this.in = in;
  }

  public void setOutputStream(final OutputStream out) {
    this.out = out;
  }

  public void setErrorStream(final OutputStream err) {
    this.err = err;
  }

  public void setExitCallback(final ExitCallback callback) {
    this.exit = callback;
  }

  void setName(final String prefix) {
    this.commandName = prefix;
  }

  public void setArguments(final String[] argv) {
    this.argv = argv;
  }

  @Override
  public void destroy() {
    if (task != null && !task.isDone()) {
      task.cancel(true);
    }
  }

  /**
   * Pass all state into the command, then run its start method.
   * <p>
   * This method copies all critical state, like the input and output streams,
   * into the supplied command. The caller must still invoke {@code cmd.start()}
   * if wants to pass control to the command.
   *
   * @param cmd the command that will receive the current state.
   */
  protected void provideStateTo(final Command cmd) {
    cmd.setInputStream(in);
    cmd.setOutputStream(out);
    cmd.setErrorStream(err);
    cmd.setExitCallback(exit);
  }

  /**
   * Parses the command line argument, injecting parsed values into fields.
   * <p>
   * This method must be explicitly invoked to cause a parse.
   *
   * @throws UnloggedFailure if the command line arguments were invalid.
   * @see Option
   * @see Argument
   */
  protected void parseCommandLine() throws UnloggedFailure {
    final CmdLineParser clp = newCmdLineParser();
    try {
      clp.parseArgument(argv);
    } catch (IllegalArgumentException err) {
      if (!help) {
        throw new UnloggedFailure(1, "fatal: " + err.getMessage());
      }
    } catch (CmdLineException err) {
      if (!help) {
        throw new UnloggedFailure(1, "fatal: " + err.getMessage());
      }
    }

    if (help) {
      final StringWriter msg = new StringWriter();
      msg.write(commandName);
      clp.printSingleLineUsage(msg, null);
      msg.write('\n');

      msg.write('\n');
      clp.printUsage(msg, null);
      msg.write('\n');
      msg.write(usage());
      throw new UnloggedFailure(1, msg.toString());
    }
  }

  protected String usage() {
    return "";
  }

  /** Construct a new parser for this command's received command line. */
  protected CmdLineParser newCmdLineParser() {
    return cmdLineParserFactory.create(this);
  }

  /**
   * Spawn a function into its own thread.
   * <p>
   * Typically this should be invoked within {@link Command#start(Environment)},
   * such as:
   *
   * <pre>
   * startThread(new Runnable() {
   *   public void run() {
   *     runImp();
   *   }
   * });
   * </pre>
   *
   * @param thunk the runnable to execute on the thread, performing the
   *        command's logic.
   */
  protected void startThread(final Runnable thunk) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        thunk.run();
      }
    });
  }

  /**
   * Spawn a function into its own thread.
   * <p>
   * Typically this should be invoked within {@link Command#start(Environment)},
   * such as:
   *
   * <pre>
   * startThread(new CommandRunnable() {
   *   public void run() throws Exception {
   *     runImp();
   *   }
   * });
   * </pre>
   * <p>
   * If the function throws an exception, it is translated to a simple message
   * for the client, a non-zero exit code, and the stack trace is logged.
   *
   * @param thunk the runnable to execute on the thread, performing the
   *        command's logic.
   */
  protected synchronized void startThread(final CommandRunnable thunk) {
    final TaskThunk tt = new TaskThunk(thunk);

    if (isAdminCommand()||(isAdminHighPriorityCommand() && userProvider.get().isAdministrator())) {
      // Admin commands should not block the main work threads (there
      // might be an interactive shell there), nor should they wait
      // for the main work threads.
      //
      new Thread(tt, tt.toString()).start();
    } else {
      task = executor.submit(tt);
    }
  }

  private final boolean isAdminCommand() {
    return getClass().getAnnotation(AdminCommand.class) != null;
  }

  private final boolean isAdminHighPriorityCommand() {
    return getClass().getAnnotation(AdminHighPriorityCommand.class) != null;
  }

  /**
   * Terminate this command and return a result code to the remote client.
   * <p>
   * Commands should invoke this at most once. Once invoked, the command may
   * lose access to request based resources as any callbacks previously
   * registered with {@link RequestCleanup} will fire.
   *
   * @param rc exit code for the remote client.
   */
  protected void onExit(final int rc) {
    exit.onExit(rc);
    cleanup.run();
  }

  /** Wrap the supplied output stream in a UTF-8 encoded PrintWriter. */
  protected static PrintWriter toPrintWriter(final OutputStream o) {
    try {
      return new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, ENC)));
    } catch (UnsupportedEncodingException e) {
      // Our default encoding is required by the specifications for the
      // runtime APIs, this should never, ever happen.
      //
      throw new RuntimeException("JVM lacks " + ENC + " encoding", e);
    }
  }

  private int handleError(final Throwable e) {
    if (e.getClass() == IOException.class
        && "Pipe closed".equals(e.getMessage())) {
      // This is sshd telling us the client just dropped off while
      // we were waiting for a read or a write to complete. Either
      // way its not really a fatal error. Don't log it.
      //
      return 127;
    }

    if (e.getClass() == SshException.class
        && "Already closed".equals(e.getMessage())) {
      // This is sshd telling us the client just dropped off while
      // we were waiting for a read or a write to complete. Either
      // way its not really a fatal error. Don't log it.
      //
      return 127;
    }

    if (e instanceof UnloggedFailure) {
    } else {
      final StringBuilder m = new StringBuilder();
      m.append("Internal server error");
      if (userProvider.get() instanceof IdentifiedUser) {
        final IdentifiedUser u = (IdentifiedUser) userProvider.get();
        m.append(" (user ");
        m.append(u.getAccount().getUserName());
        m.append(" account ");
        m.append(u.getAccountId());
        m.append(")");
      }
      m.append(" during ");
      m.append(contextProvider.get().getCommandLine());
      log.error(m.toString(), e);
    }

    if (e instanceof Failure) {
      final Failure f = (Failure) e;
      try {
        err.write((f.getMessage() + "\n").getBytes(ENC));
        err.flush();
      } catch (IOException e2) {
      } catch (Throwable e2) {
        log.warn("Cannot send failure message to client", e2);
      }
      return f.exitCode;

    } else {
      try {
        err.write("fatal: internal server error\n".getBytes(ENC));
        err.flush();
      } catch (IOException e2) {
      } catch (Throwable e2) {
        log.warn("Cannot send internal server error message to client", e2);
      }
      return 128;
    }
  }

  protected UnloggedFailure die(String msg) {
    return new UnloggedFailure(1, "fatal: " + msg);
  }

  protected UnloggedFailure die(Throwable why) {
    return new UnloggedFailure(1, "fatal: " + why.getMessage(), why);
  }

  private final class TaskThunk implements CancelableRunnable, ProjectRunnable {
    private final CommandRunnable thunk;
    private final Context context;
    private final String taskName;
    private Project.NameKey projectName;

    private TaskThunk(final CommandRunnable thunk) {
      this.thunk = thunk;
      this.context = contextProvider.get();

      StringBuilder m = new StringBuilder();
      m.append(context.getCommandLine());
      if (userProvider.get() instanceof IdentifiedUser) {
        IdentifiedUser u = (IdentifiedUser) userProvider.get();
        m.append(" (" + u.getAccount().getUserName() + ")");
      }
      this.taskName = m.toString();
    }

    @Override
    public void cancel() {
      final Context old = SshScope.set(context);
      try {
        onExit(STATUS_CANCEL);
      } finally {
        SshScope.set(old);
      }
    }

    @Override
    public void run() {
      final Thread thisThread = Thread.currentThread();
      final String thisName = thisThread.getName();
      int rc = 0;
      final Context old = SshScope.set(context);
      try {
        context.started = System.currentTimeMillis();
        thisThread.setName("SSH " + taskName);

        if (thunk instanceof ProjectCommandRunnable) {
          ((ProjectCommandRunnable) thunk).executeParseCommand();
          projectName = ((ProjectCommandRunnable) thunk).getProjectName();
        }

        try {
          thunk.run();
        } catch (NoSuchProjectException e) {
          throw new UnloggedFailure(1, e.getMessage() + " no such project");
        } catch (NoSuchChangeException e) {
          throw new UnloggedFailure(1, e.getMessage() + " no such change");
        }

        out.flush();
        err.flush();
      } catch (Throwable e) {
        try {
          out.flush();
        } catch (Throwable e2) {
        }
        try {
          err.flush();
        } catch (Throwable e2) {
        }
        rc = handleError(e);
      } finally {
        try {
          onExit(rc);
        } finally {
          SshScope.set(old);
          thisThread.setName(thisName);
        }
      }
    }

    @Override
    public String toString() {
      return taskName;
    }

    @Override
    public NameKey getProjectNameKey() {
      return projectName;
    }

    @Override
    public String getRemoteName() {
      return null;
    }

    @Override
    public boolean hasCustomizedPrint() {
      return false;
    }
  }

  /** Runnable function which can throw an exception. */
  public static interface CommandRunnable {
    public void run() throws Exception;
  }

  /** Runnable function which can retrieve a project name related to the task */
  public static interface ProjectCommandRunnable extends CommandRunnable {
    // execute parser command before running, in order to be able to retrieve
    // project name
    public void executeParseCommand() throws Exception;

    public Project.NameKey getProjectName();
  }

  /** Thrown from {@link CommandRunnable#run()} with client message and code. */
  public static class Failure extends Exception {
    private static final long serialVersionUID = 1L;

    final int exitCode;

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the
     *        failure status of this command. Should be between 1 and 255,
     *        inclusive.
     * @param msg message to also send to the client's stderr.
     */
    public Failure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the
     *        failure status of this command. Should be between 1 and 255,
     *        inclusive.
     * @param msg message to also send to the client's stderr.
     * @param why stack trace to include in the server's log, but is not sent to
     *        the client's stderr.
     */
    public Failure(final int exitCode, final String msg, final Throwable why) {
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
     * @param exitCode exit code to return the client, which indicates the
     *        failure status of this command. Should be between 1 and 255,
     *        inclusive.
     * @param msg message to also send to the client's stderr.
     */
    public UnloggedFailure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    /**
     * Create a new failure.
     *
     * @param exitCode exit code to return the client, which indicates the
     *        failure status of this command. Should be between 1 and 255,
     *        inclusive.
     * @param msg message to also send to the client's stderr.
     * @param why stack trace to include in the server's log, but is not sent to
     *        the client's stderr.
     */
    public UnloggedFailure(final int exitCode, final String msg,
        final Throwable why) {
      super(exitCode, msg, why);
    }
  }
}
