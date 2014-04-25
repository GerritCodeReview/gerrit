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

package com.google.gerrit.acceptance.edit;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.edit.RevisionEditDeleter;
import com.google.gerrit.server.edit.RevisionEditModifier;
import com.google.gerrit.server.edit.RevisionEditPublisher;
import com.google.gerrit.server.edit.RevisionEditReader;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RevisionEditIT extends AbstractDaemonTest {

  private final static String FILE_NAME = "foo";
  private final static String FILE_NAME2 = "foo2";
  private final static String CONTENT_OLD = "bar";
  private final static String CONTENT_NEW = "baz";

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private PushOneCommit.Factory pushFactory;

  @Inject
  private RevisionEditModifier modifier;

  @Inject
  private RevisionEditDeleter deleter;

  @Inject
  private RevisionEditPublisher publisher;

  @Inject
  private RevisionEditReader reader;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  private ReviewDb db;
  private Change change;
  private Change change2;
  private PatchSet ps;
  private PatchSet ps2;
  RestSession session;

  @Before
  public void setUp() throws Exception {
    Project.NameKey project = new Project.NameKey("p");
    SshSession sshSession = new SshSession(server, admin);
    createProject(sshSession, project.get());
    Git git = cloneProject(sshSession.getUrl() + "/" + project.get());
    sshSession.close();
    db = reviewDbProvider.open();
    String changeId = newChange(git, admin.getIdent());
    change = getChange(changeId);
    ps = getCurrentPatchSet(changeId);
    assertNotNull(ps);
    changeId = newChange2(git, admin.getIdent());
    change2 = getChange(changeId);
    assertNotNull(change2);
    ps2 = getCurrentPatchSet(changeId);
    assertNotNull(ps2);
    session = new RestSession(server, admin);
    atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
        identifiedUserFactory.create(Providers.of(db), admin.getId())));
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void updateExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.editContent(
            change,
            ps,
            FILE_NAME,
            Constants.encode(CONTENT_NEW)));
    assertNotNull(reader.read(change));
    deleter.delete(change, ps);
    assertNull(reader.read(change));
  }

  @Test
  public void deleteExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.deleteContent(
            change,
            ps,
            FILE_NAME));
    assertNotNull(reader.read(change));
    deleter.delete(change, ps);
    assertNull(reader.read(change));
  }

  @Test
  public void restoreDeletedFileInEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.deleteContent(
            change,
            ps,
            FILE_NAME));
    assertNotNull(reader.read(change));
    modifier.restoreContent(change, ps, FILE_NAME);
    assertNotNull(reader.read(change));
    publisher.publish(change, ps);
    assertNull(reader.read(change));
  }

  @Test
  public void restoreDeletedFileInPatchSet() throws Exception {
    modifier.restoreContent(change2, ps2, FILE_NAME);
    assertNotNull(reader.read(change2));
    publisher.publish(change2, ps2);
    assertNull(reader.read(change2));
  }

  @Test
  public void ammendExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.editContent(
            change,
            ps,
            FILE_NAME,
            Constants.encode(CONTENT_NEW)));
    assertNotNull(reader.read(change));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.editContent(
            change,
            ps,
            FILE_NAME,
            Constants.encode(CONTENT_NEW + 42)));
    publisher.publish(change, ps);
    assertNull(reader.read(change));
  }

  @Test
  public void addNewFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.editContent(
            change,
            ps,
            FILE_NAME2,
            Constants.encode(CONTENT_NEW)));

    assertNotNull(reader.read(change));
    deleter.delete(change, ps);
    assertNull(reader.read(change));
  }

  @Test
  public void addNewFileAndAmmend() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.editContent(
            change,
            ps,
            FILE_NAME2,
            Constants.encode(CONTENT_NEW)));

    assertEquals(RefUpdate.Result.FORCED,
        modifier.editContent(
            change,
            ps,
            FILE_NAME2,
            Constants.encode(CONTENT_NEW + 42)));

    assertNotNull(reader.read(change));
    deleter.delete(change, ps);
    assertNull(reader.read(change));
  }

  @Test(expected = InvalidChangeOperationException.class)
  public void writeNoChanges() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.editContent(
            change,
            ps,
            FILE_NAME,
            Constants.encode(CONTENT_OLD)));
  }

  private String newChange(Git git, PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME,
            CONTENT_OLD);
    return push.to(git, "refs/for/master").getChangeId();
  }

  private String newChange2(Git git, PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME,
            CONTENT_OLD);
    return push.rm(git, "refs/for/master").getChangeId();
  }

  private Change getChange(String changeId) throws Exception {
    return Iterables.getOnlyElement(db.changes()
        .byKey(new Change.Key(changeId)));
  }

  private PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return db.patchSets()
        .get(getChange(changeId).currentPatchSetId());
  }
}
