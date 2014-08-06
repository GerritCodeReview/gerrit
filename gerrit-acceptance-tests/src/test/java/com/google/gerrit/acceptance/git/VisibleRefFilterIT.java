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

import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Ordering;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class VisibleRefFilterIT extends AbstractDaemonTest {
  @Inject
  AllProjectsName allProjects;

  @Before
  public void setUp() throws Exception {
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

    pushFactory.create(db, admin.getIdent())
        .to(git, "refs/for/master")
        .assertOkStatus();
    pushFactory.create(db, admin.getIdent())
        .to(git, "refs/for/branch")
        .assertOkStatus();
  }

  @Test
  public void allRefsVisibleNoRefsMetaConfig() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    Util.allow(cfg, Permission.READ, PROJECT_OWNERS, "refs/meta/config");
    Util.doNotInherit(cfg, Permission.READ, "refs/meta/config");
    saveProjectConfig(project, cfg);

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/changes/02/2/1",
        "refs/heads/branch",
        "refs/heads/master");
  }

  @Test
  public void allRefsVisibleWithRefsMetaConfig() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/*");
    allow(Permission.READ, REGISTERED_USERS, "refs/meta/config");

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/changes/02/2/1",
        "refs/heads/branch",
        "refs/heads/master",
        "refs/meta/config");
  }

  @Test
  public void subsetOfBranchesVisibleIncludingHead() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    assertRefs(
        "HEAD",
        "refs/changes/01/1/1",
        "refs/heads/master");
  }

  @Test
  public void subsetOfBranchesVisibleNotIncludingHead() throws Exception {
    deny(Permission.READ, REGISTERED_USERS, "refs/heads/master");
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/branch");

    assertRefs(
        "refs/changes/02/2/1",
        "refs/heads/branch");
  }

  private void assertRefs(String... expected) throws Exception {
    String out = sshSession.exec(String.format(
        "gerrit ls-user-refs -p %s -u %s",
        project.get(), user.getId().get()));
    assertFalse(sshSession.getError(), sshSession.hasError());
    Splitter s = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings();
    assertEquals(Arrays.asList(expected),
        Ordering.natural().sortedCopy(s.split(out)));
  }
}
