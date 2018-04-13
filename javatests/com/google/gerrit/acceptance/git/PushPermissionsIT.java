// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.git.testing.PushResultSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.Util;
import java.util.Arrays;
import java.util.function.Consumer;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.junit.Before;
import org.junit.Test;

public class PushPermissionsIT extends AbstractDaemonTest {
  @Before
  public void setUp() throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(allProjects)) {
      ProjectConfig cfg = ProjectConfig.read(md);
      cfg.getProject()
          .setBooleanConfig(BooleanProjectConfig.REQUIRE_CHANGE_ID, InheritableBoolean.FALSE);

      // Remove push-related permissions, so they can be added back individually by test methods.
      removeAllBranchPermissions(
          cfg,
          Permission.ADD_PATCH_SET,
          Permission.CREATE,
          Permission.DELETE,
          Permission.PUSH,
          Permission.PUSH_MERGE,
          Permission.SUBMIT);
      removeAllGlobalCapabilities(cfg, GlobalCapability.ADMINISTRATE_SERVER);

      // Include some auxiliary permissions.
      Util.allow(cfg, Permission.FORGE_AUTHOR, REGISTERED_USERS, "refs/*");
      Util.allow(cfg, Permission.FORGE_COMMITTER, REGISTERED_USERS, "refs/*");

      saveProjectConfig(allProjects, cfg);
    }
  }

  @Test
  public void fastForwardUpdateDenied() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("prohibited by Gerrit: ref update access denied");
    assertThat(r)
        .hasMessages(
            "Branch refs/heads/master:",
            "You are not allowed to perform this operation.",
            "To push into this reference you need 'Push' rights.",
            "User: admin",
            "Please read the documentation and contact an administrator",
            "if you feel the configuration is incorrect");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void nonFastForwardUpdateDenied() throws Exception {
    ObjectId commit = testRepo.commit().create();
    PushResult r = push("+" + commit.name() + ":refs/heads/master");
    assertThat(r).onlyRef("refs/heads/master").isRejected("need 'Force Push' privilege.");
    assertThat(r).hasNoMessages();
    // TODO(dborowitz): Why does this not mention refs?
    assertThat(r).hasProcessed(ImmutableMap.of());
  }

  @Test
  public void deleteDenied() throws Exception {
    PushResult r = push(":refs/heads/master");
    assertThat(r).onlyRef("refs/heads/master").isRejected("cannot delete references");
    assertThat(r)
        .hasMessages(
            "Branch refs/heads/master:",
            "You need 'Delete Reference' rights or 'Push' rights with the ",
            "'Force Push' flag set to delete references.",
            "User: admin",
            "Please read the documentation and contact an administrator",
            "if you feel the configuration is incorrect");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void createDenied() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/heads/newbranch");
    assertThat(r)
        .onlyRef("refs/heads/newbranch")
        .isRejected("prohibited by Gerrit: create not permitted for refs/heads/newbranch");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void groupRefsByMessage() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      tr.branch("foo").commit().create();
      tr.branch("bar").commit().create();
    }

    testRepo.branch("HEAD").commit().create();
    PushResult r = push(":refs/heads/foo", ":refs/heads/bar", "HEAD:refs/heads/master");
    assertThat(r).ref("refs/heads/foo").isRejected("cannot delete references");
    assertThat(r).ref("refs/heads/bar").isRejected("cannot delete references");
    assertThat(r)
        .ref("refs/heads/master")
        .isRejected("prohibited by Gerrit: ref update access denied");
    // TODO(dborowitz): JGit's Transport#expandPushWildcardsFor breaks order of input refspecs.
    //assertThat(r)
    //    .hasMessages(
    //        "Branches refs/heads/foo, refs/heads/bar:",
    //        "You need 'Delete Reference' rights or 'Push' rights with the ",
    //        "'Force Push' flag set to delete references.",
    //        "Branch refs/heads/master:",
    //        "You are not allowed to perform this operation.",
    //        "To push into this reference you need 'Push' rights.",
    //        "User: admin",
    //        "Please read the documentation and contact an administrator",
    //        "if you feel the configuration is incorrect");
  }

  @Test
  public void readOnlyProjectRejectedBeforeTestingPermissions() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      ProjectConfig cfg = new ProjectConfig(project);
      cfg.load(repo);
      cfg.getProject().setState(ProjectState.READ_ONLY);
      saveProjectConfig(cfg);
    }

    PushResult r = push(":refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("prohibited by Gerrit: project state does not permit write");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void refsMetaConfigUpdateRequiresProjectOwner() throws Exception {
    grant(project, "refs/meta/config", Permission.PUSH, false, REGISTERED_USERS);

    forceFetch("refs/meta/config");
    ObjectId commit = testRepo.branch("refs/meta/config").commit().create();
    PushResult r = push(commit.name() + ":refs/meta/config");
    assertThat(r)
        .onlyRef("refs/meta/config")
        // ReceiveCommits theoretically has a different message when a WRITE_CONFIG check fails, but
        // it never gets there, since DefaultPermissionBackend special-cases refs/meta/config and
        // denies UPDATE if the user is not a project owner.
        .isRejected("prohibited by Gerrit: ref update access denied");
    assertThat(r)
        .hasMessages(
            "Branch refs/meta/config:",
            "You are not allowed to perform this operation.",
            "Configuration changes can only be pushed by project owners",
            "who also have 'Push' rights on refs/meta/config",
            "User: admin",
            "Please read the documentation and contact an administrator",
            "if you feel the configuration is incorrect");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));

    grant(project, "refs/*", Permission.OWNER, false, REGISTERED_USERS);

    // Re-fetch refs/meta/config from the server because the grant changed it, and we want a
    // fast-forward.
    forceFetch("refs/meta/config");
    commit = testRepo.branch("refs/meta/config").commit().create();

    assertThat(push(commit.name() + ":refs/meta/config")).onlyRef("refs/meta/config").isOk();
  }

  @Test
  public void createChangeDenied() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/for/master");
    assertThat(r)
        .onlyRef("refs/for/master")
        .isRejected("create change not permitted for refs/heads/master");
    assertThat(r)
        .hasMessages(
            "Branch refs/heads/master:",
            "You need 'Push' rights to upload code review requests.",
            "Verify that you are pushing to the right branch.",
            "User: admin",
            "Please read the documentation and contact an administrator",
            "if you feel the configuration is incorrect");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void updateBySubmitDenied() throws Exception {
    grant(project, "refs/for/refs/heads/*", Permission.PUSH, false, REGISTERED_USERS);

    ObjectId commit = testRepo.branch("HEAD").commit().create();
    assertThat(push("HEAD:refs/for/master")).onlyRef("refs/for/master").isOk();
    gApi.changes().id(commit.name()).current().review(ReviewInput.approve());

    PushResult r = push("HEAD:refs/for/master%submit");
    assertThat(r)
        .onlyRef("refs/for/master%submit")
        .isRejected("update by submit not permitted for refs/heads/master");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void addPatchSetDenied() throws Exception {
    grant(project, "refs/for/refs/heads/*", Permission.PUSH, false, REGISTERED_USERS);
    setApiUser(user);
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.branch = "master";
    ci.subject = "A change";
    Change.Id id = new Change.Id(gApi.changes().create(ci).get()._number);

    setApiUser(admin);
    ObjectId ps1Id = forceFetch(new PatchSet.Id(id, 1).toRefName());
    ObjectId ps2Id = testRepo.amend(ps1Id).add("file", "content").create();
    PushResult r = push(ps2Id.name() + ":refs/for/master");
    assertThat(r)
        .onlyRef("refs/for/master")
        .isRejected("cannot add patch set to " + id.get() + ".");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void skipValidationDenied() throws Exception {
    grant(project, "refs/heads/*", Permission.PUSH, false, REGISTERED_USERS);

    testRepo.branch("HEAD").commit().create();
    PushResult r =
        push(c -> c.setPushOptions(ImmutableList.of("skip-validation")), "HEAD:refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("skip validation not permitted for refs/heads/master");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void accessDatabaseForNoteDbDenied() throws Exception {
    grant(project, "refs/heads/*", Permission.PUSH, false, REGISTERED_USERS);

    testRepo.branch("HEAD").commit().create();
    PushResult r =
        push(
            c -> c.setPushOptions(ImmutableList.of("notedb=allow")),
            "HEAD:refs/changes/34/1234/meta");
    // Same rejection message regardless of whether NoteDb is actually enabled.
    assertThat(r)
        .onlyRef("refs/changes/34/1234/meta")
        .isRejected("NoteDb update requires access database permission");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void administrateServerForUpdateParentDenied() throws Exception {
    grant(project, "refs/meta/config", Permission.PUSH, false, REGISTERED_USERS);
    grant(project, "refs/*", Permission.OWNER, false, REGISTERED_USERS);

    String project2 = name("project2");
    gApi.projects().create(project2);

    ObjectId oldId = forceFetch("refs/meta/config");

    Config cfg = new BlobBasedConfig(null, testRepo.getRepository(), oldId, "project.config");
    cfg.setString("access", null, "inheritFrom", project2);
    ObjectId newId =
        testRepo.branch("refs/meta/config").commit().add("project.config", cfg.toText()).create();

    PushResult r = push(newId.name() + ":refs/meta/config");
    assertThat(r)
        .onlyRef("refs/meta/config")
        .isRejected("invalid project configuration: only Gerrit admin can set parent");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  private static void removeAllBranchPermissions(ProjectConfig cfg, String... permissions) {
    cfg.getAccessSections()
        .stream()
        .filter(
            s ->
                s.getName().startsWith("refs/heads/")
                    || s.getName().startsWith("refs/for/")
                    || s.getName().equals("refs/*"))
        .forEach(s -> Arrays.stream(permissions).forEach(s::removePermission));
  }

  private static void removeAllGlobalCapabilities(ProjectConfig cfg, String... capabilities) {
    Arrays.stream(capabilities)
        .forEach(
            c ->
                cfg.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true)
                    .getPermission(c, true)
                    .getRules()
                    .clear());
  }

  private PushResult push(String... refSpecs) throws Exception {
    return push(c -> {}, refSpecs);
  }

  private PushResult push(Consumer<PushCommand> setUp, String... refSpecs) throws Exception {
    PushCommand cmd =
        testRepo
            .git()
            .push()
            .setRemote("origin")
            .setRefSpecs(Arrays.stream(refSpecs).map(RefSpec::new).collect(toList()));
    setUp.accept(cmd);
    Iterable<PushResult> results = cmd.call();
    assertWithMessage("expected 1 PushResult").that(results).hasSize(1);
    return results.iterator().next();
  }

  private ObjectId forceFetch(String ref) throws Exception {
    TrackingRefUpdate u =
        testRepo.git().fetch().setRefSpecs("+" + ref + ":" + ref).call().getTrackingRefUpdate(ref);
    assertThat(u).isNotNull();
    switch (u.getResult()) {
      case NEW:
      case FAST_FORWARD:
      case FORCED:
        break;
      case IO_FAILURE:
      case LOCK_FAILURE:
      case NOT_ATTEMPTED:
      case NO_CHANGE:
      case REJECTED:
      case REJECTED_CURRENT_BRANCH:
      case REJECTED_MISSING_OBJECT:
      case REJECTED_OTHER_REASON:
      case RENAMED:
      default:
        assert_().fail("fetch failed to update local %s: %s", ref, u.getResult());
        break;
    }
    return u.getNewObjectId();
  }
}
