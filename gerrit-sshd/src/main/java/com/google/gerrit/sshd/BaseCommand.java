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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.sshd.SshScopes.Context;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.inject.Inject;

import org.apache.sshd.common.SshException;
import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.CommandFactory.ExitCallback;
import org.apache.sshd.server.session.ServerSession;
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
import java.util.ArrayList;
import java.util.List;

public abstract class BaseCommand implements Command {
  private static final Logger log = LoggerFactory.getLogger(BaseCommand.class);
  public static final String ENC = "UTF-8";

  @Option(name = "--help", usage = "display this help text", aliases = {"-h"})
  private boolean help;

  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;

  private ExitCallback exit;

  @Inject
  private CmdLineParser.Factory cmdLineParserFactory;

  @Inject
  private RequestCleanup cleanup;

  /** Text of the command line which lead up to invoking this instance. */
  protected String commandPrefix = "";

  /** Unparsed rest of the command line. */
  protected String commandLine = "";

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

  public void setCommandPrefix(final String prefix) {
    this.commandPrefix = prefix;
  }

  /**
   * Set the command line to be evaluated by this command.
   * <p>
   * If this command is being invoked from a higher level
   * {@link DispatchCommand} then only the portion after the command name (that
   * is, the arguments) is supplied.
   *
   * @param line the command line received from the client.
   */
  public void setCommandLine(final String line) {
    this.commandLine = line;
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
   * This method must be explicitly invoked to cause a parse. When parsing,
   * arguments are split out of and read from the {@link #commandLine} field.
   *
   * @throws Failure if the command line arguments were invalid.
   * @see Option
   * @see Argument
   */
  protected void parseCommandLine() throws Failure {
    final List<String> list = new ArrayList<String>();
    boolean inquote = false;
    StringBuilder r = new StringBuilder();
    for (int ip = 0; ip < commandLine.length();) {
      final char b = commandLine.charAt(ip++);
      switch (b) {
        case '\t':
        case ' ':
          if (inquote)
            r.append(b);
          else if (r.length() > 0) {
            list.add(r.toString());
            r = new StringBuilder();
          }
          continue;
        case '\'':
          inquote = !inquote;
          continue;
        case '\\':
          if (inquote || ip == commandLine.length())
            r.append(b); // literal within a quote
          else
            r.append(commandLine.charAt(ip++));
          continue;
        default:
          r.append(b);
          continue;
      }
    }
    if (r.length() > 0) {
      list.add(r.toString());
    }

    final CmdLineParser clp = newCmdLineParser();
    try {
      clp.parseArgument(list.toArray(new String[list.size()]));
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
      msg.write(commandPrefix);
      clp.printSingleLineUsage(msg, null);
      msg.write('\n');

      msg.write('\n');
      clp.printUsage(msg, null);
      msg.write('\n');
      throw new UnloggedFailure(1, msg.toString());
    }
  }

  /** Construct a new parser for this command's received command line. */
  protected CmdLineParser newCmdLineParser() {
    return cmdLineParserFactory.create(this);
  }

  /**
   * Spawn a function into its own thread.
   * <p>
   * Typically this should be invoked within {@link Command#start()}, such as:
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
   * Typically this should be invoked within {@link Command#start()}, such as:
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
  protected void startThread(final CommandRunnable thunk) {
    final Context context = SshScopes.getContext();
    final List<Command> active = context.session.getAttribute(SshUtil.ACTIVE);
    final Command cmd = this;
    new Thread(threadName()) {
      @Override
      public void run() {
        int rc = 0;
        try {
          synchronized (active) {
            active.add(cmd);
          }
          SshScopes.current.set(context);
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
          synchronized (active) {
            active.remove(cmd);
          }
          onExit(rc);
        }
      }
    }.start();
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

  private String threadName() {
    final ServerSession session = SshScopes.getContext().session;
    final String who = session.getUsername();
    final Account.Id id = session.getAttribute(SshUtil.CURRENT_ACCOUNT);
    return "SSH " + getFullCommandLine() + " / " + who + " " + id;
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
      final ServerSession session = SshScopes.getContext().session;
      final StringBuilder m = new StringBuilder();
      m.append("Internal server error (");
      m.append("user ");
      m.append(session.getUsername());
      m.append(" account ");
      m.append(session.getAttribute(SshUtil.CURRENT_ACCOUNT));
      m.append(") during ");
      m.append(getFullCommandLine());
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

  @Override
  public String toString() {
    return getFullCommandLine();
  }

  private String getFullCommandLine() {
    if (commandPrefix.isEmpty())
      return commandLine;
    else if (commandLine.isEmpty())
      return commandPrefix;
    else
      return commandPrefix + " " + commandLine;
  }

  /** Runnable function which can throw an exception. */
  public static interface CommandRunnable {
    public void run() throws Exception;
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
