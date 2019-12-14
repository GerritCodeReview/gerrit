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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.FileUploadInput;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

@UseClockStep
public class FilwUploadIT extends AbstractDaemonTest {

  private static final String FILE_NAME = "foo";
  private static final String CONTENT_NEW_ENCODED = "data:text/plain;base64,SGVsbG93LCBXb3JsZCE=";
  private static final String CONTENT_NEW_DECODED = "Hellow, World!".getBytes(UTF_8);

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private String changeId;
  private PatchSet ps;

  @Before
  public void setUp() throws Exception {
    changeId = newChange(admin.newIdent());
    ps = getCurrentPatchSet(changeId);
    assertThat(ps).isNotNull();
    amendChange(admin.newIdent(), changeId);
  }

  @Test
  public void createAndUploadFileInChangeEditInOneRequestRest() throws Exception {
    FileUploadInput in = new FileUploadInput();
    in.content = CONTENT_NEW_ENCODED;
    in.path = FILE_NAME;
    adminRestSession.put(urlEditUpload(changeId), in).assertNoContent();
    ensureSameBytes(getFileContentOfEdit(changeId, FILE_NAME), CONTENT_NEW_DECODED);
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

  private String amendChange(PersonIdent ident, String changeId) throws Exception {
    PushOneCommit push =
        pushFactory.create(
            ident,
            testRepo,
            PushOneCommit.SUBJECT,
            FILE_NAME2,
            new String(CONTENT_NEW2, UTF_8),
            changeId);
    return push.to("refs/for/master").getChangeId();
  }

  private PatchSet getCurrentPatchSet(String changeId) throws Exception {
    return getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).currentPatchSet();
  }

  private void ensureSameBytes(Optional<BinaryResult> fileContent, byte[] expectedFileBytes)
      throws IOException {
    assertThat(fileContent).value().bytes().isEqualTo(expectedFileBytes);
  }

  private String urlEditUpload(String changeId) {
    return "/changes/" + changeId + "/edit:upload";
  }
}
