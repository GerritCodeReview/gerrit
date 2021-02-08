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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.RefNames.changeMetaRef;
import static com.google.gerrit.extensions.client.ListChangesOption.META_REF;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gson.stream.JsonReader;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class ChangeMetaIT extends AbstractDaemonTest {
  @Test
  public void ChangeInfo_metaSha1() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    ChangeInfo before = gApi.changes().id(changeId).get(META_REF);
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(before.metaRef)
          .isEqualTo(
              repo.exactRef(changeMetaRef(Change.id(before._number))).getObjectId().getName());
    }
  }

  @Test
  public void ChangeInfo_metaSha1_parameter() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).setMessage("before\n\n" + "Change-Id: " + result.getChangeId());
    ChangeInfo before = gApi.changes().id(changeId).get(META_REF);
    gApi.changes().id(changeId).setMessage("after\n\n" + "Change-Id: " + result.getChangeId());
    ChangeInfo after = gApi.changes().id(changeId).get(META_REF);
    assertThat(after.metaRef).isNotEqualTo(before.metaRef);

    RestResponse resp =
        adminRestSession.get("/changes/" + changeId + "/?meta=" + before.metaRef + "&O=1000000");
    resp.assertOK();

    ChangeInfo got;
    try (JsonReader jsonReader = new JsonReader(resp.getReader())) {
      jsonReader.setLenient(true);
      got = newGson().fromJson(jsonReader, ChangeInfo.class);
    }
    assertThat(got.subject).isEqualTo(before.subject);
  }

  @Test
  public void metaUnreachableSha1() throws Exception {
    PushOneCommit.Result ch1 = createChange();
    PushOneCommit.Result ch2 = createChange();

    ChangeInfo info2 = gApi.changes().id(ch2.getChangeId()).get(META_REF);

    RestResponse resp =
        adminRestSession.get("/changes/" + ch1.getChangeId() + "/?meta=" + info2.metaRef);

    resp.assertStatus(412);
  }
}
