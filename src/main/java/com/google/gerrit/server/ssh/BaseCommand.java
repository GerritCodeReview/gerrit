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

import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.CommandFactory.ExitCallback;
import org.apache.sshd.server.CommandFactory.SessionAware;
import org.apache.sshd.server.session.ServerSession;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class BaseCommand implements Command, SessionAware {
  protected InputStream in;
  protected OutputStream out;
  protected OutputStream err;
  protected ExitCallback exit;
  protected ServerSession session;

  /** Text of the command line which lead up to invoking this instance. */
  protected String commandPrefix = "";

  /** Unparsed rest of the command line. */
  protected String commandLine;

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

  @Override
  public String toString() {
    if (commandPrefix.isEmpty())
      return commandLine;
    else
      return commandPrefix + " " + commandLine;
  }
}
