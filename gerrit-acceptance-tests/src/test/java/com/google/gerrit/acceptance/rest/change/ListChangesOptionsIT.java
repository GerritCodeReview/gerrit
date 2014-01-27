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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.extensions.common.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.common.ListChangesOption.CURRENT_REVISION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ListChangesOptionsIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  protected PushOneCommit.Factory pushFactory;

  private Project.NameKey project;
  private Git git;
  private ReviewDb db;
  private String changeId;
  private List<PushOneCommit.Result> results;

  @Before
  public void setUp() throws Exception {
    project = new Project.NameKey("p");
    db = reviewDbProvider.open();
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());

    results = Lists.newArrayList();
    results.add(push("file contents", null));
    changeId = results.get(0).getChangeId();
    results.add(push("new contents 1", changeId));
    results.add(push("new contents 2", changeId));
  }

  private PushOneCommit.Result push(String content, String baseChangeId)
      throws Exception {
    String subject = "Change subject";
    String fileName = "a.txt";
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), subject, fileName, content, baseChangeId);
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    r.assertOkStatus();
    return r;
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void noRevisionOptions() throws Exception {
    ChangeInfo c = getChange(changeId);
    assertNull(c.current_revision);
    assertNull(c.revisions);
  }

  @Test
  public void currentRevision() throws Exception {
    ChangeInfo c = getChange(changeId, CURRENT_REVISION);
    assertEquals(commitId(2), c.current_revision);
    assertEquals(ImmutableSet.of(commitId(2)), c.revisions.keySet());
    assertEquals(3, c.revisions.get(commitId(2))._number);
  }

  @Test
  public void allRevisions() throws Exception {
    ChangeInfo c = getChange(changeId, ALL_REVISIONS);
    assertEquals(commitId(2), c.current_revision);
    assertEquals(ImmutableSet.of(commitId(0), commitId(1), commitId(2)),
        c.revisions.keySet());
    assertEquals(1, c.revisions.get(commitId(0))._number);
    assertEquals(2, c.revisions.get(commitId(1))._number);
    assertEquals(3, c.revisions.get(commitId(2))._number);
  }

  private String commitId(int i) {
    return results.get(i).getCommitId().name();
  }
}
