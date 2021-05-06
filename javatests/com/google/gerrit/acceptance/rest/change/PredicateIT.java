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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractPredicateTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.entities.Change;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PredicateIT extends AbstractPredicateTest {

  @Test
  public void testLabelPredicate() throws Exception {
    try (AutoCloseable ignored = installPlugin(PLUGIN_NAME, PluginModule.class)) {
      Change.Id changeId = createChange().getChange().getId();
      approve(String.valueOf(changeId.get()));
      List<MyInfo> myInfos =
          pluginInfoFromSingletonList(
              adminRestSession.get("/changes/?--my-plugin--sample&q=change:" + changeId.get()));

      assertThat(myInfos).hasSize(1);
      assertThat(myInfos.get(0).name).isEqualTo(PLUGIN_NAME);
      assertThat(myInfos.get(0).message).isEqualTo("matched");
    }
  }

  public List<MyInfo> pluginInfoFromSingletonList(RestResponse res) throws Exception {
    res.assertOK();
    List<Map<String, Object>> changeInfos =
        GSON.fromJson(res.getReader(), new TypeToken<List<Map<String, Object>>>() {}.getType());

    assertThat(changeInfos).hasSize(1);
    return decodeRawPluginsList(changeInfos.get(0).get("plugins"));
  }
}
