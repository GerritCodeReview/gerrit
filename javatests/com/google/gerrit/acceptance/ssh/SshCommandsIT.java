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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@NoHttpd
@UseSsh
public class SshCommandsIT extends AbstractDaemonTest {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // TODO: It would be better to dynamically generate these lists
  private static final ImmutableList<String> COMMON_ROOT_COMMANDS =
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
          "reload-config",
          "show-caches",
          "show-connections",
          "show-queue",
          "version");

  private static final ImmutableList<String> MASTER_ONLY_ROOT_COMMANDS =
      ImmutableList.of(
          "ban-commit",
          "create-account",
          "create-branch",
          "create-group",
          "create-project",
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
          "set-topic",
          "stream-events",
          "test-submit");

  private static final ImmutableList<String> EMPTY = ImmutableList.of();
  private static final ImmutableMap<String, List<String>> MASTER_COMMANDS =
      ImmutableMap.<String, List<String>>builder()
          .put("kill", EMPTY)
          .put("ps", EMPTY)
          // TODO(dpursehouse): Add "scp" and "suexec"
          .put(
              "gerrit",
              Streams.concat(COMMON_ROOT_COMMANDS.stream(), MASTER_ONLY_ROOT_COMMANDS.stream())
                  .sorted()
                  .collect(toImmutableList()))
          .put(
              "gerrit index",
              ImmutableList.of(
                  "changes", "changes-in-project")) // "activate" and "start" are not included
          .put("gerrit logging", ImmutableList.of("ls", "set"))
          .put(
              "gerrit plugin",
              ImmutableList.of("add", "enable", "install", "ls", "reload", "remove", "rm"))
          .put("gerrit test-submit", ImmutableList.of("rule", "type"))
          .build();

  private static final ImmutableMap<String, List<String>> SLAVE_COMMANDS =
      ImmutableMap.of(
          "kill",
          EMPTY,
          "gerrit",
          COMMON_ROOT_COMMANDS,
          "gerrit plugin",
          ImmutableList.of("add", "enable", "install", "ls", "reload", "remove", "rm"));

  @Test
  @Sandboxed
  public void sshCommandCanBeExecuted() throws Exception {
    testCommandExecution(MASTER_COMMANDS);

    restartAsSlave();
    testCommandExecution(SLAVE_COMMANDS);
  }

  private void testCommandExecution(Map<String, List<String>> commands) throws Exception {
    for (String root : commands.keySet()) {
      List<String> cmds = commands.get(root);
      if (cmds.isEmpty()) {
        testCommandExecution(root);
      } else {
        for (String cmd : cmds) {
          testCommandExecution(String.format("%s %s", root, cmd));
        }
      }
    }
  }

  private void testCommandExecution(String cmd) throws Exception {
    // We can't assert that adminSshSession.hasError() is false, because using the --help
    // option causes the usage info to be written to stderr. Instead, we assert on the
    // content of the stderr, which will always start with "gerrit command" when the --help
    // option is used.
    logger.atFine().log(cmd);
    adminSshSession.exec(String.format("%s --help", cmd));
    String response = adminSshSession.getError();
    assertWithMessage(String.format("command %s failed: %s", cmd, response))
        .that(response)
        .startsWith(cmd);
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
    assertThat(commands).containsExactlyElementsIn(MASTER_COMMANDS.get("gerrit")).inOrder();

    restartAsSlave();
    adminSshSession.exec("gerrit --help");
    commands = parseCommandsFromGerritHelpText(adminSshSession.getError());
    assertThat(commands).containsExactlyElementsIn(SLAVE_COMMANDS.get("gerrit")).inOrder();
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
