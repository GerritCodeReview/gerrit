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

import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
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
public class VisibleRefFilterIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbWriteEnabled() {
    Config cfg = new Config();
    cfg.setBoolean("notedb", null, "write", true);
    return cfg;
  }

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private NotesMigration notesMigration;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private GroupCache groupCache;

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
    PushOneCommit.Result mr = pushFactory.create(db, admin.getIdent())
        .to(git, "refs/for/master%submit");
    mr.assertOkStatus();
    PushOneCommit.Result br = pushFactory.create(db, admin.getIdent())
        .to(git, "refs/for/branch%submit");
    br.assertOkStatus();

    Repository repo = repoManager.openRepository(project);
    try {
      // master-tag -> master
      RefUpdate mtu = repo.updateRef("refs/tags/master-tag");
      mtu.setExpectedOldObjectId(ObjectId.zeroId());
      mtu.setNewObjectId(repo.getRef("refs/heads/master").getObjectId());
      assertEquals(RefUpdate.Result.NEW, mtu.update());

      // branch-tag -> branch
      RefUpdate btu = repo.updateRef("refs/tags/branch-tag");
      btu.setExpectedOldObjectId(ObjectId.zeroId());
      btu.setNewObjectId(repo.getRef("refs/heads/branch").getObjectId());
      assertEquals(RefUpdate.Result.NEW, btu.update());
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
    assertFalse(sshSession.getError(), sshSession.hasError());

    List<String> filtered = new ArrayList<>(expected.length);
    for (String r : expected) {
      if (notesMigration.write() || !r.endsWith(RefNames.META_SUFFIX)) {
        filtered.add(r);
      }
    }

    Splitter s = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
    assertEquals(filtered, Ordering.natural().sortedCopy(s.split(out)));
  }
}
