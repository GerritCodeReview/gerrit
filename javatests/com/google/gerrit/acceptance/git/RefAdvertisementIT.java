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

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.ProjectResetter;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.AnonymousCowardName;
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
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.git.receive.ReceiveCommitsAdvertiseRefsHook;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.NoteDbMode;
import com.google.gerrit.testing.TestChanges;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.TestRepository;
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
  @Inject private PermissionBackend permissionBackend;
  @Inject private ChangeNoteUtil noteUtil;
  @Inject @AnonymousCowardName private String anonymousCowardName;
  @Inject private AllUsersName allUsersName;

  private AccountGroup.UUID admins;
  private AccountGroup.UUID nonInteractiveUsers;

  private ChangeData c1;
  private ChangeData c2;
  private ChangeData c3;
  private ChangeData c4;
  private String r1;
  private String r2;
  private String r3;
  private String r4;

  @Before
  public void setUp() throws Exception {
    admins = adminGroupUuid();
    nonInteractiveUsers = groupUuid("Non-Interactive Users");
    setUpPermissions();
    setUpChanges();
  }

  private void setUpPermissions() throws Exception {
    // Remove read permissions for all users besides admin. This method is idempotent, so is safe
    // to call on every test setup.
    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    Util.allow(pc, Permission.READ, admins, "refs/*");
    saveProjectConfig(allProjects, pc);

    // Remove all read permissions on All-Users. This method is idempotent, so is safe to call on
    // every test setup.
    pc = projectCache.checkedGet(allUsers).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    saveProjectConfig(allUsers, pc);
  }

  private static String changeRefPrefix(Change.Id id) {
    String ps = new PatchSet.Id(id, 1).toRefName();
    return ps.substring(0, ps.length() - 1);
  }

  private void setUpChanges() throws Exception {
    gApi.projects().name(project.get()).branch("branch").create(new BranchInput());

    // First 2 changes are merged, which means the tags pointing to them are
    // visible.
    allow("refs/for/refs/heads/*", Permission.SUBMIT, admins);
    PushOneCommit.Result mr =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/master%submit");
    mr.assertOkStatus();
    c1 = mr.getChange();
    r1 = changeRefPrefix(c1.getId());
    PushOneCommit.Result br =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/branch%submit");
    br.assertOkStatus();
    c2 = br.getChange();
    r2 = changeRefPrefix(c2.getId());

    // Second 2 changes are unmerged.
    mr = pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/master");
    mr.assertOkStatus();
    c3 = mr.getChange();
    r3 = changeRefPrefix(c3.getId());
    br = pushFactory.create(db, admin.getIdent(), testRepo).to("refs/for/branch");
    br.assertOkStatus();
    c4 = br.getChange();
    r4 = changeRefPrefix(c4.getId());

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
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    Util.allow(cfg, Permission.READ, admins, RefNames.REFS_CONFIG);
    Util.doNotInherit(cfg, Permission.READ, RefNames.REFS_CONFIG);
    saveProjectConfig(project, cfg);

    setApiUser(user);
    assertUploadPackRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        r3 + "1",
        r3 + "meta",
        r4 + "1",
        r4 + "meta",
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
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        r3 + "1",
        r3 + "meta",
        r4 + "1",
        r4 + "meta",
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

    setApiUser(user);
    assertUploadPackRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r3 + "1",
        r3 + "meta",
        "refs/heads/master",
        "refs/tags/master-tag");
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleNotIncludingHead() throws Exception {
    deny("refs/heads/master", Permission.READ, REGISTERED_USERS);
    allow("refs/heads/branch", Permission.READ, REGISTERED_USERS);

    setApiUser(user);
    assertUploadPackRefs(
        r2 + "1",
        r2 + "meta",
        r4 + "1",
        r4 + "meta",
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // master branch is not visible but master-tag is reachable from branch
        // (since PushOneCommit always bases changes on each other).
        "refs/tags/master-tag");
  }

  @Test
  public void uploadPackSubsetOfBranchesVisibleWithEdit() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);

    Change c = notesFactory.createChecked(db, project, c3.getId()).getChange();
    String changeId = c.getKey().get();

    // Admin's edit is not visible.
    setApiUser(admin);
    gApi.changes().id(changeId).edit().create();

    // User's edit is visible.
    setApiUser(user);
    gApi.changes().id(changeId).edit().create();

    assertUploadPackRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r3 + "1",
        r3 + "meta",
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/tags/branch-tag",
        "refs/users/01/1000001/edit-" + c3.getId() + "/1");
  }

  @Test
  public void uploadPackSubsetOfBranchesAndEditsVisibleWithViewPrivateChanges() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);
    allow("refs/*", Permission.VIEW_PRIVATE_CHANGES, REGISTERED_USERS);

    Change change3 = notesFactory.createChecked(db, project, c3.getId()).getChange();
    String changeId3 = change3.getKey().get();
    Change change4 = notesFactory.createChecked(db, project, c4.getId()).getChange();
    String changeId4 = change4.getKey().get();

    // Admin's edit on change3 is visible.
    setApiUser(admin);
    gApi.changes().id(changeId3).edit().create();

    // Admin's edit on change4 is not visible since user cannot see the change.
    gApi.changes().id(changeId4).edit().create();

    // User's edit is visible.
    setApiUser(user);
    gApi.changes().id(changeId3).edit().create();

    assertUploadPackRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r3 + "1",
        r3 + "meta",
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/tags/branch-tag",
        "refs/users/00/1000000/edit-" + c3.getId() + "/1",
        "refs/users/01/1000001/edit-" + c3.getId() + "/1");
  }

  @Test
  public void uploadPackSubsetOfRefsVisibleWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    deny("refs/heads/master", Permission.READ, REGISTERED_USERS);
    allow("refs/heads/branch", Permission.READ, REGISTERED_USERS);

    String changeId = c3.change().getKey().get();
    setApiUser(admin);
    gApi.changes().id(changeId).edit().create();
    setApiUser(user);

    assertUploadPackRefs(
        // Change 1 is visible due to accessDatabase capability, even though
        // refs/heads/master is not.
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        r3 + "1",
        r3 + "meta",
        r4 + "1",
        r4 + "meta",
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // See comment in subsetOfBranchesVisibleNotIncludingHead.
        "refs/tags/master-tag",
        // All edits are visible due to accessDatabase capability.
        "refs/users/00/1000000/edit-" + c3.getId() + "/1");
  }

  @Test
  public void uploadPackNoSearchingChangeCacheImpl() throws Exception {
    allow("refs/heads/*", Permission.READ, REGISTERED_USERS);

    setApiUser(user);
    try (Repository repo = repoManager.openRepository(project)) {
      assertRefs(
          repo,
          permissionBackend.user(user(user)).project(project),
          // Can't use stored values from the index so DB must be enabled.
          false,
          "HEAD",
          r1 + "1",
          r1 + "meta",
          r2 + "1",
          r2 + "meta",
          r3 + "1",
          r3 + "meta",
          r4 + "1",
          r4 + "meta",
          "refs/heads/branch",
          "refs/heads/master",
          "refs/tags/branch-tag",
          "refs/tags/master-tag");
    }
  }

  @Test
  public void uploadPackSequencesWithAccessDatabase() throws Exception {
    assume().that(notesMigration.readChangeSequence()).isTrue();
    try (Repository repo = repoManager.openRepository(allProjects)) {
      assertRefs(repo, newFilter(allProjects, user), true);

      allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
      assertRefs(repo, newFilter(allProjects, user), true, "refs/sequences/changes");
    }
  }

  @Test
  public void receivePackListsOpenChangesAsAdditionalHaves() throws Exception {
    ReceiveCommitsAdvertiseRefsHook.Result r = getReceivePackRefs();
    assertThat(r.allRefs().keySet())
        .containsExactly(
            // meta refs are excluded even when NoteDb is enabled.
            "HEAD",
            "refs/heads/branch",
            "refs/heads/master",
            "refs/meta/config",
            "refs/tags/branch-tag",
            "refs/tags/master-tag");
    assertThat(r.additionalHaves()).containsExactly(obj(c3, 1), obj(c4, 1));
  }

  @Test
  public void receivePackRespectsVisibilityOfOpenChanges() throws Exception {
    allow("refs/heads/master", Permission.READ, REGISTERED_USERS);
    deny("refs/heads/branch", Permission.READ, REGISTERED_USERS);
    setApiUser(user);

    assertThat(getReceivePackRefs().additionalHaves()).containsExactly(obj(c3, 1));
  }

  @Test
  public void receivePackListsOnlyLatestPatchSet() throws Exception {
    testRepo.reset(obj(c3, 1));
    PushOneCommit.Result r = amendChange(c3.change().getKey().get());
    r.assertOkStatus();
    c3 = r.getChange();
    assertThat(getReceivePackRefs().additionalHaves()).containsExactly(obj(c3, 2), obj(c4, 1));
  }

  @Test
  public void receivePackOmitsMissingObject() throws Exception {
    String rev = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      String subject = "Subject for missing commit";
      Change c = new Change(c3.change());
      PatchSet.Id psId = new PatchSet.Id(c3.getId(), 2);
      c.setCurrentPatchSet(psId, subject, c.getOriginalSubject());

      if (notesMigration.changePrimaryStorage() == PrimaryStorage.REVIEW_DB) {
        PatchSet ps = TestChanges.newPatchSet(psId, rev, admin.getId());
        db.patchSets().insert(Collections.singleton(ps));
        db.changes().update(Collections.singleton(c));
      }

      if (notesMigration.commitChangeWrites()) {
        PersonIdent committer = serverIdent.get();
        PersonIdent author =
            noteUtil.newIdent(getAccount(admin.getId()), committer.getWhen(), committer);
        tr.branch(RefNames.changeMetaRef(c3.getId()))
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
      }
      indexer.index(db, c.getProject(), c.getId());
    }

    assertThat(getReceivePackRefs().additionalHaves()).containsExactly(obj(c4, 1));
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
          .containsExactly(RefNames.REFS_USERS_SELF, RefNames.refsUsers(user.id));
    }
  }

  @Test
  public void advertisedReferencesIncludeAllUserBranchesWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    TestRepository<?> userTestRepository = cloneProject(allUsers, user);
    try (Git git = userTestRepository.git()) {
      assertThat(getUserRefs(git))
          .containsExactly(
              RefNames.REFS_USERS_SELF, RefNames.refsUsers(user.id), RefNames.refsUsers(admin.id));
    }
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
  public void advertisedReferencesDontShowGroupBranchToOwnerWithoutRead() throws Exception {
    try (ProjectResetter resetter = resetGroups()) {
      createSelfOwnedGroup("Foos", user);
      TestRepository<?> userTestRepository = cloneProject(allUsers, user);
      try (Git git = userTestRepository.git()) {
        assertThat(getGroupRefs(git)).isEmpty();
      }
    }
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
  public void advertisedReferencesOmitGroupBranchesOfNonOwnedGroups() throws Exception {
    try (ProjectResetter resetter = resetGroups()) {
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
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
  public void advertisedReferencesIncludeAllGroupBranchesWithAccessDatabase() throws Exception {
    try (ProjectResetter resetter = resetGroups()) {
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
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
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
  @GerritConfig(name = "noteDb.groups.write", value = "true")
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
      String change3RefName = c3.currentPatchSet().getRefName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(c3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).doesNotContain(change3RefName);
    }
  }

  @Test
  public void advertisedReferencesIncludePrivateChangesWhenAllRefsMayBeRead() throws Exception {
    allow("refs/*", Permission.READ, REGISTERED_USERS);

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      String change3RefName = c3.currentPatchSet().getRefName();
      assertWithMessage("Precondition violated").that(getRefs(git)).contains(change3RefName);

      gApi.changes().id(c3.getId().get()).setPrivate(true, null);
      assertThat(getRefs(git)).contains(change3RefName);
    }
  }

  @Test
  public void advertisedReferencesOmitDraftCommentRefsOfOtherUsers() throws Exception {
    assume().that(notesMigration.commitChangeWrites()).isTrue();

    allow(project, "refs/*", Permission.READ, REGISTERED_USERS);
    allow(allUsersName, "refs/*", Permission.READ, REGISTERED_USERS);

    setApiUser(user);
    DraftInput draftInput = new DraftInput();
    draftInput.line = 1;
    draftInput.message = "nit: trailing whitespace";
    draftInput.path = Patch.COMMIT_MSG;
    gApi.changes().id(c3.getId().get()).current().createDraft(draftInput);
    String draftCommentRef = RefNames.refsDraftComments(c3.getId(), user.id);

    // user can see the draft comment ref of the own draft comment
    assertThat(lsRemote(allUsersName, user)).contains(draftCommentRef);

    // user2 can't see the draft comment ref of user's draft comment
    assertThat(lsRemote(allUsersName, accountCreator.user2())).doesNotContain(draftCommentRef);
  }

  @Test
  public void advertisedReferencesOmitStarredChangesRefsOfOtherUsers() throws Exception {
    assume().that(notesMigration.commitChangeWrites()).isTrue();

    allow(project, "refs/*", Permission.READ, REGISTERED_USERS);
    allow(allUsersName, "refs/*", Permission.READ, REGISTERED_USERS);

    setApiUser(user);
    gApi.accounts().self().starChange(c3.getId().toString());
    String starredChangesRef = RefNames.refsStarredChanges(c3.getId(), user.id);

    // user can see the starred changes ref of the own star
    assertThat(lsRemote(allUsersName, user)).contains(starredChangesRef);

    // user2 can't see the starred changes ref of admin's star
    assertThat(lsRemote(allUsersName, accountCreator.user2())).doesNotContain(starredChangesRef);
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
  public void hideMetadata() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    // create change
    TestRepository<?> allUsersRepo = cloneProject(allUsers);
    fetch(allUsersRepo, RefNames.REFS_USERS_SELF + ":userRef");
    allUsersRepo.reset("userRef");
    PushOneCommit.Result mr =
        pushFactory
            .create(db, admin.getIdent(), allUsersRepo)
            .to("refs/for/" + RefNames.REFS_USERS_SELF);
    mr.assertOkStatus();

    List<String> expectedNonMetaRefs =
        ImmutableList.of(
            RefNames.REFS_USERS_SELF,
            RefNames.refsUsers(admin.id),
            RefNames.refsUsers(user.id),
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
    if (NoteDbMode.get() != NoteDbMode.OFF) {
      expectedMetaRefs.add(changeRefPrefix(mr.getChange().getId()) + "meta");
    }

    List<String> expectedAllRefs = new ArrayList<>(expectedNonMetaRefs);
    expectedAllRefs.addAll(expectedMetaRefs);

    try (Repository repo = repoManager.openRepository(allUsers)) {
      Map<String, Ref> all = repo.getAllRefs();

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
   * Assert that refs seen by a non-admin user match expected.
   *
   * @param expectedWithMeta expected refs, in order. If NoteDb is disabled by the configuration,
   *     any NoteDb refs (i.e. ending in "/meta") are removed from the expected list before
   *     comparing to the actual results.
   * @throws Exception
   */
  private void assertUploadPackRefs(String... expectedWithMeta) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      assertRefs(repo, permissionBackend.user(user(user)).project(project), true, expectedWithMeta);
    }
  }

  private void assertRefs(
      Repository repo,
      PermissionBackend.ForProject forProject,
      boolean disableDb,
      String... expectedWithMeta)
      throws Exception {
    List<String> expected = new ArrayList<>(expectedWithMeta.length);
    for (String r : expectedWithMeta) {
      if (notesMigration.commitChangeWrites() || !r.endsWith(RefNames.META_SUFFIX)) {
        expected.add(r);
      }
    }

    AcceptanceTestRequestScope.Context ctx = null;
    if (disableDb) {
      ctx = disableDb();
    }
    try {
      Map<String, Ref> all = repo.getAllRefs();
      assertThat(forProject.filter(all, repo, RefFilterOptions.defaults()).keySet())
          .containsExactlyElementsIn(expected);
    } finally {
      if (disableDb) {
        enableDb(ctx);
      }
    }
  }

  private ReceiveCommitsAdvertiseRefsHook.Result getReceivePackRefs() throws Exception {
    ReceiveCommitsAdvertiseRefsHook hook =
        new ReceiveCommitsAdvertiseRefsHook(queryProvider, project);
    try (Repository repo = repoManager.openRepository(project)) {
      return hook.advertiseRefs(repo.getAllRefs());
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
        Arrays.stream(members).map(m -> String.valueOf(m.id.get())).collect(toList());
    return new AccountGroup.UUID(gApi.groups().create(groupInput).get().id);
  }

  /**
   * Create a resetter to reset the group branches in All-Users. This makes the group data between
   * ReviewDb and NoteDb inconsistent, but in the context of this test class we only care about refs
   * and hence this is not an issue. Once groups are no longer in ReviewDb and {@link
   * AbstractDaemonTest#resetProjects} takes care to reset group branches we no longer need this
   * method.
   */
  private ProjectResetter resetGroups() throws IOException {
    return projectResetter
        .builder()
        .build(
            new ProjectResetter.Config()
                .reset(allUsers, RefNames.REFS_GROUPS + "*", RefNames.REFS_GROUPNAMES));
  }
}
