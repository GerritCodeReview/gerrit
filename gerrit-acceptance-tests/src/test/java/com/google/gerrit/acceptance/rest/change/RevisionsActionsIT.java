// Copyright (C) 2015 The Android Open Source Project
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
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class RevisionsActionsIT extends AbstractDaemonTest {
  @Test
  public void revisionActions() throws Exception {
    assertThat(getActions(createChange().getChangeId())).hasSize(1);
  }

  private Map<String, ActionInfo> getActions(String changeId)
      throws IOException {
    return newGson().fromJson(
        adminSession.get("/changes/"
            + changeId
            + "/revisions/1/actions").getReader(),
        new TypeToken<Map<String, ActionInfo>>() {}.getType());
  }
}
