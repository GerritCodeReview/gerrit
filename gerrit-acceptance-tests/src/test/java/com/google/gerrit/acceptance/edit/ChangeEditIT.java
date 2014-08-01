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

package com.google.gerrit.acceptance.edit;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PutContent;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gson.reflect.TypeToken;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ChangeEditIT extends AbstractDaemonTest {

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
  ChangeEditUtil editUtil;

  @Inject
  private ChangeEditModifier modifier;

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
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            Constants.encode(CONTENT_NEW)));
    edit = editUtil.byChange(change);
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void retrieveEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            Constants.encode(CONTENT_NEW)));

    RestResponse r = session.get(urlGet());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, EditInfo> result = toEditInfoMap(r);
    assertEquals(1, result.size());
    EditInfo info = Iterables.getOnlyElement(result.values());
    assertEquals(0, info._number);

    edit = editUtil.byChange(change);
    editUtil.delete(edit.get());

    r = session.get(urlGet());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(0, toEditInfoMap(r).size());
  }

  @Test
  public void retrieveFilesInEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            Constants.encode(CONTENT_NEW)));

    RestResponse r = session.get(urlGetFiles());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    Map<String, FileInfo> result = toFileInfoMap(r);
    assertEquals(2, result.size());
    List<String> l = Lists.newArrayList(result.keySet());
    assertEquals("/COMMIT_MSG", l.get(0));
    assertEquals("foo", l.get(1));
    edit = editUtil.byChange(change);
    editUtil.delete(edit.get());

    r = session.get(urlGet());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertEquals(0, toEditInfoMap(r).size());
  }

  @Test
  public void deleteExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.deleteFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void deleteExistingFileRest() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(204, session.delete(urlDelete(change)).getStatusCode());
    edit = editUtil.byChange(change);
    editUtil.publish(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void restoreDeletedFileInEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.deleteFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.restoreFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void restoreDeletedFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change2,
            ps2));
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.restoreFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change2);
    assertTrue(edit.isPresent());
    editUtil.publish(edit.get());
    edit = editUtil.byChange(change2);
    assertFalse(edit.isPresent());
  }

  @Test
  public void restoreDeletedFileRest() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change2,
            ps2));
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertTrue(edit.isPresent());
    PutContent.Input in = new PutContent.Input();
    in.restore = true;
    assertEquals(204, session.put(urlPut(change2), in).getStatusCode());
    edit = editUtil.byChange(change2);
    assertTrue(edit.isPresent());
    editUtil.publish(edit.get());
    edit = editUtil.byChange(change2);
    assertFalse(edit.isPresent());
  }

  @Test
  public void updateExistingFileRest() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    PutContent.Input in = new PutContent.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertEquals(204, session.putRaw(urlPut(change),
        in.content).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    editUtil.publish(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void ammendExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            Constants.encode(CONTENT_NEW)));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            Constants.encode(CONTENT_NEW + 42)));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    editUtil.publish(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void ammendExistingFileRest() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    PutContent.Input in = new PutContent.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertEquals(204, session.putRaw(urlPut(change),
        in.content).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    in.content = RestSession.newRawInput(CONTENT_NEW + 42);
    assertEquals(204, session.putRaw(urlPut(change),
        in.content).getStatusCode());
    edit = editUtil.byChange(change);
    editUtil.publish(edit.get());
    edit = editUtil.byChange(change2);
    assertFalse(edit.isPresent());
  }

  @Test
  public void addNewFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME2,
            Constants.encode(CONTENT_NEW)));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void addNewFileAndAmmend() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME2,
            Constants.encode(CONTENT_NEW)));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME2,
            Constants.encode(CONTENT_NEW + 42)));
    edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test(expected = InvalidChangeOperationException.class)
  public void writeNoChanges() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertTrue(edit.isPresent());
    assertEquals(RefUpdate.Result.NEW,
        modifier.modifyFile(
            edit.get(),
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

  private String urlGet() {
    return "/changes/"
        + change.getChangeId()
        + "/edits";
  }

  private String urlGetFiles() {
    return "/changes/"
        + change.getChangeId()
        + "/edits/"
        + "0"
        + "/files/";
  }

  private String urlDelete(Change c) {
    return "/changes/"
            + c.getChangeId()
            + "/edits/"
            + 0
            + "/files/"
            + FILE_NAME;
  }

  private String urlPut(Change c) {
    return urlDelete(c) + "/content";
  }

private static Map<String, EditInfo> toEditInfoMap(RestResponse r)
      throws IOException {
    Map<String, EditInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, EditInfo>>() {}.getType());
    return result;
  }

  private static Map<String, FileInfo> toFileInfoMap(RestResponse r)
      throws IOException {
    Map<String, FileInfo> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<Map<String, FileInfo>>() {}.getType());
    return result;
  }
}
