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

package com.google.gerrit.acceptance.git;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutContent;
import com.google.gerrit.server.change.RevisionEditCommands;
import com.google.gerrit.server.git.RevisionEdit;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class RevisionEditIT extends AbstractDaemonTest {

  private final static String FILE_NAME = "foo";
  private final static String FILE_NAME2 = "foo2";
  private final static String CONTENT_OLD = "bar";
  private final static String CONTENT_NEW = "baz";

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private RevisionEditCommands cmd;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  private ReviewDb db;
  private Change change;
  private PatchSet ps;
  RestSession session;

  @Before
  public void setUp() throws Exception {
    TestAccount admin = accounts.admin();
    initSsh(admin);
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    Git git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
    String changeId = newChange(git, admin.getIdent());
    change = getChange(changeId);
    assertNotNull(change);
    ps = getCurrentPatchSet(changeId);
    assertNotNull(ps);
    session = new RestSession(server, admin);
    atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
        identifiedUserFactory.create(Providers.of(db), admin.getId())));
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void updateExistingFile() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException {
    assertEquals(RefUpdate.Result.NEW,
        cmd.edit(
            change,
            ps,
            FILE_NAME,
            CONTENT_NEW));
    assertEquals(1, cmd.read(change).size());
    cmd.delete(change, ps);
    assertEquals(0, cmd.read(change).size());
  }

  @Test
  public void updateExistingFileRest() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException {
    PutContent.Input in = new PutContent.Input();
    in.content = CONTENT_NEW;
    assertEquals(204, session.put(urlFile(), in).getStatusCode());
    assertEquals(1, cmd.read(change).size());
    assertEquals(204, session.delete(urlRev()).getStatusCode());
    assertEquals(0, cmd.read(change).size());
  }

  @Test
  public void ammendExistingFile() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException, OrmException {
    assertEquals(RefUpdate.Result.NEW,
        cmd.edit(
            change,
            ps,
            FILE_NAME,
            CONTENT_NEW));
    assertEquals(1, cmd.read(change).size());
    assertEquals(RefUpdate.Result.FORCED,
        cmd.edit(
            change,
            ps,
            FILE_NAME,
            CONTENT_NEW + 42));
    cmd.publish(change, ps);
    assertEquals(0, cmd.read(change).size());
  }

  @Test
  public void ammendExistingFileRest() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException, OrmException {
    PutContent.Input in = new PutContent.Input();
    in.content = CONTENT_NEW;
    assertEquals(204, session.put(urlFile(), in).getStatusCode());
    Map<Id, RevisionEdit> m = cmd.read(change);
    assertEquals(1, m.size());
    Map.Entry<Id, RevisionEdit> e = Iterables.getOnlyElement(m.entrySet());
    assertEquals(true, e.getKey().isEdit());
    assertEquals("1+", e.getKey().getId());
    in.content = CONTENT_NEW + 42;
    assertEquals(204, session.put(urlFile(), in).getStatusCode());
    cmd.publish(change, ps);
    assertEquals(0, cmd.read(change).size());
  }

  @Test
  public void addNewFile() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException {
    assertEquals(RefUpdate.Result.NEW,
        cmd.edit(
            change,
            ps,
            FILE_NAME2,
            CONTENT_NEW));

    assertEquals(1, cmd.read(change).size());
    cmd.delete(change, ps);
    assertEquals(0, cmd.read(change).size());
  }

  @Test
  public void addNewFileAndAmmend() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException {
    assertEquals(RefUpdate.Result.NEW,
        cmd.edit(
            change,
            ps,
            FILE_NAME2,
            CONTENT_NEW));

    assertEquals(RefUpdate.Result.FORCED,
        cmd.edit(
            change,
            ps,
            FILE_NAME2,
            CONTENT_NEW + 42));

    assertEquals(1, cmd.read(change).size());
    cmd.delete(change, ps);
    assertEquals(0, cmd.read(change).size());
  }

  @Test(expected = InvalidChangeOperationException.class)
  public void writeNoChanges() throws IOException, AuthException,
      InvalidChangeOperationException, NoSuchChangeException {
    assertEquals(RefUpdate.Result.NEW,
        cmd.edit(
            change,
            ps,
            FILE_NAME,
            CONTENT_OLD));
  }

  private String newChange(Git git, PersonIdent ident) throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db,
        ident, PushOneCommit.SUBJECT,
        FILE_NAME, CONTENT_OLD);
    return push.to(git, "refs/for/master").getChangeId();
  }

  private Change getChange(String changeId) throws OrmException {
    return Iterables.getOnlyElement(db.changes()
        .byKey(new Change.Key(changeId)));
  }

  private PatchSet getCurrentPatchSet(String changeId) throws OrmException {
    return db.patchSets()
        .get(getChange(changeId).currentPatchSetId());
  }

  private String urlFile() {
    return urlRev()
        + "/files/"
        + FILE_NAME
        + "/content";
  }

  private String urlRev() {
    String url = "/changes/"
        + change.getChangeId()
        + "/revisions/"
        + ps.getPatchSetId()
        + ".edit";
    return url;
  }
}
