// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.revision;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_CONTENT;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import org.eclipse.jgit.util.Base64;
import org.junit.Test;

public class RevisionIT extends AbstractDaemonTest {
  @Test
  public void contentOfParent() throws Exception {
    String parentContent = "parent content";
    PushOneCommit.Result parent = createChange("Parent change", FILE_NAME, parentContent);
    parent.assertOkStatus();

    gApi.changes().id(parent.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(parent.getChangeId()).current().submit();

    PushOneCommit.Result child = createChange("Child change", FILE_NAME, FILE_CONTENT);
    child.assertOkStatus();
    assertContent(child, FILE_NAME, FILE_CONTENT);

    RestResponse response =
        adminRestSession.get(
            "/changes/"
                + child.getChangeId()
                + "/revisions/current/files/"
                + FILE_NAME
                + "/content?parent=1");
    response.assertOK();
    assertThat(new String(Base64.decode(response.getEntityContent()), UTF_8))
        .isEqualTo(parentContent);
  }

  @Test
  public void contentOfInvalidParent() throws Exception {
    String parentContent = "parent content";
    PushOneCommit.Result parent = createChange("Parent change", FILE_NAME, parentContent);
    parent.assertOkStatus();

    gApi.changes().id(parent.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(parent.getChangeId()).current().submit();

    PushOneCommit.Result child = createChange("Child change", FILE_NAME, FILE_CONTENT);
    child.assertOkStatus();
    assertContent(child, FILE_NAME, FILE_CONTENT);

    RestResponse response =
        adminRestSession.get(
            "/changes/"
                + child.getChangeId()
                + "/revisions/current/files/"
                + FILE_NAME
                + "/content?parent=10");
    response.assertBadRequest();
    assertThat(response.getEntityContent()).isEqualTo("invalid parent");
  }
}
