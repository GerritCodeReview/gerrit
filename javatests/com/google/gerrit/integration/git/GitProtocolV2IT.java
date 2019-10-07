// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.integration.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.GitClientVersion;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.io.File;
import java.net.InetSocketAddress;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

@UseSsh
public class GitProtocolV2IT extends StandaloneSiteTest {
  private final String[] SSH_KEYGEN_CMD =
      new String[] {"ssh-keygen", "-t", "rsa", "-q", "-P", "", "-f"};
  private final String[] GIT_LS_REMOTE =
      new String[] {"git", "-c", "protocol.version=2", "ls-remote", "-o", "trace=12345"};
  private final String GIT_SSH_COMMAND =
      "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i";

  @Inject private GerritApi gApi;
  @Inject private AccountCreator accountCreator;
  @Inject private ProjectOperations projectOperations;
  @Inject private @TestSshServerAddress InetSocketAddress sshAddress;
  @Inject private @GerritServerConfig Config config;

  @Test
  public void testGitWireProtocolV2WithSsh() throws Exception {
    // Minimum required git-core version that supports wire protocol v2 is 2.18.0
    GitClientVersion requiredGitVersion = new GitClientVersion(2, 18, 0);
    GitClientVersion actualGitVersion =
        new GitClientVersion(execute(ImmutableList.of("git", "version")));
    // If not found, test succeeds with assumption violation
    assume().that(actualGitVersion).isAtLeast(requiredGitVersion);

    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Create project
      Project.NameKey project = Project.nameKey("foo");
      gApi.projects().create(project.get());

      // Set up project permission
      projectOperations
          .project(project)
          .forUpdate()
          .add(deny(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
          .add(
              allow(Permission.READ)
                  .ref("refs/heads/master")
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      // Set protocol.version=2 in target repository
      execute(
          ImmutableList.of("git", "config", "protocol.version", "2"),
          sitePaths.site_path.resolve("git").resolve(project.get() + Constants.DOT_GIT).toFile());

      // Retrieve HTTP url
      String url = config.getString("gerrit", null, "canonicalweburl");
      String urlDestinationTemplate =
          url.substring(0, 7)
              + "%s:secret@"
              + url.substring(7, url.length())
              + "/a/"
              + project.get();

      // Retrieve SSH host and port
      String sshDestinationTemplate =
          "ssh://%s@" + sshAddress.getHostName() + ":" + sshAddress.getPort() + "/" + project.get();

      // Admin user was already created by the base class
      setUpUserAuthentication(admin.username());

      // Create non-admin user
      TestAccount user = accountCreator.user();
      setUpUserAuthentication(user.username());

      // Prepare data for new change on master branch
      ChangeInput in = new ChangeInput(project.get(), "master", "Test public change");
      in.newBranch = true;

      // Create new change and retrieve SHA1 for the created patch set
      String commit =
          gApi.changes()
              .id(gApi.changes().create(in).info().changeId)
              .current()
              .commit(false)
              .commit;

      // Prepare new change on secret branch
      in = new ChangeInput(project.get(), "secret", "Test secret change");
      in.newBranch = true;

      // Create new change and retrieve SHA1 for the created patch set
      String secretCommit =
          gApi.changes()
              .id(gApi.changes().create(in).info().changeId)
              .current()
              .commit(false)
              .commit;

      // Read refs from target repository using git wire protocol v2 over HTTP for admin user
      String out =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_LS_REMOTE)
                  .add(String.format(urlDestinationTemplate, admin.username()))
                  .build(),
              ImmutableMap.of("GIT_TRACE_PACKET", "1"));

      assertGitProtocolV2Refs(commit, out);
      assertThat(out).contains(secretCommit);

      // Read refs from target repository using git wire protocol v2 over SSH for admin user
      out =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_LS_REMOTE)
                  .add(String.format(sshDestinationTemplate, admin.username()))
                  .build(),
              ImmutableMap.of(
                  "GIT_SSH_COMMAND",
                  GIT_SSH_COMMAND
                      + sitePaths.data_dir.resolve(String.format("id_rsa_%s", admin.username())),
                  "GIT_TRACE_PACKET",
                  "1"));

      assertGitProtocolV2Refs(commit, out);
      assertThat(out).contains(secretCommit);

      // Read refs from target repository using git wire protocol v2 over HTTP for non-admin user
      out =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_LS_REMOTE)
                  .add(String.format(urlDestinationTemplate, user.username()))
                  .build(),
              ImmutableMap.of("GIT_TRACE_PACKET", "1"));

      assertGitProtocolV2Refs(commit, out);
      assertThat(out).doesNotContain(secretCommit);

      // Read refs from target repository using git wire protocol v2 over SSH for non-admin user
      out =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_LS_REMOTE)
                  .add(String.format(sshDestinationTemplate, user.username()))
                  .build(),
              ImmutableMap.of(
                  "GIT_SSH_COMMAND",
                  GIT_SSH_COMMAND
                      + sitePaths.data_dir.resolve(String.format("id_rsa_%s", user.username())),
                  "GIT_TRACE_PACKET",
                  "1"));

      assertGitProtocolV2Refs(commit, out);
      assertThat(out).doesNotContain(secretCommit);
    }
  }

  private void setUpUserAuthentication(String username) throws Exception {
    // Assign HTTP password to user
    gApi.accounts().id(username).setHttpPassword("secret");

    // Generate private/public key for user
    execute(
        ImmutableList.<String>builder()
            .add(SSH_KEYGEN_CMD)
            .add(String.format("id_rsa_%s", username))
            .build());

    // Read the content of generated public key and add it for the user in Gerrit
    gApi.accounts()
        .id(username)
        .addSshKey(
            new String(
                java.nio.file.Files.readAllBytes(
                    sitePaths.data_dir.resolve(String.format("id_rsa_%s.pub", username))),
                UTF_8));
  }

  private static void assertGitProtocolV2Refs(String commit, String out) {
    assertThat(out).contains("git< version 2");
    assertThat(out).contains("refs/changes/01/1/1");
    assertThat(out).contains("refs/changes/01/1/meta");
    assertThat(out).contains(commit);
  }

  private String execute(ImmutableList<String> cmd) throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), ImmutableMap.of());
  }

  private String execute(ImmutableList<String> cmd, ImmutableMap<String, String> env)
      throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), env);
  }

  private static String execute(ImmutableList<String> cmd, File dir) throws Exception {
    return execute(cmd, dir, ImmutableMap.of());
  }
}
