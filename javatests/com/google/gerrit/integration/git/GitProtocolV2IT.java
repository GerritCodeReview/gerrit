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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.GitClientVersion;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.testsuite.change.IndexOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.BeforeClass;
import org.junit.Test;

@UseSsh
public class GitProtocolV2IT extends StandaloneSiteTest {
  private static final String ADMIN_PASSWORD = "secret";
  private final String[] SSH_KEYGEN_CMD =
      new String[] {"ssh-keygen", "-t", "rsa", "-q", "-P", "", "-f"};
  private final String[] GIT_LS_REMOTE =
      new String[] {"git", "-c", "protocol.version=2", "ls-remote", "-o", "trace=12345"};
  private final String[] GIT_CLONE_MIRROR =
      new String[] {"git", "-c", "protocol.version=2", "clone", "--mirror"};
  private final String[] GIT_FETCH = new String[] {"git", "-c", "protocol.version=2", "fetch"};
  private final String[] GIT_INIT = new String[] {"git", "init"};
  private final String GIT_SSH_COMMAND =
      "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -i";

  @Inject private GerritApi gApi;
  @Inject private AccountCreator accountCreator;
  @Inject private ProjectOperations projectOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private @TestSshServerAddress InetSocketAddress sshAddress;
  @Inject private @GerritServerConfig Config config;
  @Inject private AllProjectsName allProjectsName;
  @Inject private IndexOperations.Change changeIndexOperations;

  @BeforeClass
  public static void assertGitClientVersion() throws Exception {
    // Minimum required git-core version that supports wire protocol v2 is 2.18.0
    GitClientVersion requiredGitVersion = new GitClientVersion(2, 18, 0);
    GitClientVersion actualGitVersion =
        new GitClientVersion(execute(ImmutableList.of("git", "version"), new File("/")));
    // If git client version cannot be updated, consider to skip this tests. Due to
    // an existing issue in bazel, JUnit assumption violation feature cannot be used.
    assertThat(actualGitVersion).isAtLeast(requiredGitVersion);
  }

  @Test
  public void testGitWireProtocolV2WithSsh() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Create project
      Project.NameKey project = Project.nameKey("foo");
      gApi.projects().create(project.get());

      // Clear all permissions for anonymous users. Allow registered users to fetch/push.
      AccountGroup.UUID admins = groupOperations.newGroup().addMember(admin.id()).create();
      projectOperations
          .project(allProjectsName)
          .forUpdate()
          .removeAllAccessSections()
          .add(
              allow(Permission.READ)
                  .ref("refs/heads/master")
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.READ).ref("refs/*").group(admins))
          .add(allow(Permission.CREATE).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.PUSH).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allowCapability(GlobalCapability.ADMINISTRATE_SERVER).group(admins))
          .update();

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
      TestAccount user = accountCreator.user1();
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
      in = new ChangeInput(project.get(), ADMIN_PASSWORD, "Test secret change");
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

  @Test
  public void testGitWireProtocolV2HidesRefMetaConfig() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      String url = config.getString("gerrit", null, "canonicalweburl");

      // Create project
      Project.NameKey allRefsVisibleProject = Project.nameKey("all-refs-visible");
      gApi.projects().create(allRefsVisibleProject.get());

      // Allow registered users to fetch/push. Allow anonymous users to read refs/heads/* which also
      // allows reading changes.
      projectOperations
          .project(allProjectsName)
          .forUpdate()
          .removeAllAccessSections()
          .add(allow(Permission.READ).ref("refs/heads/*").group(SystemGroupBackend.ANONYMOUS_USERS))
          .add(
              allow(Permission.READ).ref("refs/heads/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.CREATE).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.PUSH).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      // Create new change and retrieve refs for the created patch set
      ChangeInput visibleChangeIn =
          new ChangeInput(allRefsVisibleProject.get(), "master", "Test public change");
      visibleChangeIn.newBranch = true;
      int visibleChangeNumber = gApi.changes().create(visibleChangeIn).info()._number;
      Change.Id changeId = Change.id(visibleChangeNumber);
      String visibleChangeNumberRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));
      String visibleChangeNumberMetaRef = RefNames.changeMetaRef(changeId);

      // Read refs from target repository using git wire protocol v2 over HTTP anonymously
      String outAnonymousLsRemote =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_CLONE_MIRROR)
                  .add(url + "/" + allRefsVisibleProject.get())
                  .build(),
              ImmutableMap.of("GIT_TRACE_PACKET", "1"));

      assertThat(outAnonymousLsRemote).contains("git< version 2");
      assertThat(outAnonymousLsRemote).doesNotContain(RefNames.REFS_CONFIG);
      assertThat(outAnonymousLsRemote).contains(visibleChangeNumberRef);
      assertThat(outAnonymousLsRemote).contains(visibleChangeNumberMetaRef);
    }
  }

  @Test
  public void testGitWireProtocolV2FetchIndividualRef() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Setup admin password
      gApi.accounts().id(admin.username()).setHttpPassword(ADMIN_PASSWORD);

      // Get authenticated Git/HTTP URL
      String urlWithCredentials =
          config
              .getString("gerrit", null, "canonicalweburl")
              .replace("http://", "http://" + admin.username() + ":" + ADMIN_PASSWORD + "@");

      // Create project
      Project.NameKey privateProject = Project.nameKey("private-project");
      gApi.projects().create(privateProject.get());

      // Clear all permissions for anonymous users. Allow registered users to fetch/push.
      projectOperations
          .project(allProjectsName)
          .forUpdate()
          .removeAllAccessSections()
          .add(
              allow(Permission.READ).ref("refs/heads/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.CREATE).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.PUSH).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      // Create new change and retrieve refs for the created patch set
      ChangeInput visibleChangeIn =
          new ChangeInput(privateProject.get(), "master", "Test private change");
      visibleChangeIn.newBranch = true;
      int visibleChangeNumber = gApi.changes().create(visibleChangeIn).info()._number;
      Change.Id changeId = Change.id(visibleChangeNumber);
      String visibleChangeNumberRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));

      // Create new change and retrieve refs for the created patch set. We'll use this to make sure
      // refs-in-wants only sends us changes we asked for.
      ChangeInput changeWeDidNotAskForIn =
          new ChangeInput(privateProject.get(), "stable", "Test private change 2");
      changeWeDidNotAskForIn.newBranch = true;
      int changeWeDidNotAskForNumber = gApi.changes().create(changeWeDidNotAskForIn).info()._number;
      Change.Id changeWeDidNotAskFor = Change.id(changeWeDidNotAskForNumber);
      String changeWeDidNotAskForRef = RefNames.patchSetRef(PatchSet.id(changeWeDidNotAskFor, 1));

      // Fetch a single ref using git wire protocol v2 over HTTP with authentication
      execute(GIT_INIT);

      String outFetchRef =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_FETCH)
                  .add(urlWithCredentials + "/" + privateProject.get())
                  .add(visibleChangeNumberRef)
                  .build(),
              ImmutableMap.of("GIT_TRACE_PACKET", "1"));

      assertThat(outFetchRef).contains("git< version 2");
      assertThat(outFetchRef).contains(visibleChangeNumberRef);
      // refs-in-wants should not advertise changes we did not ask for
      assertThat(outFetchRef).doesNotContain(changeWeDidNotAskForRef);
    }
  }

  @Test
  public void testGitWireProtocolV2FetchIndividualRef_doesNotNeedChangeIndex() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Setup admin password
      gApi.accounts().id(admin.username()).setHttpPassword(ADMIN_PASSWORD);

      // Get authenticated Git/HTTP URL
      String urlWithCredentials =
          config
              .getString("gerrit", null, "canonicalweburl")
              .replace("http://", "http://" + admin.username() + ":" + ADMIN_PASSWORD + "@");

      // Create project
      Project.NameKey privateProject = Project.nameKey("private-project");
      gApi.projects().create(privateProject.get());

      // Disallow general read permissions for anonymous users
      projectOperations
          .project(allProjectsName)
          .forUpdate()
          .add(deny(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
          .add(
              allow(Permission.READ)
                  .ref("refs/heads/master")
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      // Create new change and retrieve refs for the created patch set
      ChangeInput visibleChangeIn =
          new ChangeInput(privateProject.get(), "master", "Test private change");
      visibleChangeIn.newBranch = true;
      int visibleChangeNumber = gApi.changes().create(visibleChangeIn).info()._number;
      Change.Id changeId = Change.id(visibleChangeNumber);
      String visibleChangeNumberRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));

      // Create new change and retrieve refs for the created patch set. We'll use this to make sure
      // refs-in-wants only sends us changes we asked for.
      ChangeInput changeWeDidNotAskForIn =
          new ChangeInput(privateProject.get(), "stable", "Test private change 2");
      changeWeDidNotAskForIn.newBranch = true;
      int changeWeDidNotAskForNumber = gApi.changes().create(changeWeDidNotAskForIn).info()._number;
      Change.Id changeWeDidNotAskFor = Change.id(changeWeDidNotAskForNumber);
      String changeWeDidNotAskForRef = RefNames.patchSetRef(PatchSet.id(changeWeDidNotAskFor, 1));

      // Fetch a single ref using git wire protocol v2 over HTTP with authentication
      execute(GIT_INIT);

      String outFetchRef;
      try (AutoCloseable ignored = changeIndexOperations.disableReadsAndWrites()) {
        outFetchRef =
            execute(
                ImmutableList.<String>builder()
                    .add(GIT_FETCH)
                    .add(urlWithCredentials + "/" + privateProject.get())
                    .add(visibleChangeNumberRef)
                    .build(),
                ImmutableMap.of("GIT_TRACE_PACKET", "1"));
      }

      assertThat(outFetchRef).contains("git< version 2");
      assertThat(outFetchRef).contains(visibleChangeNumberRef);
      // refs-in-wants should not advertise changes we did not ask for
      assertThat(outFetchRef).doesNotContain(changeWeDidNotAskForRef);
    }
  }

  @Test
  public void testGitWireProtocolV2FetchIndividualRef_doesNotNeedNoteDbWhenAskedForManyChanges()
      throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Setup admin password
      gApi.accounts().id(admin.username()).setHttpPassword(ADMIN_PASSWORD);

      // Get authenticated Git/HTTP URL
      String urlWithCredentials =
          config
              .getString("gerrit", null, "canonicalweburl")
              .replace("http://", "http://" + admin.username() + ":" + ADMIN_PASSWORD + "@");

      // Create project
      Project.NameKey privateProject = Project.nameKey("private-project");
      gApi.projects().create(privateProject.get());

      // Disallow general read permissions for anonymous users
      projectOperations
          .project(allProjectsName)
          .forUpdate()
          .add(deny(Permission.READ).ref("refs/*").group(SystemGroupBackend.ANONYMOUS_USERS))
          .add(
              allow(Permission.READ)
                  .ref("refs/heads/master")
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      // Set up project permission to allow registered users fetching changes/*
      projectOperations
          .project(privateProject)
          .forUpdate()
          .add(
              allow(Permission.READ)
                  .ref("refs/changes/*")
                  .group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      List<String> changeRefs = new ArrayList<>();
      for (int i = 0; i < 10; i++) {
        // Create new change and retrieve refs for the created patch set
        ChangeInput visibleChangeIn =
            new ChangeInput(privateProject.get(), "master", "Test private change");
        visibleChangeIn.newBranch = true;
        int visibleChangeNumber = gApi.changes().create(visibleChangeIn).info()._number;
        Change.Id changeId = Change.id(visibleChangeNumber);
        changeRefs.add(RefNames.patchSetRef(PatchSet.id(changeId, 1)));
      }

      // Fetch a single ref using git wire protocol v2 over HTTP with authentication
      execute(GIT_INIT);

      try (AutoCloseable ignored = changeIndexOperations.disableReadsAndWrites()) {
        // Since we ask for many changes at once, the server will use the change index to speed up
        // filtering. Having that disabled fails.
        assertThrows(
            IOException.class,
            () ->
                execute(
                    ImmutableList.<String>builder()
                        .add(GIT_FETCH)
                        .add(urlWithCredentials + "/" + privateProject.get())
                        .addAll(changeRefs)
                        .build(),
                    ImmutableMap.of("GIT_TRACE_PACKET", "1")));
      }

      // The same call succeeds if the change index is enabled.
      String outFetchRef =
          execute(
              ImmutableList.<String>builder()
                  .add(GIT_FETCH)
                  .add(urlWithCredentials + "/" + privateProject.get())
                  .addAll(changeRefs)
                  .build(),
              ImmutableMap.of("GIT_TRACE_PACKET", "1"));
      assertThat(outFetchRef).contains("git< version 2");
      assertThat(outFetchRef).contains(changeRefs.get(0));
    }
  }

  @Test
  public void testGitWireProtocolV2FetchIndividualRef_anonymousCantSeeInvisibleChange()
      throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      // Setup admin password
      gApi.accounts().id(admin.username()).setHttpPassword(ADMIN_PASSWORD);

      String url = config.getString("gerrit", null, "canonicalweburl");

      // Create project
      Project.NameKey privateProject = Project.nameKey("private-project");
      gApi.projects().create(privateProject.get());

      // Disallow general read permissions for anonymous users except on master
      projectOperations
          .project(allProjectsName)
          .forUpdate()
          .removeAllAccessSections()
          .add(
              allow(Permission.READ)
                  .ref("refs/heads/master")
                  .group(SystemGroupBackend.ANONYMOUS_USERS))
          .add(
              allow(Permission.READ).ref("refs/heads/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.CREATE).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .add(allow(Permission.PUSH).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
          .update();

      // Create new change and retrieve refs for the created patch set
      ChangeInput visibleChangeIn = new ChangeInput(privateProject.get(), "master", "Visible");
      visibleChangeIn.newBranch = true;
      int visibleChangeNumber = gApi.changes().create(visibleChangeIn).info()._number;
      Change.Id changeId = Change.id(visibleChangeNumber);
      String visibleChangeNumberRef = RefNames.patchSetRef(PatchSet.id(changeId, 1));

      // Create new change and retrieve refs for the created patch set. We'll use this to make sure
      // refs-in-wants only sends us changes we asked for.
      ChangeInput invisibleChangeIn = new ChangeInput(privateProject.get(), "stable", "Invisible");
      invisibleChangeIn.newBranch = true;
      int invisibleChangeNumber = gApi.changes().create(invisibleChangeIn).info()._number;
      Change.Id invisibleChange = Change.id(invisibleChangeNumber);
      String invisibleChangeRef = RefNames.patchSetRef(PatchSet.id(invisibleChange, 1));

      // Fetch a single ref using git wire protocol v2 over HTTP with authentication
      execute(GIT_INIT);

      String outFetchRef;
      try (AutoCloseable ignored = changeIndexOperations.disableReadsAndWrites()) {
        outFetchRef =
            execute(
                ImmutableList.<String>builder()
                    .add(GIT_FETCH)
                    .add(url + "/" + privateProject.get())
                    .add(visibleChangeNumberRef)
                    .build(),
                ImmutableMap.of("GIT_TRACE_PACKET", "1"));
      }

      assertThat(outFetchRef).contains("git< version 2");
      assertThat(outFetchRef).contains(visibleChangeNumberRef);

      try (AutoCloseable ignored = changeIndexOperations.disableReadsAndWrites()) {
        // Fetching invisible ref fails
        assertThrows(
            IOException.class,
            () ->
                execute(
                    ImmutableList.<String>builder()
                        .add(GIT_FETCH)
                        .add(url + "/" + privateProject.get())
                        .add(invisibleChangeRef)
                        .build(),
                    ImmutableMap.of("GIT_TRACE_PACKET", "1")));
      }
    }
  }

  private void setUpUserAuthentication(String username) throws Exception {
    // Assign HTTP password to user
    gApi.accounts().id(username).setHttpPassword(ADMIN_PASSWORD);

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
    assertThat(out).containsMatch("(git|ls-remote)< version 2");
    assertThat(out).contains("refs/changes/01/1/1");
    assertThat(out).contains("refs/changes/01/1/meta");
    assertThat(out).contains(commit);
  }

  private String execute(String... cmds) throws Exception {
    return execute(ImmutableList.<String>builder().add(cmds).build());
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
