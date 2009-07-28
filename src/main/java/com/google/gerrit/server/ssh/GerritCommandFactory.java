// Copyright (C) 2008 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.sshd.server.CommandFactory;

import java.util.HashMap;

/** Creates a command implementation based on the client input. */
@Singleton
public class GerritCommandFactory implements CommandFactory {
  private final Injector injector;
  private final HashMap<String, Provider<? extends AbstractCommand>> commands;

  @Inject
  GerritCommandFactory(final Injector i) {
    injector = i;
    commands = new HashMap<String, Provider<? extends AbstractCommand>>();

    bind("gerrit-upload-pack", Upload.class);
    bind("gerrit-receive-pack", Receive.class);
    bind("gerrit-flush-caches", AdminFlushCaches.class);
    bind("gerrit-ls-projects", ListProjects.class);
    bind("gerrit-show-caches", AdminShowCaches.class);
    bind("gerrit-show-connections", AdminShowConnections.class);
    bind("gerrit-show-queue", AdminShowQueue.class);
    bind("gerrit-replicate", AdminReplicate.class);

    alias("gerrit-upload-pack", "git-upload-pack");
    alias("gerrit-receive-pack", "git-receive-pack");
  }

  private void bind(final String cmd, final Class<? extends AbstractCommand> imp) {
    commands.put(cmd, injector.getProvider(imp));
  }

  private void alias(final String from, final String to) {
    commands.put(to, commands.get(from));
  }

  public Command createCommand(final String commandLine) {
    final int sp1 = commandLine.indexOf(' ');
    String cmd, args;
    if (0 < sp1) {
      cmd = commandLine.substring(0, sp1);
      args = commandLine.substring(sp1 + 1);
    } else {
      cmd = commandLine;
      args = "";
    }

    // Support newer-style "git receive-pack" requests by converting
    // to the older-style "git-receive-pack".
    //
    if ("git".equals(cmd) || "gerrit".equals(cmd)) {
      cmd += "-";
      final int sp2 = args.indexOf(' ');
      if (0 < sp2) {
        cmd += args.substring(0, sp2);
        args = args.substring(sp2 + 1);
      } else {
        cmd += args;
        args = "";
      }
    } else if ("scp".equals(cmd)) {
      return new ScpCommand(args.split(" "));
    }

    final AbstractCommand c = create(cmd);
    c.setCommandLine(cmd, args);
    return c;
  }

  private AbstractCommand create(final String cmd) {
    final Provider<? extends AbstractCommand> f = commands.get(cmd);
    if (f != null) {
      return f.get();
    }
    return new AbstractCommand() {
      @Override
      protected void run() throws Failure {
        throw new UnloggedFailure(127, "gerrit: " + getName() + ": not found");
      }
    };
  }
}
