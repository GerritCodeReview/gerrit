// Copyright (C) 2019 The Android Open Source Project
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
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.extensions.restapi.testing.BinaryResultSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.FileUploadInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class FileUploadIT extends AbstractDaemonTest {

  private static final String FILE_NAME = "foo";
  private static final String FILE_NAME_NEW = "hellow_world.txt";
  private static final byte[] CONTENT_OLD = "Hello".getBytes(UTF_8);
  private static final String CONTENT_NEW_ENCODED = "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==";
  private static final byte[] CONTENT_NEW_DECODED = "Hello, World!".getBytes(UTF_8);
  private static final String CONTENT_NEW_2_ENCODED =
      "data:text/plain;base64,VXBsb2FkaW5nIHRvIGFuIGVkaXQgd29ya2VkIQ==";
  private static final byte[] CONTENT_NEW_2_DECODED =
      "Uploading to an edit worked!".getBytes(UTF_8);
  private static final String PLAIN_TEXT = "test";

  private String changeId;
  private PatchSet ps;

  @Before
  public void setUp() throws Exception {
    changeId = newChange(admin.newIdent());
    ps = getCurrentPatchSet(changeId);
    assertThat(ps).isNotNull();
  }

  @Test
  public void createAndUploadFileInChangeEditInOneRequestRest() throws Exception {
    FileUploadInput in = new FileUploadInput();
    in.content = CONTENT_NEW_ENCODED;
    in.path = FILE_NAME_NEW;
    gApi.changes().id(changeId).edit().modifyFile(in);
    ensureSameBytes(getFileContentOfEdit(changeId, FILE_NAME_NEW), CONTENT_NEW_DECODED);
  }

  @Test
  public void uploadFileToExistingChangeEditInOneRequestRest() throws Exception {
    FileUploadInput in = new FileUploadInput();
    in.content = CONTENT_NEW_ENCODED;
    in.path = FILE_NAME;
    gApi.changes().id(changeId).edit().modifyFile(in);
    ensureSameBytes(getFileContentOfEdit(changeId, FILE_NAME), CONTENT_NEW_DECODED);
    in.content = CONTENT_NEW_2_ENCODED;
    in.path = FILE_NAME;
    gApi.changes().id(changeId).edit().modifyFile(in);
    ensureSameBytes(getFileContentOfEdit(changeId, FILE_NAME), CONTENT_NEW_2_DECODED);
  }

  @Test
  public void createAndUploadFileWithoutPath() throws Exception {
    FileUploadInput in = new FileUploadInput();
    in.content = CONTENT_NEW_ENCODED;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).edit().modifyFile(in));
    assertThat(thrown).hasMessageThat().contains("Path is required");
  }

  @Test
  public void createAndUploadFileWithoutContent() throws Exception {
    FileUploadInput in = new FileUploadInput();
    in.path = FILE_NAME;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).edit().modifyFile(in));
    assertThat(thrown).hasMessageThat().contains("Invalid content");
  }

  @Test
  public void createAndUploadFileNonBase64() throws Exception {
    FileUploadInput in = new FileUploadInput();
    in.content = PLAIN_TEXT;
    in.path = FILE_NAME;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.changes().id(changeId).edit().modifyFile(in));
    assertThat(thrown).hasMessageThat().contains("File must be encoded with base64 data uri");
  }

  private Optional<BinaryResult> getFileContentOfEdit(String changeId, String filePath)
      throws Exception {
    return gApi.changes().id(changeId).edit().getFile(filePath);
  }

  private String newChange(PersonIdent ident) throws Exception {
    PushOneCommit push =
        pushFactory.create(
            ident, testRepo, PushOneCommit.SUBJECT, FILE_NAME, new String(CONTENT_OLD, UTF_8));
    return push.to("refs/for/master").getChangeId();
  }

  private PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).currentPatchSet();
  }

  private void ensureSameBytes(Optional<BinaryResult> fileContent, byte[] expectedFileBytes)
      throws IOException {
    assertThat(fileContent).value().bytes().isEqualTo(expectedFileBytes);
  }
}
