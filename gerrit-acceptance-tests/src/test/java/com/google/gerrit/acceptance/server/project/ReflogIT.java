// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.common.data.Permission.OWNER;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.api.projects.ReflogEntryInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

public class ReflogIT extends AbstractDaemonTest {
  private Project.NameKey testProject;

  @Before
  public void createProject() throws Exception {
    ProjectInput in = new ProjectInput();
    in.createEmptyCommit = true;
    in.name = name("reflog-project");
    testProject = new Project.NameKey(gApi.projects().create(in).get().name);
  }

  @Test
  @UseLocalDisk
  public void reflogUpdatedBySubmittingChange() throws Exception {
    List<ReflogEntryInfo> reflog = getReflog();
    assertThat(reflog).isNotEmpty();

    // Current number of entries in the reflog
    int refLogLen = reflog.size();

    // Create and submit a change
    TestRepository<InMemoryRepository> repo = cloneProject(testProject);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    String changeId = r.getChangeId();
    String revision = r.getCommit().name();
    ReviewInput in = ReviewInput.approve();
    gApi.changes().id(changeId).revision(revision).review(in);
    gApi.changes().id(changeId).revision(revision).submit();

    // Submitting the change causes a new entry in the reflog
    reflog = getReflog();
    assertThat(reflog).hasSize(refLogLen + 1);
  }

  @Test
  @UseLocalDisk
  public void regularUserIsNotAllowedToGetReflog() throws Exception {
    setApiUser(user);
    exception.expect(AuthException.class);
    getReflog();
  }

  @Test
  @UseLocalDisk
  public void ownerUserIsAllowedToGetReflog() throws Exception {
    GroupApi groupApi = gApi.groups().create(name("reflog-project-owners"));
    groupApi.addMembers("user");

    ProjectConfig cfg = projectCache.checkedGet(testProject).getConfig();
    Util.allow(cfg, OWNER, new AccountGroup.UUID(groupApi.get().id), "refs/*");
    saveProjectConfig(testProject, cfg);

    setApiUser(user);
    getReflog();
  }

  @Test
  @UseLocalDisk
  public void adminUserIsAllowedToGetReflog() throws Exception {
    setApiUser(admin);
    getReflog();
  }

  private List<ReflogEntryInfo> getReflog() throws Exception {
    return gApi.projects().name(testProject.get()).branch("master").reflog();
  }
}
