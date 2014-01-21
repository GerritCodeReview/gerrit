// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class StarredChangesIT extends AbstractDaemonTest {

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private PushOneCommit.Factory pushFactory;

  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void starredChangeState() throws GitAPIException, IOException,
      OrmException {
    Result c1 = createChange();
    Result c2 = createChange();
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
    starChange(true, c1.getPatchSetId().getParentKey());
    starChange(true, c2.getPatchSetId().getParentKey());
    assertTrue(getChange(c1.getChangeId()).starred);
    assertTrue(getChange(c2.getChangeId()).starred);
    starChange(false, c1.getPatchSetId().getParentKey());
    starChange(false, c2.getPatchSetId().getParentKey());
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
  }

  private void starChange(boolean on, Change.Id id) throws IOException {
    String url = "/accounts/self/starred.changes/" + id.get();
    if (on) {
      RestResponse r = adminSession.put(url);
      assertEquals(204, r.getStatusCode());
    } else {
      RestResponse r = adminSession.delete(url);
      assertEquals(204, r.getStatusCode());
    }
  }

  private Result createChange() throws GitAPIException, IOException {
    PushOneCommit push = pushFactory.create(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }
}
