// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import org.junit.Test;

public class RepositoryFieldCompatibilityIT extends AbstractDaemonTest {
  @Test
  public void outbound() throws Exception {
    String changeId = createChange().getChangeId();

    RestResponse r = adminRestSession.get("/changes/" + changeId + "/detail");
    r.assertOK();

    JsonObject json = newGson().fromJson(r.getReader(), JsonElement.class).getAsJsonObject();
    assertThat(json.has("project")).isTrue();
    assertThat(json.get("project").getAsString()).isEqualTo(project.get());
    assertThat(json.has("repository")).isTrue();
    assertThat(json.get("repository").getAsString()).isEqualTo(project.get());
  }

  @Test
  public void inbound() throws Exception {
    Map<String, String> changeInput =
        ImmutableMap.of("repository", project.get(), "subject", "Test", "branch", "master");

    RestResponse r = adminRestSession.post("/changes/", changeInput);
    r.assertCreated();

    JsonObject json = newGson().fromJson(r.getReader(), JsonElement.class).getAsJsonObject();
    assertThat(json.has("project")).isTrue();
    assertThat(json.get("project").getAsString()).isEqualTo(project.get());
    assertThat(json.has("repository")).isTrue();
    assertThat(json.get("repository").getAsString()).isEqualTo(project.get());
  }
}
