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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.GitClientVersion;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Key;
import java.io.File;
import java.net.InetSocketAddress;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

@UseSsh
@NoHttpd
public class GitProtocolV2IT extends StandaloneSiteTest {
  private final String[] SSH_KEYGEN_CMD =
      new String[] {"ssh-keygen", "-t", "rsa", "-q", "-P", "", "-f"};
  private final String[] GIT_LS_REMOTE =
      new String[] {"git", "-c", "protocol.version=2", "ls-remote"};
  private final String GIT_SSH_COMMAND =
      "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i";

  @Test
  public void testGitWireProtocolV2WithSsh() throws Exception {
    // Minimum required git-core version that supports wire protocol v2 is 2.18.0
    GitClientVersion requiredGitVersion = new GitClientVersion(2, 18, 0);
    GitClientVersion actualGitVersion =
        new GitClientVersion(execute(ImmutableList.of("git", "version")));
    // If not found, test succeeds with assumption violation
    assume().that(actualGitVersion).isAtLeast(requiredGitVersion);

    // Create new project
    Project.NameKey project = Project.nameKey("foo");
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      gApi.projects().create(project.get());

      // Set protocol.version=2 in target repository
      execute(
          ImmutableList.of("git", "config", "protocol.version", "2"),
          sitePaths.site_path.resolve("git").resolve(project.get() + Constants.DOT_GIT).toFile());

      // Retrieve SSH host and port
      InetSocketAddress sshAddress =
          ctx.getInjector()
              .getInstance(Key.get(InetSocketAddress.class, TestSshServerAddress.class));

      String destinationTemplate =
          "ssh://%s@" + sshAddress.getHostName() + ":" + sshAddress.getPort() + "/" + project.get();

      // Prepare data for new change
      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;

      // Create new change and retrieve SHA1 for the created patch set
      String commit =
          gApi.changes()
              .id(gApi.changes().create(in).info().changeId)
              .current()
              .commit(false)
              .commit;

      // Generate private/public key for admin-user
      execute(ImmutableList.<String>builder().add(SSH_KEYGEN_CMD).add("id_rsa_admin").build());

      // Read the content of generated public key and add it for admin user in Gerrit
      gApi.accounts()
          .self()
          .addSshKey(
              new String(
                  java.nio.file.Files.readAllBytes(sitePaths.data_dir.resolve("id_rsa_admin.pub")),
                  UTF_8));

      // Read refs from target repository using git wire protocol v2 for admin user
      String out =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_LS_REMOTE)
                  .add(String.format(destinationTemplate, "admin"))
                  .build(),
              ImmutableMap.of(
                  "GIT_SSH_COMMAND",
                  GIT_SSH_COMMAND + sitePaths.data_dir.resolve("id_rsa_admin"),
                  "GIT_TRACE_PACKET",
                  "1"));

      assertGitProtocolV2Refs(commit, out);
      assertThat(out).contains("refs/meta/config");

      // Create non-admin user
      ctx.getInjector().getInstance(AccountCreator.class).user().id();

      // Generate private/public key for non-admin user
      execute(ImmutableList.<String>builder().add(SSH_KEYGEN_CMD).add("id_rsa_user").build());

      // Read the content of generated public key and add it for non-admin user in Gerrit
      gApi.accounts()
          .id("user")
          .addSshKey(
              new String(
                  java.nio.file.Files.readAllBytes(sitePaths.data_dir.resolve("id_rsa_user.pub")),
                  UTF_8));

      // Read refs from target repository using git wire protocol v2 for non-admin user
      out =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_LS_REMOTE)
                  .add(String.format(destinationTemplate, "user"))
                  .build(),
              ImmutableMap.of(
                  "GIT_SSH_COMMAND",
                  GIT_SSH_COMMAND + sitePaths.data_dir.resolve("id_rsa_user"),
                  "GIT_TRACE_PACKET",
                  "1"));

      assertGitProtocolV2Refs(commit, out);
      assertThat(out).doesNotContain("refs/meta/config");
    }
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
