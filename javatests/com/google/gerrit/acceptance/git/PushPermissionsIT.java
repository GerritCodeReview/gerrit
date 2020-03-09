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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.git.testing.PushResultSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.function.Consumer;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.junit.Before;
import org.junit.Test;

public class PushPermissionsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() throws Exception {
    try (ProjectConfigUpdate u = updateProject(allProjects)) {
      ProjectConfig cfg = u.getConfig();

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
      u.save();
    }

    // Include some auxiliary permissions.
    projectOperations
        .allProjectsForUpdate()
        .add(allow(Permission.FORGE_AUTHOR).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.FORGE_COMMITTER).ref("refs/*").group(REGISTERED_USERS))
        .update();
  }

  @Test
  public void pushMergeRegular() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    RevCommit c1 = testRepo.branch("HEAD").commit().create();
    RevCommit c2 = testRepo.commit().parent(c1).create();
    RevCommit c3 = testRepo.commit().parent(c1).parent(c2).create();

    testRepo.reset(c2);
    PushResult r = pushHead(testRepo, "refs/heads/master");
    assertThat(r.getRemoteUpdate("refs/heads/master").getStatus()).isEqualTo(Status.OK);

    testRepo.reset(c3);
    r = pushHead(testRepo, "refs/heads/master");
    String msg =
        String.format("commit %s: you are not allowed to upload merges", abbreviateName(c3));
    assertThat(r.getRemoteUpdate("refs/heads/master").getStatus()).isNotEqualTo(Status.OK);
    assertThat(r.getRemoteUpdate("refs/heads/master").getMessage()).isEqualTo(msg);

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH_MERGE).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();
    r = pushHead(testRepo, "refs/heads/master");
    assertThat(r.getRemoteUpdate("refs/heads/master").getStatus()).isEqualTo(Status.OK);
  }

  @Test
  public void mixingMagicAndRegularPush() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/heads/master", "HEAD:refs/for/master");

    String msg = "cannot combine normal pushes and magic pushes";
    assertThat(r.getRemoteUpdate("refs/heads/master")).isNotEqualTo(Status.OK);
    assertThat(r.getRemoteUpdate("refs/for/master")).isNotEqualTo(Status.OK);
    assertThat(r.getRemoteUpdate("refs/for/master").getMessage()).isEqualTo(msg);
  }

  @Test
  public void fastForwardUpdateDenied() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("prohibited by Gerrit: not permitted: update");
    assertThat(r)
        .hasMessages(
            "error: branch refs/heads/master:",
            "To push into this reference you need 'Push' rights.",
            "User: admin",
            "Contact an administrator to fix the permissions");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void nonFastForwardUpdateDenied() throws Exception {
    ObjectId commit = testRepo.commit().create();
    PushResult r = push("+" + commit.name() + ":refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("prohibited by Gerrit: not permitted: force update");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void deleteDenied() throws Exception {
    PushResult r = push(":refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("prohibited by Gerrit: not permitted: delete");
    assertThat(r)
        .hasMessages(
            "error: branch refs/heads/master:",
            "You need 'Delete Reference' rights or 'Push' rights with the ",
            "'Force Push' flag set to delete references.",
            "User: admin",
            "Contact an administrator to fix the permissions");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void createDenied() throws Exception {
    testRepo.branch("HEAD").commit().create();
    PushResult r = push("HEAD:refs/heads/newbranch");
    assertThat(r)
        .onlyRef("refs/heads/newbranch")
        .isRejected("prohibited by Gerrit: not permitted: create");
    assertThat(r).containsMessages("You need 'Create' rights to create new references.");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void groupRefsByMessage() throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      tr.branch("foo").commit().create();
      tr.branch("bar").commit().create();
    }

    testRepo.branch("HEAD").commit().create();
    PushResult r = push(":refs/heads/foo", ":refs/heads/bar", "HEAD:refs/heads/master");
    assertThat(r).ref("refs/heads/foo").isRejected("prohibited by Gerrit: not permitted: delete");
    assertThat(r).ref("refs/heads/bar").isRejected("prohibited by Gerrit: not permitted: delete");
    assertThat(r)
        .ref("refs/heads/master")
        .isRejected("prohibited by Gerrit: not permitted: update");
    assertThat(r)
        .hasMessages(
            "error: branches refs/heads/foo, refs/heads/bar:",
            "You need 'Delete Reference' rights or 'Push' rights with the ",
            "'Force Push' flag set to delete references.",
            "error: branch refs/heads/master:",
            "To push into this reference you need 'Push' rights.",
            "User: admin",
            "Contact an administrator to fix the permissions");
  }

  @Test
  public void readOnlyProjectRejectedBeforeTestingPermissions() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      try (ProjectConfigUpdate u = updateProject(project)) {
        u.getConfig().updateProject(p -> p.setState(ProjectState.READ_ONLY));
        u.save();
      }
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
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/meta/config").group(REGISTERED_USERS))
        .update();

    forceFetch("refs/meta/config");
    ObjectId commit = testRepo.branch("refs/meta/config").commit().create();
    PushResult r = push(commit.name() + ":refs/meta/config");
    assertThat(r)
        .onlyRef("refs/meta/config")
        // ReceiveCommits theoretically has a different message when a WRITE_CONFIG check fails, but
        // it never gets there, since DefaultPermissionBackend special-cases refs/meta/config and
        // denies UPDATE if the user is not a project owner.
        .isRejected("prohibited by Gerrit: not permitted: update");
    assertThat(r)
        .hasMessages(
            "error: branch refs/meta/config:",
            "Configuration changes can only be pushed by project owners",
            "who also have 'Push' rights on refs/meta/config",
            "User: admin",
            "Contact an administrator to fix the permissions");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();

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
        .isRejected("prohibited by Gerrit: not permitted: create change on refs/heads/master");
    assertThat(r)
        .containsMessages(
            "error: branch refs/for/master:",
            "You need 'Create Change' rights to upload code review requests.",
            "Verify that you are pushing to the right branch.");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void updateBySubmitDenied() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/for/refs/heads/*").group(REGISTERED_USERS))
        .update();

    ObjectId commit =
        testRepo.branch("HEAD").commit().message("test commit").insertChangeId().create();
    assertThat(push("HEAD:refs/for/master")).onlyRef("refs/for/master").isOk();
    gApi.changes().id(commit.name()).current().review(ReviewInput.approve());

    PushResult r = push("HEAD:refs/for/master%submit");
    assertThat(r)
        .onlyRef("refs/for/master%submit")
        .isRejected("prohibited by Gerrit: not permitted: update by submit on refs/heads/master");
    assertThat(r)
        .containsMessages(
            "You need 'Submit' rights on refs/for/ to submit changes during change upload.");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void addPatchSetDenied() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/for/refs/heads/*").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.branch = "master";
    ci.subject = "A change";
    Change.Id id = Change.id(gApi.changes().create(ci).get()._number);

    requestScopeOperations.setApiUser(admin.id());
    ObjectId ps1Id = forceFetch(PatchSet.id(id, 1).toRefName());
    ObjectId ps2Id = testRepo.amend(ps1Id).add("file", "content").create();
    PushResult r = push(ps2Id.name() + ":refs/for/master");
    // Admin had ADD_PATCH_SET removed in setup.
    assertThat(r)
        .onlyRef("refs/for/master")
        .isRejected("cannot add patch set to " + id.get() + ".");
    assertThat(r).hasNoMessages();
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void skipValidationDenied() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    testRepo.branch("HEAD").commit().create();
    PushResult r =
        push(c -> c.setPushOptions(ImmutableList.of("skip-validation")), "HEAD:refs/heads/master");
    assertThat(r)
        .onlyRef("refs/heads/master")
        .isRejected("prohibited by Gerrit: not permitted: skip validation");
    assertThat(r)
        .containsMessages(
            "You need 'Forge Author', 'Forge Server', 'Forge Committer'",
            "and 'Push Merge' rights to skip validation.");
    assertThat(r).hasProcessed(ImmutableMap.of("refs", 1));
  }

  @Test
  public void accessDatabaseForNoteDbDenied() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

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
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/meta/config").group(REGISTERED_USERS))
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();

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
    for (AccessSection s : ImmutableList.copyOf(cfg.getAccessSections())) {
      if (s.getName().startsWith("refs/heads/")
          || s.getName().startsWith("refs/for/")
          || s.getName().equals("refs/*")) {
        cfg.upsertAccessSection(
            s.getName(),
            updatedSection -> {
              Arrays.stream(permissions).forEach(p -> updatedSection.remove(Permission.builder(p)));
            });
      }
    }
  }

  private static void removeAllGlobalCapabilities(ProjectConfig cfg, String... capabilities) {
    Arrays.stream(capabilities)
        .forEach(
            c ->
                cfg.upsertAccessSection(
                    AccessSection.GLOBAL_CAPABILITIES,
                    as -> {
                      as.upsertPermission(c).clearRules();
                    }));
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
        assertWithMessage("fetch failed to update local %s: %s", ref, u.getResult()).fail();
        break;
    }
    return u.getNewObjectId();
  }
}
