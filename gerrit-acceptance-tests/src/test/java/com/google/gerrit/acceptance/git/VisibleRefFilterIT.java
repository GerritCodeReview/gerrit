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
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(ConfigSuite.class)
@NoHttpd
public class VisibleRefFilterIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbWriteEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("notedb", "changes", "write", true);
    return cfg;
  }

  @Inject
  private NotesMigration notesMigration;

  @Inject
  private ChangeEditModifier editModifier;

  private AccountGroup.UUID admins;

  @Before
  public void setUp() throws Exception {
    admins = groupCache.get(new AccountGroup.NameKey("Administrators"))
        .getGroupUUID();
    setUpChanges();
    setUpPermissions();
  }

  private void setUpPermissions() throws Exception {
    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    saveProjectConfig(allProjects, pc);
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
    PushOneCommit.Result br = pushFactory.create(db, admin.getIdent(), testRepo)
        .to("refs/for/branch%submit");
    br.assertOkStatus();

    Repository repo = repoManager.openRepository(project);
    try {
      // master-tag -> master
      RefUpdate mtu = repo.updateRef("refs/tags/master-tag");
      mtu.setExpectedOldObjectId(ObjectId.zeroId());
      mtu.setNewObjectId(repo.getRef("refs/heads/master").getObjectId());
      assertThat(mtu.update()).isEqualTo(RefUpdate.Result.NEW);

      // branch-tag -> branch
      RefUpdate btu = repo.updateRef("refs/tags/branch-tag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(repo.getRef("refs/heads/branch").getObjectId());
      assertThat(btu.update()).isEqualTo(RefUpdate.Result.NEW);
    } finally {
      repo.close();
    }
  }

  @Test
  public void allRefsVisibleNoRefsMetaConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    Util.allow(cfg, Permission.READ, admins, "refs/meta/config");
    Util.doNotInherit(cfg, Permission.READ, "refs/meta/config");
    saveProjectConfig(project, cfg);

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/changes/01/1/meta",
        "refs/changes/02/2/1",
        "refs/changes/02/2/meta",
        "refs/heads/branch",
        "refs/heads/master",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  @Test
  public void allRefsVisibleWithRefsMetaConfig() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/*");
    allow(Permission.READ, REGISTERED_USERS, "refs/meta/config");

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/changes/01/1/meta",
        "refs/changes/02/2/1",
        "refs/changes/02/2/meta",
        "refs/heads/branch",
        "refs/heads/master",
        "refs/meta/config",
        "refs/tags/branch-tag",
        "refs/tags/master-tag");
  }

  @Test
  public void subsetOfBranchesVisibleIncludingHead() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/changes/01/1/meta",
        "refs/heads/master",
        "refs/tags/master-tag");
  }

  @Test
  public void subsetOfBranchesVisibleNotIncludingHead() throws Exception {
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    assertRefs(
        "refs/changes/02/2/1",
        "refs/changes/02/2/meta",
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

    Change c1 = db.changes().get(new Change.Id(1));
    PatchSet ps1 = db.patchSets().get(new PatchSet.Id(c1.getId(), 1));

    // Admin's edit is not visible.
    setApiUser(admin);
    editModifier.createEdit(c1, ps1);

    // User's edit is visible.
    setApiUser(user);
    editModifier.createEdit(c1, ps1);

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/changes/01/1/meta",
        "refs/heads/master",
        "refs/tags/master-tag",
        "refs/users/01/1000001/edit-1/1");
  }

  @Test
  public void subsetOfRefsVisibleWithAccessDatabase() throws Exception {
    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    try {
      deny(Permission.READ, REGISTERED_USERS, "refs/heads/master");
      allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

      Change c1 = db.changes().get(new Change.Id(1));
      PatchSet ps1 = db.patchSets().get(new PatchSet.Id(c1.getId(), 1));
      setApiUser(admin);
      editModifier.createEdit(c1, ps1);
      setApiUser(user);
      editModifier.createEdit(c1, ps1);

      assertRefs(
          // Change 1 is visible due to accessDatabase capability, even though
          // refs/heads/master is not.
          "refs/changes/01/1/1",
          "refs/changes/01/1/meta",
          "refs/changes/02/2/1",
          "refs/changes/02/2/meta",
          "refs/heads/branch",
          "refs/tags/branch-tag",
          // See comment in subsetOfBranchesVisibleNotIncludingHead.
          "refs/tags/master-tag",
          // All edits are visible due to accessDatabase capability.
          "refs/users/00/1000000/edit-1/1",
          "refs/users/01/1000001/edit-1/1");
    } finally {
      removeGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    }
  }

  /**
   * Assert that refs seen by a non-admin user match expected.
   *
   * @param expected expected refs, in order. If notedb is disabled by the
   *     configuration, any notedb refs (i.e. ending in "/meta") are removed
   *     from the expected list before comparing to the actual results.
   * @throws Exception
   */
  private void assertRefs(String... expected) throws Exception {
    String out = sshSession.exec(String.format(
        "gerrit ls-user-refs -p %s -u %s",
        project.get(), user.getId().get()));
    assert_().withFailureMessage(sshSession.getError())
      .that(sshSession.hasError()).isFalse();

    List<String> filtered = new ArrayList<>(expected.length);
    for (String r : expected) {
      if (notesMigration.writeChanges() || !r.endsWith(RefNames.META_SUFFIX)) {
        filtered.add(r);
      }
    }

    Splitter s = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
    assertThat(filtered).containsSequence(
        Ordering.natural().sortedCopy(s.split(out)));
  }
}
