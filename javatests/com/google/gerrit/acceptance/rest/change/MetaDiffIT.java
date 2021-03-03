// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.ChangeInfo;
import org.junit.Test;

public class MetaDiffIT extends AbstractDaemonTest {

  @Test
  public void metaDiffUnreachableNewSha1() throws Exception {
    PushOneCommit.Result ch1 = createChange();
    PushOneCommit.Result ch2 = createChange();

    ChangeInfo info2 = gApi.changes().id(ch2.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get(
            "/changes/" + ch1.getChangeId() + "/metadiff/?meta=" + info2.metaRevId);

    resp.assertStatus(412);
  }

  @Test
  public void metaDiffUnreachableOldSha1UsesDefault() throws Exception {
    PushOneCommit.Result ch1 = createChange();
    PushOneCommit.Result ch2 = createChange();

    ChangeInfo info2 = gApi.changes().id(ch2.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch1.getChangeId() + "/metadiff/?old=" + info2.metaRevId);

    resp.assertOK();
    // TODO(alexaspradlin): assert the output is expected
  }

  @Test
  public void metaDiffNoOldMetaGivenUsesLastPatchSet() throws Exception {
    PushOneCommit.Result ch = createChange();

    ChangeInfo info = gApi.changes().id(ch.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch.getChangeId() + "/metadiff/?meta=" + info.metaRevId);

    resp.assertOK();
    // TODO(alexaspradlin): assert the output is expected
  }

  @Test
  public void metaDiffNoNewMetaGivenUsesCurrentPatchSet() throws Exception {
    PushOneCommit.Result ch = createChange();

    ChangeInfo info = gApi.changes().id(ch.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch.getChangeId() + "/metadiff/?old=" + info.metaRevId);

    resp.assertOK();
    // TODO(alexaspradlin): assert the output is expected
  }
}
