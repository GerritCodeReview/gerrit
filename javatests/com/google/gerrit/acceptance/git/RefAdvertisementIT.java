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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.fetch;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.receive.ReceiveCommitsAdvertiseRefsHook;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class RefAdvertisementIT extends AbstractDaemonTest {
  @Inject private AllUsersName allUsersName;
  @Inject private ChangeNoteUtil noteUtil;
  @Inject private PermissionBackend permissionBackend;
  @Inject private RequestScopeOperations requestScopeOperations;

  private AccountGroup.UUID admins;
  private AccountGroup.UUID nonInteractiveUsers;

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
      for (AccessSection sec : u.getConfig().getAccessSections()) {
        sec.removePermission(Permission.READ);
      }
      Util.allow(u.getConfig(), Permission.READ, admins, "refs/*");
      u.save();
    }

    // Remove all read permissions on All-Users.
    try (ProjectConfigUpdate u = updateProject(allUsers)) {
      for (AccessSection sec : u.getConfig().getAccessSections()) {
        sec.removePermission(Permission.READ);
      }
      u.save();
    }
  }

  private void setUpChanges() throws Exception {
    gApi.projects().name(project.get()).branch("branch").create(new BranchInput());

    // First 2 changes are merged, which means the tags pointing to them are
    // visible.
    allow("refs/for/refs/heads/*", Permission.SUBMIT, admins);
    PushOneCommit.Result mr =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master%submit");
    mr.assertOkStatus();
    cd1 = mr.getChange();
    psRef1 = cd1.currentPatchSet().getId().toRefName();
    metaRef1 = RefNames.changeMetaRef(cd1.getId());
    PushOneCommit.Result br =
        pushFactory.create(admin.newIdent(), testRepo).to("refs/for/branch%submit");
    br.assertOkStatus();
    cd2 = br.getChange();
    psRef2 = cd2.currentPatchSet().getId().toRefName();
    metaRef2 = RefNames.changeMetaRef(cd2.getId());

    // Second 2 changes are unmerged.
    mr = pushFactory.create(admin.newIdent(), testRepo).to("refs/for/master");
    mr.assertOkStatus();
    cd3 = mr.getChange();
    psRef3 = cd3.currentPatchSet().getId().toRefName();
    metaRef3 = RefNames.changeMetaRef(cd3.getId());
    br = pushFactory.create(admin.newIdent(), testRepo).to("refs/for/branch");
    br.assertOkStatus();
    cd4 = br.getChange();
    psRef4 = cd4.currentPatchSet().getId().toRefName();
    metaRef4 = RefNames.changeMetaRef(cd4.getId());

    try (Repository repo = repoManager.openRepository(project)) {
      // master-tag -> master
      RefUpdate mtu = repo.updateRef("refs/tags/master-tag");
      mtu.setExpectedOldObjectId(ObjectId.zeroId());
      mtu.setNewObjectId(repo.exactRef("refs/heads/master").getObjectId());
      assertThat(mtu.update()).isEqualTo(RefUpdate.Result.NEW);

      // branch-tag -> branch
      RefUpdate btu = repo.updateRef("refs/tags/branch-tag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(repo.exactRef("refs/heads/branch").getObjectId());
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);
    }
  }

  @Test
  public void uploadPackAllRefsVisibleNoRefsMetaConfig() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      Util.allow(u.getConfig(), Permission.READ, REGISTERED_USERS, "refs/*");
      Util.allow(u.getConfig(), Permission.READ, admins, RefNames.REFS_CONFIG);
      Util.doNotInherit(u.getConfig(), Permission.READ, RefNames.REFS_CONFIG);
      u.save();
    }

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
  }

  @Test
  public void uploadPackAllRefsVisibleWithRefsMetaConfig() throws Exception {
    allow("refs/*", Permission.READ, REGISTERED_USERS);
    allow(RefNames.REFS_CONFIG, Permission.READ, REGISTERED_USERS);

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
        "refs/tags/master-tag");
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleIncludingHead() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);
    deny("refs/heads/branch", Permission.READ, REGISTERED_USERS);

    requestScopeOperations.setApiUser(user.id());
    assertUploadPackRefs(
        "HEAD", psRef1, metaRef1, psRef3, metaRef3, "refs/heads/master", "refs/tags/master-tag");
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleNotIncludingHead() throws Exception {
    deny("refs/heads/master", Permission.READ, REGISTERED_USERS);
    allow("refs/heads/branch", Permission.READ, REGISTERED_USERS);

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
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleWithEdit() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);

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
  }

  @Test
  public void uploadPackSubsetOfBranchesAndEditsVisibleWithViewPrivateChanges() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);
    allow("refs/*", Permission.VIEW_PRIVATE_CHANGES, REGISTERED_USERS);

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
  }

  @Test
  public void uploadPackSubsetOfRefsVisibleWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    deny("refs/heads/master", Permission.READ, REGISTERED_USERS);
    allow("refs/heads/branch", Permission.READ, REGISTERED_USERS);

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
    allow("refs/heads/*", Permission.READ, REGISTERED_USERS);

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
  }

  @Test
  public void uploadPackSequencesWithAccessDatabase() throws Exception {
    assertRefs(allProjects, user, true);

    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    assertRefs(allProjects, user, true, "refs/sequences/changes");
  }

  @Test
  public void uploadPackAllRefsAreVisibleOrphanedTag() throws Exception {
    allow("refs/*", Permission.READ, REGISTERED_USERS);
    // Delete the pending change on 'branch' and 'branch' itself so that the tag gets orphaned
    gApi.changes().id(cd4.getId().id).delete();
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
        "refs/tags/master-tag");
  }

  @Test
  public void receivePackListsOpenChangesAsAdditionalHaves() throws Exception {
    ReceiveCommitsAdvertiseRefsHook.Result r = getReceivePackRefs();
    assertThat(r.allRefs().keySet())
        .containsExactly(
            // meta refs are excluded
            "HEAD",
            "refs/heads/branch",
            "refs/heads/master",
            "refs/meta/config",
            "refs/tags/branch-tag",
            "refs/tags/master-tag");
    assertThat(r.additionalHaves()).containsExactly(obj(cd3, 1), obj(cd4, 1));
  }

  @Test
  public void receivePackRespectsVisibilityOfOpenChanges() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);
    deny("refs/heads/branch", Permission.READ, REGISTERED_USERS);
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
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      String subject = "Subject for missing commit";
      Change c = new Change(cd3.change());
      PatchSet.Id psId = new PatchSet.Id(cd3.getId(), 2);
      c.setCurrentPatchSet(psId, subject, c.getOriginalSubject());

      PersonIdent committer = serverIdent.get();
      PersonIdent author =
          noteUtil.newIdent(getAccount(admin.id()), committer.getWhen(), committer);
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
    allow(allUsersName, RefNames.REFS_USERS + "*", Permission.READ, REGISTERED_USERS);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getUserRefs(git))
          .containsExactly(RefNames.REFS_USERS_SELF, RefNames.refsUsers(user.id()));
    }
  }

  @Test
  public void advertisedReferencesIncludeAllUserBranchesWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
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
    allow(allUsersName, RefNames.REFS_GROUPS + "*", Permission.READ, REGISTERED_USERS);
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
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
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
    allow(allUsersName, RefNames.REFS_GROUPS + "*", Permission.READ, REGISTERED_USERS);
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ADMINISTRATE_SERVER);
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
    allow(allUsersName, RefNames.REFS + "*", Permission.READ, REGISTERED_USERS);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getRefs(git)).containsNoneOf(RefNames.REFS_EXTERNAL_IDS, RefNames.REFS_GROUPNAMES);
    }
  }

  @Test
  public void advertisedReferencesOmitPrivateChangesOfOtherUsers() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = cd3.currentPatchSet().getRefName();
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
    allow("refs/*", Permission.READ, REGISTERED_USERS);

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = cd3.currentPatchSet().getRefName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(cd3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).contains(change3RefName);
    }
  }

  @Test
  @GerritConfig(name = "auth.skipFullRefEvaluationIfAllRefsAreVisible", value = "false")
  public void advertisedReferencesOmitPrivateChangesOfOtherUsersWhenShortcutDisabled()
      throws Exception {
    allow("refs/*", Permission.READ, REGISTERED_USERS);

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = cd3.currentPatchSet().getRefName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(cd3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).doesNotContain(change3RefName);
    }
  }

  @Test
  public void advertisedReferencesOmitDraftCommentRefsOfOtherUsers() throws Exception {
    allow(project, "refs/*", Permission.READ, REGISTERED_USERS);
    allow(allUsersName, "refs/*", Permission.READ, REGISTERED_USERS);

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
    allow(project, "refs/*", Permission.READ, REGISTERED_USERS);
    allow(allUsersName, "refs/*", Permission.READ, REGISTERED_USERS);

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
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
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
            RefNames.REFS_USERS_SELF,
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
      Map<String, Ref> all = getAllRefs(repo);

      PermissionBackend.ForProject forProject = newFilter(allUsers, admin);
      assertThat(forProject.filter(all, repo, RefFilterOptions.defaults()).keySet())
          .containsExactlyElementsIn(expectedAllRefs);
      assertThat(
              forProject
                  .filter(all, repo, RefFilterOptions.builder().setFilterMeta(true).build())
                  .keySet())
          .containsExactlyElementsIn(expectedNonMetaRefs);
    }
  }

  @Test
  public void fetchSingleChangeWithoutIndexAccess() throws Exception {
    PushOneCommit.Result change = createChange();
    String patchSetRef = change.getPatchSetId().toRefName();
    try (AutoCloseable ignored = disableChangeIndex();
        Repository repo = repoManager.openRepository(project)) {
      Map<String, Ref> singleRef = ImmutableMap.of(patchSetRef, repo.exactRef(patchSetRef));
      Map<String, Ref> filteredRefs =
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
    return getRefs(git, Predicates.alwaysTrue());
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

  private ReceiveCommitsAdvertiseRefsHook.Result getReceivePackRefs() throws Exception {
    ReceiveCommitsAdvertiseRefsHook hook =
        new ReceiveCommitsAdvertiseRefsHook(queryProvider, project);
    try (Repository repo = repoManager.openRepository(project)) {
      return hook.advertiseRefs(getAllRefs(repo));
    }
  }

  private PermissionBackend.ForProject newFilter(Project.NameKey project, TestAccount u) {
    return permissionBackend.user(user(u)).project(project);
  }

  private static ObjectId obj(ChangeData cd, int psNum) throws Exception {
    PatchSet.Id psId = new PatchSet.Id(cd.getId(), psNum);
    PatchSet ps = cd.patchSet(psId);
    assertWithMessage("%s not found in %s", psId, cd.patchSets()).that(ps).isNotNull();
    return ObjectId.fromString(ps.getRevision().get());
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
    return new AccountGroup.UUID(gApi.groups().create(groupInput).get().id);
  }

  private static Map<String, Ref> getAllRefs(Repository repo) throws IOException {
    return repo.getRefDatabase().getRefs().stream()
        .collect(toMap(Ref::getName, Function.identity()));
  }
}
