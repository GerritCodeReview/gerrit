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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.testutil.DisabledReviewDb;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@NoHttpd
public class VisibleRefFilterIT extends AbstractDaemonTest {
  @Inject
  private ChangeEditModifier editModifier;

  @Inject
  private ProjectControl.GenericFactory projectControlFactory;

  @Inject
  @Nullable
  private SearchingChangeCacheImpl changeCache;

  @Inject
  private TagCache tagCache;

  @Inject
  private Provider<CurrentUser> userProvider;

  private AccountGroup.UUID admins;

  private Change.Id c1;
  private Change.Id c2;
  private String r1;
  private String r2;

  @Before
  public void setUp() throws Exception {
    admins = groupCache.get(new AccountGroup.NameKey("Administrators"))
        .getGroupUUID();
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
    gApi.projects()
        .name(project.get())
        .branch("branch")
        .create(new BranchInput());

    allow(Permission.SUBMIT, admins, "refs/for/refs/heads/*");
    PushOneCommit.Result mr = pushFactory.create(db, admin.getIdent(), testRepo)
        .to("refs/for/master%submit");
    mr.assertOkStatus();
    c1 = mr.getChange().getId();
    r1 = changeRefPrefix(c1);
    PushOneCommit.Result br = pushFactory.create(db, admin.getIdent(), testRepo)
        .to("refs/for/branch%submit");
    br.assertOkStatus();
    c2 = br.getChange().getId();
    r2 = changeRefPrefix(c2);

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
  public void allRefsVisibleNoRefsMetaConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    Util.allow(cfg, Permission.READ, admins, RefNames.REFS_CONFIG);
    Util.doNotInherit(cfg, Permission.READ, RefNames.REFS_CONFIG);
    saveProjectConfig(project, cfg);

    setApiUser(user);
    assertRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        "refs/heads/branch",
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  @Test
  public void allRefsVisibleWithRefsMetaConfig() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/*");
    allow(Permission.READ, REGISTERED_USERS, RefNames.REFS_CONFIG);

    assertRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        "refs/heads/branch",
        "refs/heads/master",
        RefNames.REFS_CONFIG,
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  @Test
  public void subsetOfBranchesVisibleIncludingHead() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    setApiUser(user);
    assertRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        "refs/heads/master",
        "refs/tags/master-tag");
  }

  @Test
  public void subsetOfBranchesVisibleNotIncludingHead() throws Exception {
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    setApiUser(user);
    assertRefs(
        r2 + "1",
        r2 + "meta",
        "refs/heads/branch",
        "refs/tags/branch-tag",
        // master branch is not visible but master-tag is reachable from branch
        // (since PushOneCommit always bases changes on each other).
        "refs/tags/master-tag");
  }

  @Test
  public void subsetOfBranchesVisibleWithEdit() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    Change c = notesFactory.createChecked(db, project, c1).getChange();
    PatchSet ps1 = getPatchSet(new PatchSet.Id(c1, 1));

    // Admin's edit is not visible.
    setApiUser(admin);
    editModifier.createEdit(c, ps1);

    // User's edit is visible.
    setApiUser(user);
    editModifier.createEdit(c, ps1);

    assertRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/users/01/1000001/edit-" + c1.get() + "/1");
  }

  @Test
  public void subsetOfRefsVisibleWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    try {
      deny(Permission.READ, REGISTERED_USERS, "refs/heads/master");
      allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

      Change c = notesFactory.createChecked(db, project, c1).getChange();
      PatchSet ps1 = getPatchSet(new PatchSet.Id(c1, 1));
      setApiUser(admin);
      editModifier.createEdit(c, ps1);
      setApiUser(user);
      editModifier.createEdit(c, ps1);

      assertRefs(
          // Change 1 is visible due to accessDatabase capability, even though
          // refs/heads/master is not.
          r1 + "1",
          r1 + "meta",
          r2 + "1",
          r2 + "meta",
          "refs/heads/branch",
          "refs/tags/branch-tag",
          // See comment in subsetOfBranchesVisibleNotIncludingHead.
          "refs/tags/master-tag",
          // All edits are visible due to accessDatabase capability.
          "refs/users/00/1000000/edit-" + c1.get() + "/1",
          "refs/users/01/1000001/edit-" + c1.get() + "/1");
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    }
  }

  @Test
  public void draftRefs() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/*");

    PushOneCommit.Result br = pushFactory.create(db, admin.getIdent(), testRepo)
        .to("refs/drafts/master");
    br.assertOkStatus();
    Change.Id c3 = br.getChange().getId();
    String r3 = changeRefPrefix(c3);

    // Only admin can see admin's draft change.
    setApiUser(admin);
    assertRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        r3 + "1",
        r3 + "meta",
        "refs/heads/branch",
        "refs/heads/master",
        RefNames.REFS_CONFIG,
        "refs/tags/branch-tag",
        "refs/tags/master-tag");

    // user can't.
    setApiUser(user);
    assertRefs(
        "HEAD",
        r1 + "1",
        r1 + "meta",
        r2 + "1",
        r2 + "meta",
        "refs/heads/branch",
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  @Test
  public void noSearchingChangeCacheImpl() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/*");

    setApiUser(user);
    try (Repository repo = repoManager.openRepository(project)) {
      assertRefs(
          repo,
          new VisibleRefFilter(tagCache, notesFactory, null, repo,
              projectControl(), db, true),
          // Can't use stored values from the index so DB must be enabled.
          false,
          "HEAD",
          r1 + "1",
          r1 + "meta",
          r2 + "1",
          r2 + "meta",
          "refs/heads/branch",
          "refs/heads/master",
          "refs/tags/branch-tag",
          "refs/tags/master-tag");
    }
  }

  /**
   * Assert that refs seen by a non-admin user match expected.
   *
   * @param expectedWithMeta expected refs, in order. If NoteDb is disabled by
   *     the configuration, any NoteDb refs (i.e. ending in "/meta") are removed
   *     from the expected list before comparing to the actual results.
   * @throws Exception
   */
  private void assertRefs(String... expectedWithMeta) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      assertRefs(
          repo,
          new VisibleRefFilter(tagCache, notesFactory, changeCache, repo,
              projectControl(), new DisabledReviewDb(), true),
          true,
          expectedWithMeta);
    }
  }

  private void assertRefs(Repository repo, VisibleRefFilter filter,
      boolean disableDb, String... expectedWithMeta) throws Exception {
    List<String> expected = new ArrayList<>(expectedWithMeta.length);
    for (String r : expectedWithMeta) {
      if (notesMigration.writeChanges() || !r.endsWith(RefNames.META_SUFFIX)) {
        expected.add(r);
      }
    }

    AcceptanceTestRequestScope.Context ctx = null;
    if (disableDb) {
      ctx = disableDb();
    }
    try {
      Map<String, Ref> all = repo.getAllRefs();
      assertThat(filter.filter(all, false).keySet())
          .containsExactlyElementsIn(expected);
    } finally {
      if (disableDb) {
        enableDb(ctx);
      }
    }
  }

  private ProjectControl projectControl() throws Exception {
    return projectControlFactory.controlFor(project, userProvider.get());
  }
}
