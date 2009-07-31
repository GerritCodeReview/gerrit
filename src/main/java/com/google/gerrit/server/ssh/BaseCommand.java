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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.ssh.SshScopes.Context;

import org.apache.sshd.common.SshException;
import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.CommandFactory.ExitCallback;
import org.apache.sshd.server.CommandFactory.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public abstract class BaseCommand implements Command, SessionAware {
  private static final Logger log = LoggerFactory.getLogger(BaseCommand.class);

  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;
  protected ExitCallback exit;
  protected ServerSession session;

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

  public void setSession(final ServerSession session) {
    this.session = session;
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
    if (cmd instanceof SessionAware) {
      ((SessionAware) cmd).setSession(session);
    }
    cmd.setInputStream(in);
    cmd.setOutputStream(out);
    cmd.setErrorStream(err);
    cmd.setExitCallback(exit);
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
   * startThread(new Task() {
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
    final List<Command> activeList = session.getAttribute(SshUtil.ACTIVE);
    final Command cmd = this;
    new Thread(threadName()) {
      @Override
      public void run() {
        int rc = 0;
        try {
          synchronized (activeList) {
            activeList.add(cmd);
          }
          SshScopes.current.set(context);
          thunk.run();
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
          synchronized (activeList) {
            activeList.remove(cmd);
          }
          exit.onExit(rc);
        }
      }
    }.start();
  }

  private String threadName() {
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
        err.write((f.getMessage() + "\n").getBytes("UTF-8"));
        err.flush();
      } catch (IOException e2) {
      } catch (Throwable e2) {
        log.warn("Cannot send failure message to client", e2);
      }
      return f.exitCode;

    } else {
      try {
        err.write("fatal: internal server error\n".getBytes("UTF-8"));
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

    public Failure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    public Failure(final int exitCode, final String msg, final Throwable why) {
      super(msg, why);
      this.exitCode = exitCode;
    }
  }

  /** Thrown from {@link CommandRunnable#run()} with client message and code. */
  public static class UnloggedFailure extends Failure {
    private static final long serialVersionUID = 1L;

    public UnloggedFailure(final int exitCode, final String msg) {
      this(exitCode, msg, null);
    }

    public UnloggedFailure(final int exitCode, final String msg,
        final Throwable why) {
      super(exitCode, msg, why);
    }
  }
}
