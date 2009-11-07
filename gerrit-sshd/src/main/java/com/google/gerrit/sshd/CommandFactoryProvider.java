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

import com.google.gerrit.sshd.SshScopes.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.CommandFactory.Command;
import org.apache.sshd.server.CommandFactory.ExitCallback;
import org.apache.sshd.server.CommandFactory.SessionAware;
import org.apache.sshd.server.session.ServerSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Creates a CommandFactory using commands registered by {@link CommandModule}.
 */
class CommandFactoryProvider implements Provider<CommandFactory> {
  private final DispatchCommandProvider dispatcher;

  @Inject
  CommandFactoryProvider(
      @CommandName(Commands.ROOT) final DispatchCommandProvider d) {
    dispatcher = d;
  }

  @Override
  public CommandFactory get() {
    return new CommandFactory() {
      public Command createCommand(final String requestCommand) {
        return new Trampoline(requestCommand);
      }
    };
  }

  private class Trampoline implements Command, SessionAware {
    private final String commandLine;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback exit;
    private ServerSession session;

    Trampoline(final String cmdLine) {
      commandLine = cmdLine;
    }

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

    public void start() throws IOException {
      final Context old = SshScopes.current.get();
      try {
        SshScopes.current.set(new Context(session));
        final DispatchCommand c = dispatcher.get();
        c.setCommandLine(commandLine);
        c.setInputStream(in);
        c.setOutputStream(out);
        c.setErrorStream(err);
        c.setExitCallback(exit);
        c.start();
      } finally {
        SshScopes.current.set(old);
      }
    }
  }
}
