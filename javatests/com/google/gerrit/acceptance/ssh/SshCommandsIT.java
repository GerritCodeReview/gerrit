// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.sshd.Commands;
import com.jcraft.jsch.JSchException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoHttpd
@UseSsh
public class SshCommandsIT extends AbstractDaemonTest {
  private static final Logger log = LoggerFactory.getLogger(SshCommandsIT.class);

  // TODO: It would be better to dynamically generate these lists
  private static final List<String> COMMON_ROOT_COMMANDS =
      ImmutableList.of(
          "apropos",
          "close-connection",
          "flush-caches",
          "gc",
          "logging",
          "ls-groups",
          "ls-members",
          "ls-projects",
          "ls-user-refs",
          "plugin",
          "show-caches",
          "show-connections",
          "show-queue",
          "version");

  private static final List<String> MASTER_ONLY_ROOT_COMMANDS =
      ImmutableList.of(
          "ban-commit",
          "create-account",
          "create-branch",
          "create-group",
          "create-project",
          "gsql",
          "index",
          "query",
          "receive-pack",
          "rename-group",
          "review",
          "set-account",
          "set-head",
          "set-members",
          "set-project",
          "set-project-parent",
          "set-reviewers",
          "stream-events",
          "test-submit");

  private static final Map<String, List<String>> MASTER_COMMANDS =
      ImmutableMap.of(
          Commands.ROOT,
          ImmutableList.copyOf(
              new ArrayList<String>() {
                private static final long serialVersionUID = 1L;

                {
                  addAll(COMMON_ROOT_COMMANDS);
                  addAll(MASTER_ONLY_ROOT_COMMANDS);
                  Collections.sort(this);
                }
              }),
          "index",
          ImmutableList.of("changes", "project"), // "activate" and "start" are not included
          "logging",
          ImmutableList.of("ls", "set"),
          "plugin",
          ImmutableList.of("add", "enable", "install", "ls", "reload", "remove", "rm"),
          "test-submit",
          ImmutableList.of("rule", "type"));

  private static final Map<String, List<String>> SLAVE_COMMANDS =
      ImmutableMap.of(
          Commands.ROOT,
          COMMON_ROOT_COMMANDS,
          "plugin",
          ImmutableList.of("add", "enable", "install", "ls", "reload", "remove", "rm"));

  @Test
  @Sandboxed
  public void sshCommandCanBeExecuted() throws Exception {
    // Access Database capability is required to run the "gerrit gsql" command
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    testCommandExecution(MASTER_COMMANDS);

    restartAsSlave();
    testCommandExecution(SLAVE_COMMANDS);
  }

  private void testCommandExecution(Map<String, List<String>> commands)
      throws JSchException, IOException {
    for (String root : commands.keySet()) {
      for (String command : commands.get(root)) {
        // We can't assert that adminSshSession.hasError() is false, because using the --help
        // option causes the usage info to be written to stderr. Instead, we assert on the
        // content of the stderr, which will always start with "gerrit command" when the --help
        // option is used.
        String cmd = String.format("gerrit%s%s %s", root.isEmpty() ? "" : " ", root, command);
        log.debug(cmd);
        adminSshSession.exec(String.format("%s --help", cmd));
        String response = adminSshSession.getError();
        assertWithMessage(String.format("command %s failed: %s", command, response))
            .that(response)
            .startsWith(cmd);
      }
    }
  }

  @Test
  public void nonExistingCommandFails() throws Exception {
    adminSshSession.exec("gerrit non-existing-command --help");
    assertThat(adminSshSession.getError())
        .startsWith("fatal: gerrit: non-existing-command: not found");
  }

  @Test
  @Sandboxed
  public void listCommands() throws Exception {
    adminSshSession.exec("gerrit --help");
    List<String> commands = parseCommandsFromGerritHelpText(adminSshSession.getError());
    assertThat(commands).containsExactlyElementsIn(MASTER_COMMANDS.get(Commands.ROOT)).inOrder();

    restartAsSlave();
    adminSshSession.exec("gerrit --help");
    commands = parseCommandsFromGerritHelpText(adminSshSession.getError());
    assertThat(commands).containsExactlyElementsIn(SLAVE_COMMANDS.get(Commands.ROOT)).inOrder();
  }

  private List<String> parseCommandsFromGerritHelpText(String helpText) {
    List<String> commands = new ArrayList<>();

    String[] lines = helpText.split("\\n");

    // Skip all lines including the line starting with "Available commands"
    int row = 0;
    do {
      row++;
    } while (row < lines.length && !lines[row - 1].startsWith("Available commands"));

    // Skip all empty lines
    while (lines[row].trim().isEmpty()) {
      row++;
    }

    // Parse commands from all lines that are indented (start with a space)
    while (row < lines.length && lines[row].startsWith(" ")) {
      String line = lines[row].trim();
      // Abort on empty line
      if (line.isEmpty()) {
        break;
      }

      // Cut off command description if there is one
      int endOfCommand = line.indexOf(' ');
      commands.add(endOfCommand > 0 ? line.substring(0, line.indexOf(' ')) : line);
      row++;
    }

    return commands;
  }
}
