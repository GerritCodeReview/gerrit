// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Change;
import com.google.inject.Inject;
import org.junit.Test;

public class ChangeEditIT extends AbstractDaemonTest {
  private static final String FILE_NAME = "foo";
  private static final String FILE_NAME2 = "foo2";

  private static final String FILE_CONTENT = "content";
  private static final String FILE_CONTENT2 = "content2";

  @Inject private ChangeOperations changeOperations;

  @Test
  public void modifyMultipleFilesInOneChangeEdit() throws Exception {
    Change.Id changeId = changeOperations.newChange().create();
    RestResponse response =
        adminRestSession.putRaw(
            String.format("/changes/%s/edit/%s", changeId, FILE_NAME),
            RawInputUtil.create(FILE_CONTENT));
    assertThat(response.getStatusCode()).isEqualTo(204);
    RestResponse response2 =
        adminRestSession.putRaw(
            String.format("/changes/%s/edit/%s", changeId, FILE_NAME2),
            RawInputUtil.create(FILE_CONTENT2));
    assertThat(response2.getStatusCode()).isEqualTo(204);
    RestResponse publishResponse =
        adminRestSession.post(String.format("/changes/%s/edit:publish", changeId));
    assertThat(publishResponse.getStatusCode()).isEqualTo(204);
    assertThat(gApi.changes().id(changeId.get()).current().files().keySet())
        .containsExactly("/COMMIT_MSG", FILE_NAME, FILE_NAME2);
    // Created an initial change, then applied a single edit with two files resulting in one more
    // patchset.
    assertThat(gApi.changes().id(changeId.get()).get().currentRevisionNumber).isEqualTo(2);
  }
}
