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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.server.git.ProjectConfig;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GetCommitIT extends AbstractDaemonTest {
  private TestRepository<Repository> repo;

  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<>(repoManager.openRepository(project));

    ProjectConfig pc = projectCache.checkedGet(allProjects).getConfig();
    for (AccessSection sec : pc.getAccessSections()) {
      sec.removePermission(Permission.READ);
    }
    saveProjectConfig(allProjects, pc);
  }

  @After
  public void tearDown() throws Exception {
    if (repo != null) {
      repo.getRepository().close();
    }
  }

  @Test
  public void getNonExistingCommit_NotFound() throws Exception {
    assertNotFound(
        ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"));
  }

  @Test
  public void getMergedCommit_Found() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/*");
    RevCommit commit = repo.parseBody(repo.branch("master")
        .commit()
        .message("Create\n\nNew commit\n")
        .create());

    CommitInfo info = getCommit(commit);
    assertThat(info.commit).isEqualTo(commit.name());
    assertThat(info.subject).isEqualTo("Create");
    assertThat(info.message).isEqualTo("Create\n\nNew commit\n");
    assertThat(info.author.name).isEqualTo("J. Author");
    assertThat(info.author.email).isEqualTo("jauthor@example.com");
    assertThat(info.committer.name).isEqualTo("J. Committer");
    assertThat(info.committer.email).isEqualTo("jcommitter@example.com");

    CommitInfo parent = Iterables.getOnlyElement(info.parents);
    assertThat(parent.commit).isEqualTo(commit.getParent(0).name());
    assertThat(parent.subject).isEqualTo("Initial empty repository");
    assertThat(parent.message).isNull();
    assertThat(parent.author).isNull();
    assertThat(parent.committer).isNull();
  }

  @Test
  public void getMergedCommit_NotFound() throws Exception {
    RevCommit commit = repo.parseBody(repo.branch("master")
        .commit()
        .message("Create\n\nNew commit\n")
        .create());
    assertNotFound(commit);
  }

  @Test
  public void getOpenChange_Found() throws Exception {
    allow(Permission.READ, REGISTERED_USERS, "refs/heads/*");
    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), testRepo)
        .to("refs/for/master");
    r.assertOkStatus();

    CommitInfo info = getCommit(r.getCommitId());
    assertThat(info.commit).isEqualTo(r.getCommitId().name());
    assertThat(info.subject).isEqualTo("test commit");
    assertThat(info.message).isEqualTo(
        "test commit\n\nChange-Id: " + r.getChangeId() + "\n");
    assertThat(info.author.name).isEqualTo("admin");
    assertThat(info.author.email).isEqualTo("admin@example.com");
    assertThat(info.committer.name).isEqualTo("admin");
    assertThat(info.committer.email).isEqualTo("admin@example.com");

    CommitInfo parent = Iterables.getOnlyElement(info.parents);
    assertThat(parent.commit).isEqualTo(r.getCommit().getParent(0).name());
    assertThat(parent.subject).isEqualTo("Initial empty repository");
    assertThat(parent.message).isNull();
    assertThat(parent.author).isNull();
    assertThat(parent.committer).isNull();
  }

  @Test
  public void getOpenChange_NotFound() throws Exception {
    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), testRepo)
        .to("refs/for/master");
    r.assertOkStatus();
    assertNotFound(r.getCommitId());
  }

  private void assertNotFound(ObjectId id) throws Exception {
    RestResponse r = userSession.get(
        "/projects/" + project.get() + "/commits/" + id.name());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_NOT_FOUND);
  }

  private CommitInfo getCommit(ObjectId id) throws Exception {
    RestResponse r = userSession.get(
        "/projects/" + project.get() + "/commits/" + id.name());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    CommitInfo result = newGson().fromJson(r.getReader(), CommitInfo.class);
    r.consume();
    return result;
  }
}
