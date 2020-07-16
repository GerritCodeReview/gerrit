// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.deny;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.permissionKey;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.receive.ReceiveCommitsAdvertiseRefsHookChain;
import com.google.gerrit.server.git.receive.testing.TestRefAdvertiser;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class RefAdvertisementIT extends AbstractDaemonTest {
  @Inject private AllUsersName allUsersName;
  @Inject private ChangeNoteUtil noteUtil;
  @Inject private PermissionBackend permissionBackend;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private AccountGroup.UUID admins;
  private AccountGroup.UUID nonInteractiveUsers;

  private RevCommit rcMaster;
  private RevCommit rcBranch;

  private ChangeData cd1;
  private String psRef1;
  private String metaRef1;

  private ChangeData cd2;
  private String psRef2;
  private String metaRef2;

  private ChangeData cd3;
  private String psRef3;
  private String metaRef3;

  private ChangeData cd4;
  private String psRef4;
  private String metaRef4;

  @ConfigSuite.Config
  public static Config enableFullRefEvaluation() {
    Config cfg = new Config();
    cfg.setBoolean("auth", null, "skipFullRefEvaluationIfAllRefsAreVisible", false);
    return cfg;
  }

  @Before
  public void setUp() throws Exception {
    admins = adminGroupUuid();
    nonInteractiveUsers = groupUuid("Non-Interactive Users");
    setUpPermissions();
    setUpChanges();
  }

  // This method is idempotent, so it is safe to call it on every test setup.
  private void setUpPermissions() throws Exception {
    // Remove read permissions for all users besides admin.
    try (ProjectConfigUpdate u = updateProject(allProjects)) {

      for (AccessSection sec : ImmutableList.copyOf(u.getConfig().getAccessSections())) {
        u.getConfig()
            .upsertAccessSection(
                sec.getName(),
                updatedSec -> {
                  updatedSec.removePermission(Permission.READ);
                });
      }
      u.save();
    }
    projectOperations
        .allProjectsForUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(admins))
        .update();

    // Remove all read permissions on All-Users.
    try (ProjectConfigUpdate u = updateProject(allUsers)) {
      for (AccessSection sec : ImmutableList.copyOf(u.getConfig().getAccessSections())) {
        u.getConfig()
            .upsertAccessSection(
                sec.getName(),
                updatedSec -> {
                  updatedSec.removePermission(Permission.READ);
                });
      }
      u.save();
    }
  }

  // Building the following:
  //   rcMaster (c1 master master-tag) <-- rcBranch (c2 branch branch-tag)
  //      \                                    \
  //    (c3_open)                            (c4_open)
  //
  private void setUpChanges() throws Exception {
    gApi.projects().name(project.get()).branch("branch").create(new BranchInput());

    // First 2 changes are merged, which means the tags pointing to them are
    // visible.
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/*").group(admins))
        .update();

    //   rcMaster (c1 master)
    PushOneCommit.Result mr =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master%submit");
    mr.assertOkStatus();
    cd1 = mr.getChange();
    rcMaster = mr.getCommit();
    psRef1 = cd1.currentPatchSet().id().toRefName();
    metaRef1 = RefNames.changeMetaRef(cd1.getId());

    //   rcMaster (c1 master) <-- rcBranch (c2 branch)
    PushOneCommit.Result br =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/branch%submit");
    br.assertOkStatus();
    cd2 = br.getChange();
    rcBranch = br.getCommit();
    psRef2 = cd2.currentPatchSet().id().toRefName();
    metaRef2 = RefNames.changeMetaRef(cd2.getId());

    // Second 2 changes are unmerged.
    //   rcMaster (c1 master) <-- rcBranch (c2 branch)
    //      \
    //    (c3_open)
    //
    mr = pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master");
    mr.assertOkStatus();
    cd3 = mr.getChange();
    psRef3 = cd3.currentPatchSet().id().toRefName();
    metaRef3 = RefNames.changeMetaRef(cd3.getId());

    //   rcMaster (c1 master) <-- rcBranch (c2 branch)
    //      \                        \
    //     (c3_open)                (c4_open)
    br = pushFactory.create(admin.newIdent(), testRepo).to("refs/for/branch");
    br.assertOkStatus();
    cd4 = br.getChange();
    psRef4 = cd4.currentPatchSet().id().toRefName();
    metaRef4 = RefNames.changeMetaRef(cd4.getId());

    try (Repository repo = repoManager.openRepository(project)) {
      //   rcMaster (c1 master master-tag) <-- rcBranch (c2 branch)
      //       \                                  \
      //     (c3_open)                          (c4_open)
      RefUpdate mtu = repo.updateRef("refs/tags/master-tag");
      mtu.setExpectedOldObjectId(ObjectId.zeroId());
      mtu.setNewObjectId(repo.exactRef("refs/heads/master").getObjectId());
      assertThat(mtu.update()).isEqualTo(RefUpdate.Result.NEW);

      //   rcMaster (c1 master master-tag) <-- rcBranch (c2 branch branch-tag)
      //       \                                  \
      //     (c3_open)                          (c4_open)
      RefUpdate btu = repo.updateRef("refs/tags/branch-tag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(repo.exactRef("refs/heads/branch").getObjectId());
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);

      // Create a tag for the tree of the commit on 'master'
      // tree-tag -> master.tree
      RefUpdate ttu = repo.updateRef("refs/tags/tree-tag");
      ttu.setExpectedOldObjectId(ObjectId.zeroId());
      ttu.setNewObjectId(rcMaster.getTree().toObjectId());
      assertThat(ttu.update()).isEqualTo(RefUpdate.Result.NEW);
    }
  }

  @Test
  @GerritConfig(name = "auth.skipFullRefEvaluationIfAllRefsAreVisible", value = "false")
  public void uploadPackAllRefsVisibleNoRefsMetaConfig() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(admins))
        .setExclusiveGroup(permissionKey(Permission.READ).ref(RefNames.REFS_CONFIG), true)
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        "HEAD",
        psRef1,
        metaRef1,
        psRef2,
        metaRef2,
        psRef3,
        metaRef3,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
    // tree-tag not visible. See comment in subsetOfBranchesVisibleIncludingHead.
  }

  @Test
  @GerritConfig(name = "auth.skipFullRefEvaluationIfAllRefsAreVisible", value = "true")
  public void uploadPackAllRefsVisibleNoRefsMetaConfigSkipFullRefEval() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(admins))
        .setExclusiveGroup(permissionKey(Permission.READ).ref(RefNames.REFS_CONFIG), true)
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        "HEAD",
        psRef1,
        metaRef1,
        psRef2,
        metaRef2,
        psRef3,
        metaRef3,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag",
        "refs/tags/tree-tag");
  }

  @Test
  public void uploadPackAllRefsVisibleWithRefsMetaConfig() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();

    assertUploadPackRefs(
        "HEAD",
        psRef1,
        metaRef1,
        psRef2,
        metaRef2,
        psRef3,
        metaRef3,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/heads/master",
        RefNames.REFS_CONFIG,
        "refs/tags/branch-tag",
        "refs/tags/master-tag",
        "refs/tags/tree-tag");
  }

  @Test
  public void grantReadOnRefsTagsIsNoOp() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(); // We expect no refs returned
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleIncludingHead() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(deny(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        "HEAD", psRef1, metaRef1, psRef3, metaRef3, "refs/heads/master", "refs/tags/master-tag");
    // tree-tag is not visible because we don't look at trees reachable from
    // refs
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleNotIncludingHead() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // master branch is not visible but master-tag is reachable from branch
        // (since PushOneCommit always bases changes on each other).
        "refs/tags/master-tag");
    // tree-tag not visible. See comment in subsetOfBranchesVisibleIncludingHead.
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleWithEdit() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    // Admin's edit is not visible.
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(cd3.getId().get()).edit().create();

    // User's edit is visible.
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(cd3.getId().get()).edit().create();

    assertUploadPackRefs(
        "HEAD",
        psRef1,
        metaRef1,
        psRef3,
        metaRef3,
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/users/01/1000001/edit-" + cd3.getId() + "/1");
    // tree-tag not visible. See comment in subsetOfBranchesVisibleIncludingHead.
  }

  @Test
  public void uploadPackSubsetOfBranchesAndEditsVisibleWithViewPrivateChanges() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.VIEW_PRIVATE_CHANGES).ref("refs/*").group(REGISTERED_USERS))
        .update();

    // Admin's edit on change3 is visible.
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(cd3.getId().get()).edit().create();

    // Admin's edit on change4 is not visible since user cannot see the change.
    gApi.changes().id(cd4.getId().get()).edit().create();

    // User's edit is visible.
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(cd3.getId().get()).edit().create();

    assertUploadPackRefs(
        "HEAD",
        psRef1,
        metaRef1,
        psRef3,
        metaRef3,
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/users/00/1000000/edit-" + cd3.getId() + "/1",
        "refs/users/01/1000001/edit-" + cd3.getId() + "/1");
    // tree-tag not visible. See comment in subsetOfBranchesVisibleIncludingHead.
  }

  @Test
  public void uploadPackSubsetOfRefsVisibleWithAccessDatabase() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(project)
        .forUpdate()
        .add(deny(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(cd3.getId().get()).edit().create();
    requestScopeOperations.setApiUser(user.id());

    assertUploadPackRefs(
        // Change 1 is visible due to accessDatabase capability, even though
        // refs/heads/master is not.
        psRef1,
        metaRef1,
        psRef2,
        metaRef2,
        psRef3,
        metaRef3,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag",
        // All edits are visible due to accessDatabase capability.
        "refs/users/00/1000000/edit-" + cd3.getId() + "/1");
    // tree-tag not visible. See comment in subsetOfBranchesVisibleIncludingHead.
  }

  @Test
  public void uploadPackNoSearchingChangeCacheImplMaster() throws Exception {
    uploadPackNoSearchingChangeCacheImpl();
  }

  @Test
  @GerritConfig(name = "container.slave", value = "true")
  public void uploadPackNoSearchingChangeCacheImplSlave() throws Exception {
    uploadPackNoSearchingChangeCacheImpl();
  }

  private void uploadPackNoSearchingChangeCacheImpl() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertRefs(
        project,
        user,
        // Can't use stored values from the index so DB must be enabled.
        false,
        "HEAD",
        psRef1,
        metaRef1,
        psRef2,
        metaRef2,
        psRef3,
        metaRef3,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
    // tree-tag not visible. See comment in subsetOfBranchesVisibleIncludingHead.
  }

  @Test
  public void uploadPackSequencesWithAccessDatabase() throws Exception {
    assertRefs(allProjects, user, true);

    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    assertRefs(allProjects, user, true, "refs/sequences/changes");
  }

  @Test
  public void uploadPackAllRefsAreVisibleOrphanedTag() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    // Delete the pending change on 'branch' and 'branch' itself so that the tag gets orphaned
    gApi.changes().id(cd4.getId().get()).delete();
    gApi.projects().name(project.get()).branch("refs/heads/branch").delete();

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        "HEAD",
        "refs/meta/config",
        psRef1,
        metaRef1,
        psRef2,
        metaRef2,
        psRef3,
        metaRef3,
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag",
        "refs/tags/tree-tag");
  }

  @Test
  public void uploadPackSubsetRefsVisibleOrphanedTagInvisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();
    // Create a tag for the pending change on 'branch' so that the tag is orphaned
    try (Repository repo = repoManager.openRepository(project)) {
      // change4-tag -> psRef4
      RefUpdate ctu = repo.updateRef("refs/tags/change4-tag");
      ctu.setExpectedOldObjectId(ObjectId.zeroId());
      ctu.setNewObjectId(repo.exactRef(psRef4).getObjectId());
      assertThat(ctu.update()).isEqualTo(RefUpdate.Result.NEW);
    }

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag");
  }

  // first  ls-remote: rcMaster (c1 master)
  // second ls-remote: rcMaster (c1 master) <- newchange1 (master-newtag)
  @Test
  public void uploadPackNewCommitOrphanTagInvisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    // rcMaster (c1 master)
    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag");

    try (Repository repo = repoManager.openRepository(project)) {
      PushOneCommit.Result r =
          pushFactory.create(admin.newIdent(), testRepo).setParent(rcMaster).to("refs/for/master");
      r.assertOkStatus();

      // rcMaster (c1 master) <- newchange1 (master-newtag)
      RefUpdate btu = repo.updateRef("refs/tags/master-newtag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(r.getCommit());
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag");
  }

  // first  ls-remote: rcBranch (c2) <- newcommit1                 <- newcommit2 (branch)
  // second ls-remote: rcBranch (c2) <- newcommit1 (branch-newtag) <- newcommit2 (branch)
  @Test
  public void uploadPackNewReachableTagVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // c2 <- newcommit1 (branch)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/heads/branch");
      r.assertOkStatus();
      RevCommit tagRc = r.getCommit();

      // c2 <- newcommit1 <- newcommit2 (branch)
      r = pushFactory.create(admin.newIdent(), testRepo).setParent(tagRc).to("refs/heads/branch");
      r.assertOkStatus();

      assertUploadPackRefs(
          psRef2,
          metaRef2,
          psRef4,
          metaRef4,
          "refs/heads/branch",
          "refs/tags/branch-tag",
          // See comment in subsetOfBranchesVisibleNotIncludingHead.
          "refs/tags/master-tag");

      // c2 <- newcommit1 (branch-newtag) <- newcommit2 (branch)
      RefUpdate btu = repo.updateRef("refs/tags/branch-newtag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(tagRc);
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/tags/branch-newtag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag");
  }

  // first  ls-remote: rcBranch (c2) <- newcommit1 (branch)
  // second ls-remote: rcBranch (c2) <- newcommit1                 <- newcommit2 (branch)
  // third  ls-remote: rcBranch (c2) <- newcommit1 (branch-newtag) <- newcommit2 (branch)
  @Test
  public void uploadPackBranchFFNewTagOldBranchVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // rcBranch (c2) <- newcommit1 (branch)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/heads/branch");
      r.assertOkStatus();
      RevCommit tagRc = r.getCommit();

      assertUploadPackRefs(
          psRef2,
          metaRef2,
          psRef4,
          metaRef4,
          "refs/heads/branch",
          "refs/tags/branch-tag",
          // See comment in subsetOfBranchesVisibleNotIncludingHead.
          "refs/tags/master-tag");

      // rcBranch (c2) <- newcommit1 <- newcommit2 (branch)
      r = pushFactory.create(admin.newIdent(), testRepo).setParent(tagRc).to("refs/heads/branch");
      r.assertOkStatus();

      // rcBranch (c2) <- newcommit1 (branch-newtag) <- newcommit2 (branch)
      RefUpdate btu = repo.updateRef("refs/tags/branch-newtag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(tagRc);
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/tags/branch-newtag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag");
  }

  // first  ls-remote: rcBranch (c2)        <- newcommit1 (branch-oldtag) <- newcommit2 (branch)
  // second ls-remote: rcBranch (c2 branch) <- newcommit1 (branch-oldtag)
  @Test
  public void uploadPackBranchRewindMakeTagUnreachableInVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // rcBranch (c2) <- newcommit1 (branch)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/heads/branch");
      r.assertOkStatus();
      RevCommit tagRc = r.getCommit();

      // rcBranch (c2) <- newcommit1 <- newcommit2 (branch)
      r = pushFactory.create(admin.newIdent(), testRepo).setParent(tagRc).to("refs/heads/branch");
      r.assertOkStatus();
      RevCommit bRc = r.getCommit();

      // rcBranch (c2) <- newcommit1 (branch-oldtag) <- newcommit2 (branch)
      RefUpdate btu = repo.updateRef("refs/tags/branch-oldtag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(tagRc);
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);

      assertUploadPackRefs(
          psRef2,
          metaRef2,
          psRef4,
          metaRef4,
          "refs/heads/branch",
          "refs/tags/branch-tag",
          "refs/tags/branch-oldtag",
          // See comment in subsetOfBranchesVisibleNotIncludingHead.
          "refs/tags/master-tag");

      // rcBranch (c2 branch) <- newcommit1 (branch-oldtag) <- newcommit2
      btu = repo.updateRef("refs/heads/branch");
      btu.setExpectedOldObjectId(bRc);
      btu.setNewObjectId(rcBranch);
      btu.setForceUpdate(true);
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.FORCED);
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag");
  }

  // first  ls-remote: rcBranch (c2 branch) <- newcommit1 (new-tag)
  // second ls-remote: rcBranch (c2 branch) <- newcommit1 (new-tag) <- newcommit2 (new-branch)
  @Test
  public void uploadPackCreateBranchTagReachableVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/new-branch").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // rcBranch (c2 branch) <- newcommit1 (branch-newtag)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/tags/new-tag");
      r.assertOkStatus();
      RevCommit tagRc = r.getCommit();

      assertUploadPackRefs();

      // rcBranch (c2) <- newcommit1 (branch-newtag) <- newcommit2 (branch)
      r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(tagRc)
              .to("refs/heads/new-branch");
      r.assertOkStatus();
    }

    assertUploadPackRefs(
        "refs/heads/new-branch",
        "refs/tags/branch-tag",
        "refs/tags/master-tag",
        "refs/tags/new-tag");
  }

  // first  ls-remote: rcBranch (c2 branch)               <- newcommit1 (updated-tag)
  // second ls-remote: rcBranch (c2 branch updated-tag)
  @Test
  public void uploadPackTagUpdatedReachableVisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // rcBranch (c2 branch) <- newcommit1 (updated-tag)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/tags/updated-tag");
      r.assertOkStatus();
      RevCommit tagRc = r.getCommit();

      assertUploadPackRefs(
          psRef2,
          metaRef2,
          psRef4,
          metaRef4,
          "refs/heads/branch",
          "refs/tags/branch-tag",
          "refs/tags/master-tag");

      // rcBranch (c2 branch updated-tag)
      RefUpdate btu = repo.updateRef("refs/tags/updated-tag");
      btu.setExpectedOldObjectId(tagRc);
      btu.setNewObjectId(rcBranch);
      btu.setForceUpdate(true);
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.FORCED);
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/tags/master-tag",
        "refs/tags/updated-tag");
  }

  // first  ls-remote: rcBranch (c2 branch updated-tag)
  // second ls-remote: rcBranch (c2 branch)             <- newcommit1 (updated-tag)
  @Test
  public void uploadPackTagUpdatedUnreachableInvisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // rcBranch (c2 branch updated-tag)
      RefUpdate btu = repo.updateRef("refs/tags/updated-tag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(rcBranch);
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);

      assertUploadPackRefs(
          psRef2,
          metaRef2,
          psRef4,
          metaRef4,
          "refs/heads/branch",
          "refs/tags/branch-tag",
          "refs/tags/master-tag",
          "refs/tags/updated-tag");

      // rcBranch (c2 branch) <- newcommit1 (updated-tag)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/tags/updated-tag");
      r.assertOkStatus();
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  // first  ls-remote: rcBranch (c2 branch branch-tag)
  // second ls-remote: rcBranch (c2 branch)
  @Test
  public void uploadPackTagDeleted() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .add(allow(Permission.DELETE).ref("refs/tags/branch-tag").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/tags/branch-tag").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    // rcBranch (c2 branch branch-tag)
    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");

    // rcBranch (c2 branch)
    try (Repository repo = repoManager.openRepository(project)) {
      RefUpdate btu = repo.updateRef("refs/tags/branch-tag");
      btu.setExpectedOldObjectId(rcBranch);
      btu.setNewObjectId(ObjectId.zeroId());
      btu.setForceUpdate(true);
      assertThat(btu.delete()).isEqualTo(RefUpdate.Result.FORCED);
    }

    assertUploadPackRefs(
        psRef2, metaRef2, psRef4, metaRef4, "refs/heads/branch", "refs/tags/master-tag");
  }

  // first  ls-remote: rcBranch (c2 branch) <- newcommit1 (new-tag) <- newcommit2 (new-branch)
  // second ls-remote: rcBranch (c2 branch) <- newcommit1 (new-tag)
  @Test
  public void uploadPackBranchDeleteTagUnreachableInvisible() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .add(allow(Permission.READ).ref("refs/heads/new-branch").group(REGISTERED_USERS))
        .add(allow(Permission.DELETE).ref("refs/heads/new-branch").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref("refs/tags/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());

    try (Repository repo = repoManager.openRepository(project)) {
      // rcBranch (branch) <- newcommit1 (new-tag)
      PushOneCommit.Result r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(rcBranch)
              .to("refs/tags/new-tag");
      r.assertOkStatus();
      RevCommit tagRc = r.getCommit();

      // rcBranch (c2 branch) <- newcommit1 (new-tag) <- newcommit2 (new-branch)
      r =
          pushFactory
              .create(admin.newIdent(), testRepo)
              .setParent(tagRc)
              .to("refs/heads/new-branch");
      r.assertOkStatus();
    }

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/heads/new-branch",
        "refs/tags/new-tag",
        "refs/tags/master-tag");

    // rcBranch (c2 branch) <- newcommit1 (new-tag)
    gApi.projects().name(project.get()).branch("refs/heads/new-branch").delete();

    assertUploadPackRefs(
        psRef2,
        metaRef2,
        psRef4,
        metaRef4,
        "refs/heads/branch",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  @Test
  public void receivePackListsOpenChangesAsAdditionalHaves() throws Exception {
    TestRefAdvertiser.Result r = getReceivePackRefs();
    assertThat(r.allRefs().keySet())
        .containsExactly(
            // meta refs are excluded
            "refs/heads/branch",
            "refs/heads/master",
            "refs/meta/config",
            "refs/tags/branch-tag",
            "refs/tags/master-tag",
            "refs/tags/tree-tag");
    assertThat(r.additionalHaves()).containsExactly(obj(cd3, 1), obj(cd4, 1));
  }

  @Test
  public void receivePackRespectsVisibilityOfOpenChanges() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .add(deny(Permission.READ).ref("refs/heads/branch").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());

    assertThat(getReceivePackRefs().additionalHaves()).containsExactly(obj(cd3, 1));
  }

  @Test
  public void receivePackListsOnlyLatestPatchSet() throws Exception {
    testRepo.reset(obj(cd3, 1));
    PushOneCommit.Result r = amendChange(cd3.change().getKey().get());
    r.assertOkStatus();
    cd3 = r.getChange();
    assertThat(getReceivePackRefs().additionalHaves()).containsExactly(obj(cd3, 2), obj(cd4, 1));
  }

  @Test
  public void receivePackOmitsMissingObject() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> tr = new TestRepository<>(repo)) {
      String subject = "Subject for missing commit";
      Change c = new Change(cd3.change());
      PatchSet.Id psId = PatchSet.id(cd3.getId(), 2);
      c.setCurrentPatchSet(psId, subject, c.getOriginalSubject());

      PersonIdent committer = serverIdent.get();
      PersonIdent author =
          noteUtil.newAccountIdIdent(getAccount(admin.id()).id(), committer.getWhen(), committer);
      tr.branch(RefNames.changeMetaRef(cd3.getId()))
          .commit()
          .author(author)
          .committer(committer)
          .message(
              "Update patch set "
                  + psId.get()
                  + "\n"
                  + "\n"
                  + "Patch-set: "
                  + psId.get()
                  + "\n"
                  + "Commit: "
                  + rev
                  + "\n"
                  + "Subject: "
                  + subject
                  + "\n")
          .create();
      indexer.index(c.getProject(), c.getId());
    }

    assertThat(getReceivePackRefs().additionalHaves()).containsExactly(obj(cd4, 1));
  }

  @Test
  public void advertisedReferencesDontShowUserBranchWithoutRead() throws Exception {
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getUserRefs(git)).isEmpty();
    }
  }

  @Test
  public void advertisedReferencesOmitUserBranchesOfOtherUsers() throws Exception {
    projectOperations
        .project(allUsersName)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .update();
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getUserRefs(git))
          .containsExactly(RefNames.REFS_USERS_SELF, RefNames.refsUsers(user.id()));
    }
  }

  @Test
  public void advertisedReferencesIncludeAllUserBranchesWithAccessDatabase() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getUserRefs(git))
          .containsExactly(
              RefNames.REFS_USERS_SELF,
              RefNames.refsUsers(user.id()),
              RefNames.refsUsers(admin.id()));
    }
  }

  @Test
  public void advertisedReferencesDontShowGroupBranchToOwnerWithoutRead() throws Exception {
    createSelfOwnedGroup("Foos", user);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getGroupRefs(git)).isEmpty();
    }
  }

  @Test
  public void advertisedReferencesOmitGroupBranchesOfNonOwnedGroups() throws Exception {
    projectOperations
        .project(allUsersName)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .update();
    AccountGroup.UUID users = createGroup("Users", admins, user);
    AccountGroup.UUID foos = createGroup("Foos", users);
    AccountGroup.UUID bars = createSelfOwnedGroup("Bars", user);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getGroupRefs(git))
          .containsExactly(RefNames.refsGroups(foos), RefNames.refsGroups(bars));
    }
  }

  @Test
  public void advertisedReferencesIncludeAllGroupBranchesWithAccessDatabase() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    AccountGroup.UUID users = createGroup("Users", admins);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getGroupRefs(git))
          .containsExactly(
              RefNames.refsGroups(admins),
              RefNames.refsGroups(nonInteractiveUsers),
              RefNames.refsGroups(users));
    }
  }

  @Test
  public void advertisedReferencesIncludeAllGroupBranchesForAdmins() throws Exception {
    projectOperations
        .project(allUsersName)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ADMINISTRATE_SERVER).group(REGISTERED_USERS))
        .update();
    AccountGroup.UUID users = createGroup("Users", admins);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getGroupRefs(git))
          .containsExactly(
              RefNames.refsGroups(admins),
              RefNames.refsGroups(nonInteractiveUsers),
              RefNames.refsGroups(users));
    }
  }

  @Test
  public void advertisedReferencesOmitNoteDbNotesBranches() throws Exception {
    projectOperations
        .project(allUsersName)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS + "*").group(REGISTERED_USERS))
        .update();
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getRefs(git)).containsNoneOf(RefNames.REFS_EXTERNAL_IDS, RefNames.REFS_GROUPNAMES);
    }
  }

  @Test
  public void advertisedReferencesOmitPrivateChangesOfOtherUsers() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = cd3.currentPatchSet().refName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(cd3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).doesNotContain(change3RefName);
    }
  }

  @Test
  public void advertisedReferencesIncludePrivateChangesWhenAllRefsMayBeRead() throws Exception {
    assume()
        .that(baseConfig.getBoolean("auth", "skipFullRefEvaluationIfAllRefsAreVisible", true))
        .isTrue();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = cd3.currentPatchSet().refName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(cd3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).contains(change3RefName);
    }
  }

  @Test
  @GerritConfig(name = "auth.skipFullRefEvaluationIfAllRefsAreVisible", value = "false")
  public void advertisedReferencesOmitPrivateChangesOfOtherUsersWhenShortcutDisabled()
      throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = cd3.currentPatchSet().refName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(cd3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).doesNotContain(change3RefName);
    }
  }

  @Test
  public void advertisedReferencesOmitDraftCommentRefsOfOtherUsers() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(allUsersName)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    DraftInput draftInput = new DraftInput();
    draftInput.line = 1;
    draftInput.message = "nit: trailing whitespace";
    draftInput.path = Patch.COMMIT_MSG;
    gApi.changes().id(cd3.getId().get()).current().createDraft(draftInput);
    String draftCommentRef = RefNames.refsDraftComments(cd3.getId(), user.id());

    // user can see the draft comment ref of the own draft comment
    assertThat(lsRemote(allUsersName, user)).contains(draftCommentRef);

    // user2 can't see the draft comment ref of user's draft comment
    assertThat(lsRemote(allUsersName, accountCreator.user2())).doesNotContain(draftCommentRef);
  }

  @Test
  public void advertisedReferencesOmitStarredChangesRefsOfOtherUsers() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(allUsersName)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(REGISTERED_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().starChange(cd3.getId().toString());
    String starredChangesRef = RefNames.refsStarredChanges(cd3.getId(), user.id());

    // user can see the starred changes ref of the own star
    assertThat(lsRemote(allUsersName, user)).contains(starredChangesRef);

    // user2 can't see the starred changes ref of admin's star
    assertThat(lsRemote(allUsersName, accountCreator.user2())).doesNotContain(starredChangesRef);
  }

  @Test
  public void hideMetadata() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();
    // create change
    TestRepository<?> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_USERS_SELF + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit.Result mr =
        pushFactory
            .create(admin.newIdent(), allUsersRepo)
            .to("refs/for/" + RefNames.REFS_USERS_SELF);
    mr.assertOkStatus();

    List<String> expectedNonMetaRefs =
        ImmutableList.of(
            RefNames.refsUsers(admin.id()),
            RefNames.refsUsers(user.id()),
            RefNames.REFS_EXTERNAL_IDS,
            RefNames.REFS_GROUPNAMES,
            RefNames.refsGroups(admins),
            RefNames.refsGroups(nonInteractiveUsers),
            RefNames.REFS_SEQUENCES + Sequences.NAME_ACCOUNTS,
            RefNames.REFS_SEQUENCES + Sequences.NAME_GROUPS,
            RefNames.REFS_CONFIG,
            Constants.HEAD);

    List<String> expectedMetaRefs =
        new ArrayList<>(ImmutableList.of(mr.getPatchSetId().toRefName()));
    expectedMetaRefs.add(RefNames.changeMetaRef(mr.getChange().getId()));

    List<String> expectedAllRefs = new ArrayList<>(expectedNonMetaRefs);
    expectedAllRefs.addAll(expectedMetaRefs);

    try (Repository repo = repoManager.openRepository(allUsers)) {
      PermissionBackend.ForProject forProject = newFilter(allUsers, admin);
      assertThat(
              names(
                  forProject.filter(
                      repo.getRefDatabase().getRefs(), repo, RefFilterOptions.defaults())))
          .containsExactlyElementsIn(expectedAllRefs);
      assertThat(
              names(
                  forProject.filter(
                      repo.getRefDatabase().getRefs(),
                      repo,
                      RefFilterOptions.builder().setFilterMeta(true).build())))
          .containsExactlyElementsIn(expectedNonMetaRefs);
    }
  }

  @Test
  public void fetchSingleChangeWithoutIndexAccess() throws Exception {
    PushOneCommit.Result change = createChange();
    String patchSetRef = change.getPatchSetId().toRefName();
    try (AutoCloseable ignored = disableChangeIndex();
        Repository repo = repoManager.openRepository(project)) {
      Collection<Ref> singleRef = ImmutableList.of(repo.exactRef(patchSetRef));
      Collection<Ref> filteredRefs =
          permissionBackend
              .user(user(admin))
              .project(project)
              .filter(singleRef, repo, RefFilterOptions.defaults());
      assertThat(filteredRefs).isEqualTo(singleRef);
    }
  }

  private List<String> lsRemote(Project.NameKey p, TestAccount a) throws Exception {
    TestRepository<?> testRepository = cloneProject(p, a);
    try (Git git = testRepository.git()) {
      return git.lsRemote().call().stream().map(Ref::getName).collect(toList());
    }
  }

  private List<String> getRefs(Git git) throws Exception {
    return getRefs(git, x -> true);
  }

  private List<String> getUserRefs(Git git) throws Exception {
    return getRefs(git, RefNames::isRefsUsers);
  }

  private List<String> getGroupRefs(Git git) throws Exception {
    return getRefs(git, RefNames::isRefsGroups);
  }

  private List<String> getRefs(Git git, Predicate<String> predicate) throws Exception {
    return git.lsRemote().call().stream().map(Ref::getName).filter(predicate).collect(toList());
  }

  /**
   * Assert that refs seen by a non-admin user match the expected refs.
   *
   * @param expectedRefs expected refs.
   * @throws Exception
   */
  private void assertUploadPackRefs(String... expectedRefs) throws Exception {
    assertRefs(project, user, true, expectedRefs);
  }

  private void assertRefs(
      Project.NameKey project, TestAccount user, boolean disableDb, String... expectedRefs)
      throws Exception {
    AutoCloseable ctx = null;
    if (disableDb) {
      ctx = disableNoteDb();
    }
    try {
      assertThat(lsRemote(project, user)).containsExactlyElementsIn(expectedRefs);
    } finally {
      if (disableDb) {
        ctx.close();
      }
    }
  }

  private TestRefAdvertiser.Result getReceivePackRefs() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      AdvertiseRefsHook adv =
          ReceiveCommitsAdvertiseRefsHookChain.createForTest(
              queryProvider, project, identifiedUserFactory.create(admin.id()));
      ReceivePack rp = new ReceivePack(repo);
      rp.setAdvertiseRefsHook(adv);
      TestRefAdvertiser advertiser = new TestRefAdvertiser(repo);
      rp.sendAdvertisedRefs(advertiser);
      return advertiser.result();
    }
  }

  private PermissionBackend.ForProject newFilter(Project.NameKey project, TestAccount u) {
    return permissionBackend.user(user(u)).project(project);
  }

  private static ObjectId obj(ChangeData cd, int psNum) throws Exception {
    PatchSet.Id psId = PatchSet.id(cd.getId(), psNum);
    PatchSet ps = cd.patchSet(psId);
    assertWithMessage("%s not found in %s", psId, cd.patchSets()).that(ps).isNotNull();
    return ps.commitId();
  }

  private AccountGroup.UUID createSelfOwnedGroup(String name, TestAccount... members)
      throws RestApiException {
    return createGroup(name, null, members);
  }

  private AccountGroup.UUID createGroup(
      String name, @Nullable AccountGroup.UUID ownerGroup, TestAccount... members)
      throws RestApiException {
    GroupInput groupInput = new GroupInput();
    groupInput.name = name(name);
    groupInput.ownerId = ownerGroup != null ? ownerGroup.get() : null;
    groupInput.members =
        Arrays.stream(members).map(m -> String.valueOf(m.id().get())).collect(toList());
    return AccountGroup.uuid(gApi.groups().create(groupInput).get().id);
  }

  private static Collection<String> names(Collection<Ref> refs) {
    return refs.stream().map(Ref::getName).collect(toImmutableList());
  }
}
