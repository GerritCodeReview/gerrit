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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
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
import com.google.gerrit.server.project.Util;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gson.stream.JsonReader;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.apache.commons.codec.binary.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class ChangeEditIT extends AbstractDaemonTest {

  private static final String FILE_NAME = "foo";
  private static final String FILE_NAME2 = "foo2";
  private static final String FILE_NAME3 = "foo3";
  private static final byte[] CONTENT_OLD = "bar".getBytes(UTF_8);
  private static final byte[] CONTENT_NEW = "baz".getBytes(UTF_8);
  private static final String CONTENT_NEW2_STR = "quxÄÜÖßµ";
  private static final byte[] CONTENT_NEW2 = CONTENT_NEW2_STR.getBytes(UTF_8);

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
  private String changeId2;
  private PatchSet ps;
  private PatchSet ps2;

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.setClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Before
  public void setUp() throws Exception {
    db = reviewDbProvider.open();
    changeId = newChange(admin.getIdent());
    ps = getCurrentPatchSet(changeId);
    amendChange(admin.getIdent(), changeId);
    change = getChange(changeId);
    assertThat(ps).isNotNull();
    changeId2 = newChange2(admin.getIdent());
    change2 = getChange(changeId2);
    assertThat(change2).isNotNull();
    ps2 = getCurrentPatchSet(changeId2);
    assertThat(ps2).isNotNull();
  }

  @After
  public void cleanup() {
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
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(edit.isPresent()).isFalse();
    assertChangeMessages(change,
        ImmutableList.of("Uploaded patch set 1.",
            "Uploaded patch set 2.",
            "Patch set 3: Published edit on patch set 2."));
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
    adminSession.post(urlPublish()).assertNoContent();
    edit = editUtil.byChange(change);
    assertThat(edit.isPresent()).isFalse();
    PatchSet newCurrentPatchSet = getCurrentPatchSet(changeId);
    assertThat(newCurrentPatchSet.getId()).isNotEqualTo(oldCurrentPatchSet.getId());
    assertChangeMessages(change,
        ImmutableList.of("Uploaded patch set 1.",
            "Uploaded patch set 2.",
            "Patch set 3: Published edit on patch set 2."));
  }

  @Test
  public void deleteEditRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    adminSession.delete(urlEdit()).assertNoContent();
    edit = editUtil.byChange(change);
    assertThat(edit.isPresent()).isFalse();
  }

  @Test
  public void publishEditRestWithoutCLA() throws Exception {
    setUseContributorAgreements(InheritableBoolean.TRUE);
    PatchSet oldCurrentPatchSet = getCurrentPatchSet(changeId);
    assertThat(modifier.createEdit(change, oldCurrentPatchSet)).isEqualTo(
        RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    adminSession.post(urlPublish()).assertForbidden();
    setUseContributorAgreements(InheritableBoolean.FALSE);
    adminSession.post(urlPublish()).assertNoContent();
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
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME2), CONTENT_NEW2);
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
    adminSession.post(urlRebase()).assertNoContent();
    edit = editUtil.byChange(change).get();
    assertByteArray(fileUtil.getContent(projectCache.get(edit.getChange().getProject()),
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME), CONTENT_NEW);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.getChange().getProject()),
        ObjectId.fromString(edit.getRevision().get()), FILE_NAME2), CONTENT_NEW2);
    assertThat(edit.getBasePatchSet().getPatchSetId()).isEqualTo(
        current.getPatchSetId());
    Date afterRebase = edit.getEditCommit().getCommitterIdent().getWhen();
    assertThat(afterRebase).isNotEqualTo(beforeRebase);
  }

  @Test
  public void rebaseEditWithConflictsRest_Conflict() throws Exception {
    PatchSet current = getCurrentPatchSet(changeId2);
    assertThat(modifier.createEdit(change2, current)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change2).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    ChangeEdit edit = editUtil.byChange(change2).get();
    assertThat(edit.getBasePatchSet().getPatchSetId()).isEqualTo(
        current.getPatchSetId());
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_NEW2), changeId2);
    push.to("refs/for/master").assertOkStatus();
    adminSession.post(urlRebase()).assertConflict();
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
  @TestProjectInput(createEmptyCommit = false)
  public void updateRootCommitMessage() throws Exception {
    // Re-clone empty repo; TestRepository doesn't let us reset to unborn head.
    testRepo = cloneProject(project);
    changeId = newChange(admin.getIdent());
    change = getChange(changeId);

    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getParentCount()).isEqualTo(0);

    String msg = String.format("New commit message\n\nChange-Id: %s\n",
        change.getKey());
    assertThat(modifier.modifyMessage(edit.get(), msg))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage()).isEqualTo(msg);
  }

  @Test
  public void updateMessageNoChange() throws Exception {
    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);

    exception.expect(UnchangedCommitMessageException.class);
    exception.expectMessage(
        "New commit message cannot be same as existing commit message");
    modifier.modifyMessage(
        edit.get(),
        edit.get().getEditCommit().getFullMessage());
  }

  @Test
  public void updateMessage() throws Exception {
    assertThat(modifier.createEdit(change, getCurrentPatchSet(changeId)))
        .isEqualTo(RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertUnchangedMessage(edit, edit.get().getEditCommit().getFullMessage());
    assertUnchangedMessage(edit, edit.get().getEditCommit().getFullMessage() + "\n\n");
    String msg = String.format("New commit message\n\nChange-Id: %s\n",
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

    assertChangeMessages(change,
        ImmutableList.of("Uploaded patch set 1.",
            "Uploaded patch set 2.",
            "Patch set 3: Commit message was updated."));
  }

  @Test
  public void updateMessageRest() throws Exception {
    adminSession.get(urlEditMessage()).assertNotFound();
    EditMessage.Input in = new EditMessage.Input();
    in.message = String.format("New commit message\n\n" +
        CONTENT_NEW2_STR + "\n\nChange-Id: %s\n",
        change.getKey());
    adminSession.put(urlEditMessage(), in).assertNoContent();
    RestResponse r = adminSession.getJsonAccept(urlEditMessage());
    r.assertOK();
    assertThat(readContentFromJson(r)).isEqualTo(in.message);
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage())
        .isEqualTo(in.message);
    in.message = String.format("New commit message2\n\nChange-Id: %s\n",
        change.getKey());
    adminSession.put(urlEditMessage(), in).assertNoContent();
    edit = editUtil.byChange(change);
    assertThat(edit.get().getEditCommit().getFullMessage())
        .isEqualTo(in.message);
    editUtil.publish(edit.get());
    assertChangeMessages(change,
        ImmutableList.of("Uploaded patch set 1.",
            "Uploaded patch set 2.",
            "Patch set 3: Commit message was updated."));
  }

  @Test
  public void retrieveEdit() throws Exception {
    adminSession.get(urlEdit()).assertNoContent();
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

    adminSession.get(urlEdit()).assertNoContent();
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
    exception.expect(ResourceNotFoundException.class);
    fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
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
    exception.expect(ResourceNotFoundException.class);
    fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
  }

  @Test
  public void createEditByDeletingExistingFileRest() throws Exception {
    adminSession.delete(urlEditFile()).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    exception.expect(ResourceNotFoundException.class);
    fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
  }

  @Test
  public void deletingNonExistingEditRest() throws Exception {
    adminSession.delete(urlEdit()).assertNotFound();
  }

  @Test
  public void deleteExistingFileRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    adminSession.delete(urlEditFile()).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    exception.expect(ResourceNotFoundException.class);
    fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
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
  public void revertChanges() throws Exception {
    assertThat(modifier.createEdit(change2, ps2)).isEqualTo(
        RefUpdate.Result.NEW);
    Optional<ChangeEdit> edit = editUtil.byChange(change2);
    assertThat(modifier.restoreFile(edit.get(), FILE_NAME)).isEqualTo(
        RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change2);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_OLD);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change2).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change2);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
    assertThat(modifier.restoreFile(edit.get(), FILE_NAME)).isEqualTo(
        RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change2);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_OLD);
    editUtil.delete(edit.get());
  }

  @Test
  public void renameFileRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Post.Input in = new Post.Input();
    in.oldPath = FILE_NAME;
    in.newPath = FILE_NAME3;
    adminSession.post(urlEdit(), in).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME3), CONTENT_OLD);
    exception.expect(ResourceNotFoundException.class);
    fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
  }

  @Test
  public void restoreDeletedFileInPatchSetRest() throws Exception {
    Post.Input in = new Post.Input();
    in.restorePath = FILE_NAME;
    adminSession.post(urlEdit2(), in).assertNoContent();
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
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW2);
  }

  @Test
  public void createAndChangeEditInOneRequestRest() throws Exception {
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    adminSession.putRaw(urlEditFile(), in.content).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
    in.content = RestSession.newRawInput(CONTENT_NEW2);
    adminSession.putRaw(urlEditFile(), in.content).assertNoContent();
    edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW2);
  }

  @Test
  public void changeEditRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    adminSession.putRaw(urlEditFile(), in.content).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_NEW);
  }

  @Test
  public void emptyPutRequest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    adminSession.put(urlEditFile()).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), "".getBytes());
  }

  @Test
  public void createEmptyEditRest() throws Exception {
    adminSession.post(urlEdit()).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertByteArray(fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME), CONTENT_OLD);
  }

  @Test
  public void getFileContentRest() throws Exception {
    Put.Input in = new Put.Input();
    in.content = RestSession.newRawInput(CONTENT_NEW);
    adminSession.putRaw(urlEditFile(), in.content).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    assertThat(modifier.modifyFile(edit.get(), FILE_NAME, RestSession.newRawInput(CONTENT_NEW2)))
        .isEqualTo(RefUpdate.Result.FORCED);
    edit = editUtil.byChange(change);
    RestResponse r = adminSession.getJsonAccept(urlEditFile());
    r.assertOK();
    assertThat(readContentFromJson(r)).isEqualTo(
        StringUtils.newStringUtf8(CONTENT_NEW2));
  }

  @Test
  public void getFileNotFoundRest() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    adminSession.delete(urlEditFile()).assertNoContent();
    Optional<ChangeEdit> edit = editUtil.byChange(change);
    adminSession.get(urlEditFile()).assertNoContent();
    exception.expect(ResourceNotFoundException.class);
    fileUtil.getContent(projectCache.get(edit.get().getChange().getProject()),
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME);
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
        ObjectId.fromString(edit.get().getRevision().get()), FILE_NAME2), CONTENT_NEW2);
  }

  @Test
  public void writeNoChanges() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    exception.expect(InvalidChangeOperationException.class);
    exception.expectMessage("no changes were made");
    modifier.modifyFile(
        editUtil.byChange(change).get(),
        FILE_NAME,
        RestSession.newRawInput(CONTENT_OLD));
  }

  @Test
  public void editCommitMessageCopiesLabelScores() throws Exception {
    String cr = "Code-Review";
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType codeReview = Util.codeReview();
    codeReview.setCopyAllScoresIfNoCodeChange(true);
    cfg.getLabelSections().put(cr, codeReview);
    saveProjectConfig(project, cfg);

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

  private void assertUnchangedMessage(Optional<ChangeEdit> edit, String message)
      throws Exception {
    exception.expect(UnchangedCommitMessageException.class);
    exception.expectMessage(
        "New commit message cannot be same as existing commit message");
    modifier.modifyMessage(edit.get(), message);
  }

  @Test
  public void testHasEditPredicate() throws Exception {
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(queryEdits()).hasSize(1);

    PatchSet current = getCurrentPatchSet(changeId2);
    assertThat(modifier.createEdit(change2, current)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(
        modifier.modifyFile(editUtil.byChange(change2).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    assertThat(queryEdits()).hasSize(2);

    assertThat(
        modifier.modifyFile(editUtil.byChange(change).get(), FILE_NAME,
            RestSession.newRawInput(CONTENT_NEW))).isEqualTo(RefUpdate.Result.FORCED);
    editUtil.delete(editUtil.byChange(change).get());
    assertThat(queryEdits()).hasSize(1);

    editUtil.publish(editUtil.byChange(change2).get());
    assertThat(queryEdits()).hasSize(0);

    setApiUser(user);
    assertThat(modifier.createEdit(change, ps)).isEqualTo(RefUpdate.Result.NEW);
    assertThat(queryEdits()).hasSize(1);

    setApiUser(admin);
    assertThat(queryEdits()).hasSize(0);
  }

  private List<ChangeInfo> queryEdits() throws Exception {
    return query("project:{" + project.get() + "} has:edit");
  }

  private String newChange(PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, testRepo, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_OLD, UTF_8));
    return push.to("refs/for/master").getChangeId();
  }

  private String amendChange(PersonIdent ident, String changeId) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, testRepo, PushOneCommit.SUBJECT, FILE_NAME2,
            new String(CONTENT_NEW2, UTF_8), changeId);
    return push.to("refs/for/master").getChangeId();
  }

  private String newChange2(PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(db, ident, testRepo, PushOneCommit.SUBJECT, FILE_NAME,
            new String(CONTENT_OLD, UTF_8));
    return push.rm("refs/for/master").getChangeId();
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

  private EditInfo toEditInfo(boolean files) throws Exception {
    RestResponse r = adminSession.get(files ? urlGetFiles() : urlEdit());
    r.assertOK();
    return newGson().fromJson(r.getReader(), EditInfo.class);
  }

  private String readContentFromJson(RestResponse r) throws IOException {
    JsonReader jsonReader = new JsonReader(r.getReader());
    jsonReader.setLenient(true);
    return newGson().fromJson(jsonReader, String.class);
  }

  private void assertChangeMessages(Change c, List<String> expectedMessages)
      throws Exception {
    ChangeInfo ci = get(c.getId().toString());
    assertThat(ci.messages).isNotNull();
    assertThat(ci.messages).hasSize(expectedMessages.size());
    List<String> actualMessages = new ArrayList<>();
    Iterator<ChangeMessageInfo> it = ci.messages.iterator();
    while (it.hasNext()) {
      actualMessages.add(it.next().message);
    }
    assertThat(actualMessages)
      .containsExactlyElementsIn(expectedMessages)
      .inOrder();
  }
}
