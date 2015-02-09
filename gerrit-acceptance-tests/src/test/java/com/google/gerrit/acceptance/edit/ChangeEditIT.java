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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeEdits.EditMessage;
import com.google.gerrit.server.change.ChangeEdits.Post;
import com.google.gerrit.server.change.ChangeEdits.Put;
import com.google.gerrit.server.change.FileContentUtil;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.edit.UnchangedCommitMessageException;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gson.stream.JsonReader;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.commons.codec.binary.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class ChangeEditIT extends AbstractDaemonTest {

  private final static String FILE_NAME = "foo";
  private final static String FILE_NAME2 = "foo2";
  private final static String FILE_NAME3 = "foo3";
  private final static byte[] CONTENT_OLD = "bar".getBytes(UTF_8);
  private final static byte[] CONTENT_NEW = "baz".getBytes(UTF_8);
  private final static String CONTENT_NEW2 = "quxÄÜÖßµ";

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  ChangeEditUtil editUtil;

  @Inject
  private ChangeEditModifier modifier;

  @Inject
  private FileContentUtil fileUtil;

  private Change change;
  private String changeId;
  private Change change2;
  private PatchSet ps;
  private PatchSet ps2;

  @Before
  public void setUp() throws Exception {
    db = reviewDbProvider.open();
    changeId = newChange(git, admin.getIdent());
    ps = getCurrentPatchSet(changeId);
    amendChange(git, admin.getIdent(), changeId);
    change = getChange(changeId);
    assertThat(ps).isNotNull();
    String changeId2 = newChange2(git, admin.getIdent());
    change2 = getChange(changeId2);
    assertThat(change2).isNotNull();
    ps2 = getCurrentPatchSet(changeId2);
    assertThat(ps2).isNotNull();
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
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    editUtil.delete(editUtil.byChange(change).get());
    assertThat(editUtil.byChange(change).isPresent()).isFalse();
  }

  @Test
  public void publishEdit() throws Exception {
    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW2))).isEqualTo(RefUpdate.Result.FORCED);
    editUtil.publish(editUtil.byChange(change).get());
    assertThat(editUtil.byChange(change).isPresent()).isFalse();
  }

  @Test
  public void publishEditRest() throws Exception {
    PatchSet oldCurrentPatchSet = getCurrentPatchSet(changeId);
    assertThat(modifier.createEdit(change, oldCurrentPatchSet)).isEqualTo(
        RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    RestResponse r = adminSession.post(urlPublish());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
    edit = editUtil.byChange(change);
    assertThat(edit.isPresent()).isFalse();
    PatchSet newCurrentPatchSet = getCurrentPatchSet(changeId);
    assertThat(newCurrentPatchSet.getId()).isNotEqualTo(oldCurrentPatchSet.getId());
  }

  @Test
  public void deleteEditRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    RestResponse r = adminSession.delete(urlEdit());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
    edit = editUtil.byChange(change);
    assertThat(edit.isPresent()).isFalse();
  }

  @Test
  public void rebaseEdit() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    ChangeEdit edit = editUtil.byChange(change).get();
    PatchSet current = getCurrentPatchSet(changeId);
    assertThat(edit.getBasePatchSet().getPatchSetId()).isEqualTo(
        current.getPatchSetId() - 1);
    Date beforeRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    modifier.rebaseEdit(edit, current);
    edit = editUtil.byChange(change).get();
    assertByteArray(fileUtil.getContent(projectCache.get(edit.getChange().getProject()),
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME), CONTENT_NEW);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.getChange().getProject()),
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME2), CONTENT_NEW2.getBytes(UTF_8));
    assertThat(edit.getBasePatchSet().getPatchSetId()).isEqualTo(
        current.getPatchSetId());
    Date afterRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    assertThat(beforeRebase.equals(afterRebase)).isFalse();
  }

  @Test
  public void rebaseEditRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    ChangeEdit edit = editUtil.byChange(change).get();
    PatchSet current = getCurrentPatchSet(changeId);
    assertThat(edit.getBasePatchSet().getPatchSetId()).isEqualTo(
        current.getPatchSetId() - 1);
    Date beforeRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    RestResponse r = adminSession.post(urlRebase());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
    edit = editUtil.byChange(change).get();
    assertByteArray(fileUtil.getContent(projectCache.get(edit.getChange().getProject()),
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME), CONTENT_NEW);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.getChange().getProject()),
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME2), CONTENT_NEW2.getBytes(UTF_8));
    assertThat(edit.getBasePatchSet().getPatchSetId()).isEqualTo(
        current.getPatchSetId());
    Date afterRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    assertThat(afterRebase).isNotEqualTo(beforeRebase);
  }

  @Test
  public void updateExistingFile() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
    editUtil.delete(edit.get());
    edit = editUtil.byChange(change);
    assertThat(edit.isPresent()).isFalse();
  }

  @Test
  public void updateRootCommitMessage() throws Exception {
    createProject(sshSession, "root-msg-test", null, false);
    git = cloneProject(sshSession.getUrl() + "/root-msg-test");
    changeId = newChange(git, admin.getIdent());
    change = getChange(changeId);

    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getParentCount()).isEqualTo(0);

    String msg = String.format("New commit message\n\nChange-Id: %s",
        change.getKey());
    assertThat(modifier.modifyMessage(edit.get(), msg))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage()).isEqualTo(msg);
  }

  @Test
  public void updateMessage() throws Exception {
    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);

    try {
      modifier.modifyMessage(
          edit.get(),
          edit.get().getEditCommit().getFullMessage());
      fail("UnchangedCommitMessageException expected");
    } catch (UnchangedCommitMessageException ex) {
      assertThat(ex.getMessage()).isEqualTo(
          "New commit message cannot be same as existing commit message");
    }

    String msg = String.format("New commit message\n\nChange-Id: %s",
        change.getKey());
    assertThat(modifier.modifyMessage(edit.get(), msg)).isEqualTo(
        RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage()).isEqualTo(msg);

    editUtil.publish(edit.get());
    assertThat(editUtil.byChange(change).isPresent()).isFalse();

    ChangeInfo info = get(changeId, ListChangesOption.CURRENT_COMMIT,
        ListChangesOption.CURRENT_REVISION);
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .isEqualTo(msg);
  }

  @Test
  public void updateMessageRest() throws Exception {
    assertThat(adminSession.get(urlEditMessage()).getStatusCode())
        .isEqualTo(SC_NOT_FOUND);
    EditMessage.Input in = new EditMessage.Input();
    in.message = String.format("New commit message\n\n" +
        CONTENT_NEW2 + "\n\nChange-Id: %s",
        change.getKey());
    assertThat(adminSession.put(urlEditMessage(), in).getStatusCode())
        .isEqualTo(SC_NO_CONTENT);
    RestResponse r = adminSession.getJsonAccept(urlEditMessage());
    assertThat(r.getStatusCode()).isEqualTo(SC_OK);
    assertThat(readContentFromJson(r)).isEqualTo(in.message);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage())
        .isEqualTo(in.message);
    in.message = String.format("New commit message2\n\nChange-Id: %s",
        change.getKey());
    assertThat(adminSession.put(urlEditMessage(), in).getStatusCode())
        .isEqualTo(SC_NO_CONTENT);
    edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage())
        .isEqualTo(in.message);
  }

  @Test
  public void retrieveEdit() throws Exception {
    RestResponse r = adminSession.get(urlEdit());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    EditInfo info = toEditInfo(false);
    assertThat(info.commit.commit).isEqualTo(edit.get().getRevision().get());
    assertThat(info.commit.parents).hasSize(1);

    edit = editUtil.byChange(change);
    editUtil.delete(edit.get());

    r = adminSession.get(urlEdit());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  public void retrieveFilesInEdit() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW)))
        .isEqualTo(RefUpdate.Result.FORCED);

    EditInfo info = toEditInfo(true);
    assertThat(info.files).hasSize(2);
    List<String> l = Lists.newArrayList(info.files.keySet());
    assertThat(l.get(0)).isEqualTo("/COMMIT_MSG");
    assertThat(l.get(1)).isEqualTo("foo");
  }

  @Test
  public void deleteExistingFile() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.deleteFile(edit.get(), FILE_NAME)).isEqualTo(
        RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
          ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  @Test
  public void renameExistingFile() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.renameFile(edit.get(), FILE_NAME, FILE_NAME3))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME3), CONTENT_OLD);
    try {
      fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
          ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  @Test
  public void createEditByDeletingExistingFileRest() throws Exception {
    RestResponse r = adminSession.delete(urlEditFile());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
          ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  @Test
  public void deletingNonExistingEditRest() throws Exception {
    RestResponse r = adminSession.delete(urlEdit());
    assertThat(r.getStatusCode()).isEqualTo(SC_NOT_FOUND);
  }

  @Test
  public void deleteExistingFileRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(adminSession.delete(urlEditFile()).getStatusCode()).isEqualTo(
        SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
          ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  @Test
  public void restoreDeletedFileInPatchSet() throws Exception {
    assertThat(modifier.createEdit(change2, ps2)).isEqualTo(
        RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertThat(modifier.restoreFile(edit.get(), FILE_NAME)).isEqualTo(
        RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change2);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_OLD);
  }

  @Test
  public void renameFileRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Post.Input in = new Post.Input();
    in.oldPath = FILE_NAME;
    in.newPath = FILE_NAME3;
    assertThat(adminSession.post(urlEdit(), in).getStatusCode()).isEqualTo(
        SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME3), CONTENT_OLD);
    try {
      fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
          ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
  }

  @Test
  public void restoreDeletedFileInPatchSetRest() throws Exception {
    Post.Input in = new Post.Input();
    in.restorePath = FILE_NAME;
    assertThat(adminSession.post(urlEdit2(), in).getStatusCode()).isEqualTo(
        SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_OLD);
  }

  @Test
  public void amendExistingFile() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW2)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW2.getBytes(UTF_8));
  }

  @Test
  public void createAndChangeEditInOneRequestRest() throws Exception {
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertThat(adminSession.putRaw(urlEditFile(), in.content).getStatusCode())
        .isEqualTo(SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
    in.content = RestSession.newRawInput(CONTENT_NEW2);
    assertThat(adminSession.putRaw(urlEditFile(), in.content).getStatusCode())
        .isEqualTo(SC_NO_CONTENT);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW2.getBytes(UTF_8));
  }

  @Test
  public void changeEditRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertThat(adminSession.putRaw(urlEditFile(), in.content).getStatusCode())
        .isEqualTo(SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
  }

  @Test
  public void emptyPutRequest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(adminSession.put(urlEditFile()).getStatusCode()).isEqualTo(
        SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), "".getBytes());
  }

  @Test
  public void createEmptyEditRest() throws Exception {
    assertThat(adminSession.post(urlEdit()).getStatusCode()).isEqualTo(
        SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_OLD);
  }

  @Test
  public void getFileContentRest() throws Exception {
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    assertThat(adminSession.putRaw(urlEditFile(), in.content).getStatusCode())
        .isEqualTo(SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW2)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    RestResponse r = adminSession.getJsonAccept(urlEditFile());
    assertThat(r.getStatusCode()).isEqualTo(SC_OK);
    assertThat(readContentFromJson(r)).isEqualTo(
        StringUtils.newStringUtf8(CONTENT_NEW2.getBytes(UTF_8)));
  }

  @Test
  public void getFileNotFoundRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(adminSession.delete(urlEditFile()).getStatusCode()).isEqualTo(
        SC_NO_CONTENT);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    try {
      fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
          ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
      fail("ResourceNotFoundException expected");
    } catch (ResourceNotFoundException rnfe) {
    }
    RestResponse r = adminSession.get(urlEditFile());
    assertThat(r.getStatusCode()).isEqualTo(SC_NO_CONTENT);
  }

  @Test
  public void addNewFile() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME2, RestSession.newRawInput(CONTENT_NEW)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME2), CONTENT_NEW);
  }

  @Test
  public void addNewFileAndAmend() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME2, RestSession.newRawInput(CONTENT_NEW)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME2), CONTENT_NEW);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME2, RestSession.newRawInput(CONTENT_NEW2)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME2), CONTENT_NEW2.getBytes(UTF_8));
  }

  @Test
  public void writeNoChanges() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    try {
      modifier.modifyFile(
          editUtil.byChange(change).get(),
          FILE_NAME,
          RestSession.newRawInput(CONTENT_OLD));
      fail();
    } catch (InvalidChangeOperationException e) {
      assertThat(e.getMessage()).isEqualTo("no changes were made");
    }
  }

  @Test
  public void editCommitMessageCopiesLabelScores() throws Exception {
    String cr = "Code-Review";
    ProjectConfig cfg = projectCache.checkedGet(allProjects).getConfig();
    cfg.getLabelSections().get(cr)
        .setCopyAllScoresIfNoCodeChange(true);
    saveProjectConfig(allProjects, cfg);

    String changeId = change.getKey().get();
    ReviewInput r = new ReviewInput();
    r.labels = ImmutableMap.<String, Short> of(cr, (short) 1);
    gApi.changes()
        .id(changeId)
        .revision(change.currentPatchSetId().get())
        .review(r);

    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    String newSubj = "New commit message";
    String newMsg = newSubj + "\n\nChange-Id: " + changeId + "\n";
    assertThat(modifier.modifyMessage(edit.get(), newMsg))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    editUtil.publish(edit.get());

    ChangeInfo info = get(changeId);
    assertThat(info.subject).isEqualTo(newSubj);
    List<ApprovalInfo> approvals = info.labels.get(cr).all;
    assertThat(approvals).hasSize(1);
    assertThat(approvals.get(0).value).isEqualTo(1);
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
    return getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
  }

  private PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return db.patchSets()
        .get(getChange(changeId).currentPatchSetId());
  }

  private static void assertByteArray(BinaryResult result, byte[] expected)
        throws Exception {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    result.writeTo(os);
    assertThat(os.toByteArray()).isEqualTo(expected);
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

  private String urlEditMessage() {
    return "/changes/"
        + change.getChangeId()
        + "/edit:message";
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
        + "/edit:publish";
  }

  private String urlRebase() {
    return "/changes/"
        + change.getChangeId()
        + "/edit:rebase";
  }

  private EditInfo toEditInfo(boolean files) throws IOException {
    RestResponse r = adminSession.get(files ? urlGetFiles() : urlEdit());
    assertThat(r.getStatusCode()).isEqualTo(SC_OK);
    return newGson().fromJson(r.getReader(), EditInfo.class);
  }

  private String readContentFromJson(RestResponse r) throws IOException {
    JsonReader jsonReader = new JsonReader(r.getReader());
    jsonReader.setLenient(true);
    return newGson().fromJson(jsonReader, String.class);
  }
}
