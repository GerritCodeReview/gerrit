// Copyright 2008 Google Inc.
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

import org.apache.sshd.server.CommandFactory;

import java.util.HashMap;

/** Creates a command implementation based on the client input. */
class GerritCommandFactory implements CommandFactory {
  private final HashMap<String, Factory> commands;

  GerritCommandFactory() {
    commands = new HashMap<String, Factory>();

    commands.put("gerrit-upload-pack", new Factory() {
      public AbstractCommand create() {
        return new Upload();
      }
    });
    commands.put("gerrit-receive-pack", new Factory() {
      public AbstractCommand create() {
        return new Receive();
      }
    });
    commands.put("gerrit-flush-group-cache", new Factory() {
      public AbstractCommand create() {
        return new AdminFlushGroupCache();
      }
    });

    alias("gerrit-upload-pack", "git-upload-pack");
    alias("gerrit-receive-pack", "git-receive-pack");
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
    }

    final AbstractCommand c = create(cmd);
    c.parseArguments(cmd, args);
    return c;
  }

  private AbstractCommand create(final String cmd) {
    final Factory f = commands.get(cmd);
    if (f != null) {
      return f.create();
    }
    return new AbstractCommand() {
      @Override
      protected void run(final String[] argv) throws Failure {
        throw new Failure(127, "gerrit: " + getName() + ": not found");
      }
    };
  }

  protected static interface Factory {
    AbstractCommand create();
  }
}
