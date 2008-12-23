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

/** Creates a command implementation based on the client input. */
class GerritCommandFactory implements CommandFactory {
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
    return new AbstractCommand() {
      @Override
      protected void run(final String[] argv) throws Failure {
        throw new Failure(127, "gerrit: " + getName() + ": not found");
      }
    };
  }
}
