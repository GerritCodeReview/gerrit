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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.ReceiveCommitsAdvertiseRefsHook;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.notedb.ChangeNoteUtil;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.TestChanges;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class RefAdvertisementIT extends AbstractDaemonTest {
  @Inject private VisibleRefFilter.Factory refFilterFactory;
  @Inject private ChangeNoteUtil noteUtil;
  @Inject @AnonymousCowardName private String anonymousCowardName;

  private AccountGroup.UUID admins;

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
    admins = groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID();
    setUpPermissions();
    setUpChanges();
  }

  private void setUpPermissions() throws Exception {
    // Remove read permissions for all users besides admin. This method is
    // idempotent, so is safe to call on every test setup.
    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    Util.allow(pc, Permission.READ, admins, "refs/*");
    saveProjectConfig(allProjects, pc);
  }

  private static String changeRefPrefix(Change.Id id) {
    String ps = new PatchSet.Id(id, 1).toRefName();
    return ps.substring(0, ps.length() - 1);
  }

  private void setUpChanges() throws Exception {
    gApi.projects().name(project.get()).branch("branch").create(new BranchInput());

    // First 2 changes are merged, which means the tags pointing to them are
    // visible.
    allow(Permission.SUBMIT, admins, "refs/for/refs/heads/*");
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
    allow(Permission.READ, REGISTERED_USERS, "refs/*");
    allow(Permission.READ, REGISTERED_USERS, RefNames.REFS_CONFIG);

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
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
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
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

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
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");

    Change c = notesFactory.createChecked(db, project, c1.getId()).getChange();
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
        "refs/users/01/1000001/edit-" + c1.getId() + "/1");
  }

  @Test
  public void uploadPackSubsetOfBranchesAndEditsVisibleWithViewPrivateChanges() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    allow(Permission.VIEW_PRIVATE_CHANGES, REGISTERED_USERS, "refs/*");

    Change change1 = notesFactory.createChecked(db, project, c1.getId()).getChange();
    String changeId1 = change1.getKey().get();
    Change change2 = notesFactory.createChecked(db, project, c2.getId()).getChange();
    String changeId2 = change2.getKey().get();

    // Admin's edit on change1 is visible.
    setApiUser(admin);
    gApi.changes().id(changeId1).edit().create();

    // Admin's edit on change2 is not visible since user cannot see the change.
    gApi.changes().id(changeId2).edit().create();

    // User's edit is visible.
    setApiUser(user);
    gApi.changes().id(changeId1).edit().create();

    assertUploadPackRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r3 + "1",
        r3 + "meta",
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/users/00/1000000/edit-" + c1.getId() + "/1",
        "refs/users/01/1000001/edit-" + c1.getId() + "/1");
  }

  @Test
  public void uploadPackSubsetOfRefsVisibleWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    try {
      deny("refs/heads/master", Permission.READ, REGISTERED_USERS);
      allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

      String changeId = c1.change().getKey().get();
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
          "refs/users/00/1000000/edit-" + c1.getId() + "/1");
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    }
  }

  @Test
  public void uploadPackDraftRefs() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/*");

    PushOneCommit.Result br =
        pushFactory.create(db, admin.getIdent(), testRepo).to("refs/drafts/master");
    br.assertOkStatus();
    Change.Id c5 = br.getChange().getId();
    String r5 = changeRefPrefix(c5);

    // Only admin can see admin's draft change (5).
    setApiUser(admin);
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
        r5 + "1",
        r5 + "meta",
        "refs/heads/branch",
        "refs/heads/master",
        RefNames.REFS_CONFIG,
        "refs/tags/branch-tag",
        "refs/tags/master-tag");

    // user can't.
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
  public void uploadPackNoSearchingChangeCacheImpl() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/*");

    setApiUser(user);
    try (Repository repo = repoManager.openRepository(project)) {
      assertRefs(
          repo,
          refFilterFactory.create(projectCache.get(project), repo),
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
      setApiUser(user);
      assertRefs(repo, newFilter(repo, allProjects), true);

      allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
      try {
        setApiUser(user);
        assertRefs(repo, newFilter(repo, allProjects), true, "refs/sequences/changes");
      } finally {
        removeGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
      }
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
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
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
            noteUtil.newIdent(
                accountCache.get(admin.getId()).getAccount(),
                committer.getWhen(),
                committer,
                anonymousCowardName);
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
  public void advertisedReferencesOmitPrivateChangesOfOtherUsers() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      LsRemoteCommand lsRemoteCommand = git.lsRemote();
      String change3RefName = c3.currentPatchSet().getRefName();

      List<String> initialRefNames =
          lsRemoteCommand.call().stream().map(Ref::getName).collect(toList());
      assertWithMessage("Precondition violated").that(initialRefNames).contains(change3RefName);

      gApi.changes().id(c3.getId().get()).setPrivate(true, null);

      List<String> refNames = lsRemoteCommand.call().stream().map(Ref::getName).collect(toList());
      assertThat(refNames).doesNotContain(change3RefName);
    }
  }

  @Test
  public void advertisedReferencesIncludePrivateChangesWhenAllRefsMayBeRead() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/*");

    TestRepository<?> userTestRepository = cloneProject(project, user);
    try (Git git = userTestRepository.git()) {
      LsRemoteCommand lsRemoteCommand = git.lsRemote();
      String change3RefName = c3.currentPatchSet().getRefName();

      List<String> initialRefNames =
          lsRemoteCommand.call().stream().map(Ref::getName).collect(toList());
      assertWithMessage("Precondition violated").that(initialRefNames).contains(change3RefName);

      gApi.changes().id(c3.getId().get()).setPrivate(true, null);

      List<String> refNames = lsRemoteCommand.call().stream().map(Ref::getName).collect(toList());
      assertThat(refNames).contains(change3RefName);
    }
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
      assertRefs(
          repo, refFilterFactory.create(projectCache.get(project), repo), true, expectedWithMeta);
    }
  }

  private void assertRefs(
      Repository repo, VisibleRefFilter filter, boolean disableDb, String... expectedWithMeta)
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
      assertThat(filter.filter(all, false).keySet()).containsExactlyElementsIn(expected);
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

  private VisibleRefFilter newFilter(Repository repo, Project.NameKey project) {
    return refFilterFactory.create(projectCache.get(project), repo);
  }

  private static ObjectId obj(ChangeData cd, int psNum) throws Exception {
    PatchSet.Id psId = new PatchSet.Id(cd.getId(), psNum);
    PatchSet ps = cd.patchSet(psId);
    assertWithMessage("%s not found in %s", psId, cd.patchSets()).that(ps).isNotNull();
    return ObjectId.fromString(ps.getRevision().get());
  }
}
