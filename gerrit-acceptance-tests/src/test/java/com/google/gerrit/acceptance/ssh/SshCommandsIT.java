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
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.sshd.Commands;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NoHttpd
@UseSsh
public class SshCommandsIT extends AbstractDaemonTest {
  private static final Logger log = LoggerFactory.getLogger(SshCommandsIT.class);

  //TODO: It would be better to dynamically generate this list
  private static final Map<String, List<String>> COMMANDS =
      ImmutableMap.of(
          Commands.ROOT,
          ImmutableList.of(
              "apropos",
              "ban-commit",
              "close-connection",
              "create-account",
              "create-branch",
              "create-group",
              "create-project",
              "flush-caches",
              "gc",
              "gsql",
              "index",
              "logging",
              "ls-groups",
              "ls-members",
              "ls-projects",
              "ls-user-refs",
              "plugin",
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
              "show-caches",
              "show-connections",
              "show-queue",
              "stream-events",
              "test-submit",
              "version"),
          "index",
          ImmutableList.of("changes", "project"), // "activate" and "start" are not included
          "plugin",
          ImmutableList.of("add", "enable", "install", "ls", "reload", "remove", "rm"),
          "test-submit",
          ImmutableList.of("rule", "type"));

  @Test
  public void sshCommandCanBeExecuted() throws Exception {
    // Access Database capability is required to run the "gerrit gsql" command
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);

    for (String root : COMMANDS.keySet()) {
      for (String command : COMMANDS.get(root)) {
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
}
