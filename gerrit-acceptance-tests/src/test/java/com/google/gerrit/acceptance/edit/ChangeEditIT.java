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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeEdits.Put;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.util.Providers;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

public class ChangeEditIT extends AbstractDaemonTest {

  private final static String FILE_NAME = "foo";
  private final static String FILE_NAME2 = "foo2";
  private final static byte[] CONTENT_OLD = "bar".getBytes(UTF_8);
  private final static byte[] CONTENT_NEW = "baz".getBytes(UTF_8);
  private final static byte[] CONTENT_NEW2 = "qux".getBytes(UTF_8);

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
  private FileContentUtil fileUtil;

  @Inject
  private AcceptanceTestRequestScope atrScope;

  private ReviewDb db;
  private Change change;
  private String changeId;
  private Change change2;
  private PatchSet ps;
  private PatchSet ps2;
  RestSession session;

  @Before
  public void setUp() throws Exception {
    db = reviewDbProvider.open();
    changeId = newChange(git, admin.getIdent());
    ps = getCurrentPatchSet(changeId);
    amendChange(git, admin.getIdent(), changeId);
    change = getChange(changeId);
    assertNotNull(ps);
    String changeId2 = newChange2(git, admin.getIdent());
    change2 = getChange(changeId2);
    assertNotNull(change2);
    ps2 = getCurrentPatchSet(changeId2);
    assertNotNull(ps2);
    session = new RestSession(server, admin);
    atrScope.set(atrScope.newContext(reviewDbProvider, sshSession,
        identifiedUserFactory.create(Providers.of(db), admin.getId())));
    final long clockStepMs = MILLISECONDS.convert(1, SECONDS);
    final AtomicLong clockMs = new AtomicLong(
        new DateTime(2009, 9, 30, 17, 0, 0).getMillis());
    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @After
  public void cleanup() {
    DateTimeUtils.setCurrentMillisSystem();
    db.close();
  }

  @Test
  public void deleteEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            editUtil.byChange(change).get(),
            FILE_NAME,
            CONTENT_NEW));
    editUtil.delete(editUtil.byChange(change).get());
    assertFalse(editUtil.byChange(change).isPresent());
  }

  @Test
  public void publishEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            getCurrentPatchSet(changeId)));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            editUtil.byChange(change).get(),
            FILE_NAME,
            CONTENT_NEW2));
    editUtil.publish(editUtil.byChange(change).get());
    assertFalse(editUtil.byChange(change).isPresent());
  }

  @Test
  public void rebaseEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            editUtil.byChange(change).get(),
            FILE_NAME,
            CONTENT_NEW));
    ChangeEdit edit = editUtil.byChange(change).get();
    PatchSet current = getCurrentPatchSet(changeId);
    assertEquals(current.getPatchSetId() - 1,
        edit.getBasePatchSet().getPatchSetId());
    Date beforeRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    modifier.rebaseEdit(edit, current);
    edit = editUtil.byChange(change).get();
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.getChange().getProject(),
            edit.getRevision().get(), FILE_NAME)));
    assertArrayEquals(CONTENT_NEW2,
        toBytes(fileUtil.getContent(edit.getChange().getProject(),
            edit.getRevision().get(), FILE_NAME2)));
    assertEquals(current.getPatchSetId(),
        edit.getBasePatchSet().getPatchSetId());
    Date afterRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    assertFalse(beforeRebase.equals(afterRebase));
  }

  @Test
  public void updateExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            CONTENT_NEW));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

  @Test
  public void retrieveEdit() throws Exception {
    RestResponse r = session.get(urlEdit());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            CONTENT_NEW));
    edit = editUtil.byChange(change);
    EditInfo info = toEditInfo();
    assertEquals(edit.get().getRevision().get(), info.commit.commit);
    assertEquals(1, info.commit.parents.size());

    edit = editUtil.byChange(change);
    editUtil.delete(edit.get());

    r = session.get(urlEdit());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
  }

  @Test
  public void deleteExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.deleteFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(edit.get().getChange().getProject(),
          edit.get().getRevision().get(), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  @Test
  public void restoreDeletedFileInEdit() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            CONTENT_NEW));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.deleteFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(edit.get().getChange().getProject(),
          edit.get().getRevision().get(), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
    assertEquals(RefUpdate.Result.FORCED,
        modifier.restoreFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_OLD,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  @Test
  public void restoreDeletedFileInPatchSet() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change2,
            ps2));
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.restoreFile(
            edit.get(),
            FILE_NAME));
    edit = editUtil.byChange(change2);
    assertArrayEquals(CONTENT_OLD,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  @Test
  public void amendExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            CONTENT_NEW));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            CONTENT_NEW2));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW2,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  @Test
  public void createAndChangeEditInOneRequestRest() throws Exception {
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertEquals(SC_NO_CONTENT, session.putRaw(urlEditFile(),
        in.content).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
    in.content = RestSession.newRawInput(CONTENT_NEW2);
    assertEquals(SC_NO_CONTENT, session.putRaw(urlEditFile(),
        in.content).getStatusCode());
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW2,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  @Test
  public void changeEditRest() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertEquals(SC_NO_CONTENT, session.putRaw(urlEditFile(),
        in.content).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  @Test
  public void emptyPutRequest() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    assertEquals(SC_NO_CONTENT, session.put(urlEditFile()).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertArrayEquals("".getBytes(),
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  @Test
  public void addNewFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME2,
            CONTENT_NEW));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME2)));
  }

  @Test
  public void addNewFileAndAmend() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME2,
            CONTENT_NEW));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME2)));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME2,
            CONTENT_NEW2));
    edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_NEW2,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME2)));
  }

  @Test
  public void writeNoChanges() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    try {
      modifier.modifyFile(
          editUtil.byChange(change).get(),
          FILE_NAME,
          CONTENT_OLD);
      fail();
    } catch (InvalidChangeOperationException e) {
      assertEquals("no changes were made", e.getMessage());
    }
  }

  private String newChange(Git git, PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_OLD));
    return push.to(git, "refs/for/master").getChangeId();
  }

  private String amendChange(Git git, PersonIdent ident, String changeId) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME2,
            new String(CONTENT_NEW2), changeId);
    return push.to(git, "refs/for/master").getChangeId();
  }

  private String newChange2(Git git, PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_OLD));
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

  private static byte[] toBytes(BinaryResult content) throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    content.writeTo(os);
    return os.toByteArray();
  }

  private String urlEdit() {
    return "/changes/"
        + change.getChangeId()
        + "/edit";
  }

  private String urlEditFile() {
    return urlEdit()
        + "/"
        + FILE_NAME;
  }

  private EditInfo toEditInfo() throws IOException {
    RestResponse r = session.get(urlEdit());
    assertEquals(SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(), EditInfo.class);
  }
}
