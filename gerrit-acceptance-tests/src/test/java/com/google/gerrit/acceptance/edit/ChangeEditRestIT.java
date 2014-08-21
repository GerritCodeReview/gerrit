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

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.ChangeEdits.Post;
import com.google.gerrit.server.change.ChangeEdits.Put;
import com.google.gerrit.server.edit.ChangeEdit;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.List;

public class ChangeEditRestIT extends ChangeEditBase {

  private RestSession session;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    session = new RestSession(server, admin);
  }

  @Test
  public void testAll() throws Exception {
    publishEdit();
    reset();
    deleteEdit();
    reset();
    rebaseEdit();
    reset();
    retrieveEdit();
    reset();
    createEditByDeletingExistingFile();
    reset();
    deletingNonExistingEdit();
    reset();
    deleteExistingFile();
    reset();
    restoreDeletedFileInPatchSet();
    reset();
    createAndChangeEditInOneRequest();
    reset();
    retrieveFilesInEdit();
    reset();
    changeEdit();
    reset();
    emptyPutRequest();
    reset();
    createEmptyEdit();
    reset();
    getFileContent();
    reset();
    getFileNotFound();
  }

  public void publishEdit() throws Exception {
    PatchSet oldCurrentPatchSet = getCurrentPatchSet(changeId);
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            oldCurrentPatchSet));
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            editUtil.byChange(change).get(),
            FILE_NAME,
            CONTENT_NEW));
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    RestResponse r = session.post(urlPublish());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
    PatchSet newCurrentPatchSet = getCurrentPatchSet(changeId);
    assertFalse(oldCurrentPatchSet.getId().equals(newCurrentPatchSet.getId()));
  }

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
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    RestResponse r = session.delete(urlEdit());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
    edit = editUtil.byChange(change);
    assertFalse(edit.isPresent());
  }

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
    RestResponse r = session.post(urlRebase());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
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
    EditInfo info = toEditInfo(false);
    assertEquals(edit.get().getRevision().get(), info.commit.commit);
    assertEquals(1, info.commit.parents.size());

    edit = editUtil.byChange(change);
    editUtil.delete(edit.get());

    r = session.get(urlEdit());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
  }

  public void createEditByDeletingExistingFile() throws Exception {
    RestResponse r = session.delete(urlEditFile());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(edit.get().getChange().getProject(),
          edit.get().getRevision().get(), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  public void deletingNonExistingEdit() throws Exception {
    RestResponse r = session.delete(urlEdit());
    assertEquals(SC_BAD_REQUEST, r.getStatusCode());
  }

  public void deleteExistingFile() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    assertEquals(SC_NO_CONTENT, session.delete(urlEditFile()).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(edit.get().getChange().getProject(),
          edit.get().getRevision().get(), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  public void restoreDeletedFileInPatchSet() throws Exception {
    Post.Input in = new Post.Input();
    in.restorePath = FILE_NAME;
    assertEquals(SC_NO_CONTENT, session.post(urlEdit2(),
        in).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertArrayEquals(CONTENT_OLD,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  public void createAndChangeEditInOneRequest() throws Exception {
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

  public void retrieveFilesInEdit() throws Exception {
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

    EditInfo info = toEditInfo(true);
    assertEquals(2, info.files.size());
    List<String> l = Lists.newArrayList(info.files.keySet());
    assertEquals("/COMMIT_MSG", l.get(0));
    assertEquals("foo", l.get(1));
  }

  public void changeEdit() throws Exception {
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

  public void createEmptyEdit() throws Exception {
    assertEquals(SC_NO_CONTENT, session.post(urlEdit()).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertArrayEquals(CONTENT_OLD,
        toBytes(fileUtil.getContent(edit.get().getChange().getProject(),
            edit.get().getRevision().get(), FILE_NAME)));
  }

  public void getFileContent() throws Exception {
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertEquals(SC_NO_CONTENT, session.putRaw(urlEditFile(),
        in.content).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertEquals(RefUpdate.Result.FORCED,
        modifier.modifyFile(
            edit.get(),
            FILE_NAME,
            CONTENT_NEW2));
    edit = editUtil.byChange(change);
    RestResponse r = session.get(urlEditFile());
    assertEquals(SC_OK, r.getStatusCode());
    String content = r.getEntityContent();
    assertEquals(StringUtils.newStringUtf8(CONTENT_NEW2),
        StringUtils.newStringUtf8(Base64.decodeBase64(content)));
  }

  public void getFileNotFound() throws Exception {
    assertEquals(RefUpdate.Result.NEW,
        modifier.createEdit(
            change,
            ps));
    assertEquals(SC_NO_CONTENT, session.delete(urlEditFile()).getStatusCode());
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(edit.get().getChange().getProject(),
          edit.get().getRevision().get(), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
    RestResponse r = session.get(urlEditFile());
    assertEquals(SC_NO_CONTENT, r.getStatusCode());
  }

  private String urlEdit() {
    return "/changes/"
        + change.getChangeId()
        + "/edit";
  }

  private String urlEdit2() {
    return "/changes/"
        + change2.getChangeId()
        + "/edit/";
  }

  private String urlEditFile() {
    return urlEdit()
        + "/"
        + FILE_NAME;
  }

  private String urlGetFiles() {
    return urlEdit()
        + "?list";
  }

  private String urlPublish() {
    return "/changes/"
        + change.getChangeId()
        + "/publish_edit";
  }

  private String urlRebase() {
    return "/changes/"
        + change.getChangeId()
        + "/rebase_edit";
  }

  private EditInfo toEditInfo(boolean files) throws IOException {
    RestResponse r = session.get(files ? urlGetFiles() : urlEdit());
    assertEquals(SC_OK, r.getStatusCode());
    return newGson().fromJson(r.getReader(), EditInfo.class);
  }
}
