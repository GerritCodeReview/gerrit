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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.git.PushOneCommit;
import com.google.gerrit.acceptance.git.PushOneCommit.Result;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class StarredChangeIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;

  private RestSession session;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    session = new RestSession(server, admin);
    initSsh(admin);
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
    starChange(true, c1.getPatchSetId().getParentKey().toString(),
        c2.getPatchSetId().getParentKey().toString());
    assertTrue(getChange(c1.getChangeId()).starred);
    assertTrue(getChange(c2.getChangeId()).starred);
    starChange(false, c1.getPatchSetId().getParentKey().toString());
    starChange(false, c2.getPatchSetId().getParentKey().toString());
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
  }

  @Test
  public void starredChangeStateDifferent() throws GitAPIException,
      IOException, OrmException {
    Result c1 = createChange();
    Result c2 = createChange();
    assertNull(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
    StarredChangeInput in = input(
        c1.getPatchSetId().getParentKey().toString(),
        c2.getPatchSetId().getParentKey().toString());
    starChange(in);
    assertTrue(getChange(c1.getChangeId()).starred);
    assertNull(getChange(c2.getChangeId()).starred);
    in = input(
        c2.getPatchSetId().getParentKey().toString(),
        c1.getPatchSetId().getParentKey().toString());
    starChange(in);
    assertNull(getChange(c1.getChangeId()).starred);
    assertTrue(getChange(c2.getChangeId()).starred);
  }

  @Test
  public void starredChangeOnOff() throws GitAPIException, IOException,
      OrmException {
    Result c1 = createChange();
    assertNull(getChange(c1.getChangeId()).starred);
    StarredChangeInput in = input(
        c1.getPatchSetId().getParentKey().toString(),
        c1.getPatchSetId().getParentKey().toString());
    starChange(in);
    assertNull(getChange(c1.getChangeId()).starred);
  }

  private ChangeInfo getChange(String changeId) throws IOException {
    RestResponse r = session.get("/changes/?q=" + changeId);
    List<ChangeInfo> c = (new Gson()).fromJson(r.getReader(),
        new TypeToken<List<ChangeInfo>>() {}.getType());
    return c.get(0);
  }

  private void starChange(boolean on, String... id) throws IOException {
    StarredChangeInput starredChangeInfo = input(on, id);
    starChange(starredChangeInfo);
  }

  private void starChange(StarredChangeInput starredChangeInfo)
      throws IOException {
    RestResponse r = session.post("/accounts/self/starred.changes",
        starredChangeInfo);
    assertEquals(204, r.getStatusCode());
  }

  private StarredChangeInput input(boolean on, String... id) {
    StarredChangeInput starredChangeInfo = new StarredChangeInput();
    if (on) {
      starredChangeInfo.on = id;
    } else {
      starredChangeInfo.off = id;
    }
    return starredChangeInfo;
  }

  private StarredChangeInput input(String on, String off) {
    StarredChangeInput starredChangeInfo = new StarredChangeInput();
    starredChangeInfo.on = new String[] {on};
    starredChangeInfo.off = new String[] {off};
    return starredChangeInfo;
  }

  private Result createChange() throws GitAPIException, IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, "refs/for/master");
  }

  static class StarredChangeInput {
    String[] on;
    String[] off;
  }
}
