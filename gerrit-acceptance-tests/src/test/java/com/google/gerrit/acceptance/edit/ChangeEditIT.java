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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.project.InvalidChangeOperationException;

import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Test;

import java.util.Date;

@NoHttpd
public class ChangeEditIT extends ChangeEditBase {

  @Test
  public void testAll() throws Exception {
    deleteEdit();
    reset();
    publishEdit();
    reset();
    rebaseEdit();
    reset();
    updateExistingFile();
    reset();
    deleteExistingFile();
    reset();
    restoreDeletedFileInEdit();
    reset();
    restoreDeletedFileInPatchSet();
    reset();
    amendExistingFile();
    reset();
    addNewFile();
    reset();
    addNewFileAndAmend();
    reset();
    writeNoChanges();
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
    editUtil.delete(editUtil.byChange(change).get());
    assertFalse(editUtil.byChange(change).isPresent());
  }

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
}
