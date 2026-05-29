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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.CharStreams;
import com.google.gerrit.acceptance.AbstractPredicateTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gson.reflect.TypeToken;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@NoHttpd
@UseSsh
public class PredicateIT extends AbstractPredicateTest {

  @Test
  public void testLabelPredicate() throws Exception {
    try (AutoCloseable ignored = installPlugin(PLUGIN_NAME, PluginModule.class)) {
      Change.Id changeId = createChange().getChange().getId();
      approve(String.valueOf(changeId.get()));
      String sshOutput =
          adminSshSession.exec(
              "gerrit query --format json --my-plugin--sample change:" + changeId.get());
      adminSshSession.assertSuccess();
      List<PluginDefinedInfo> myInfos = pluginInfoFromSingletonList(sshOutput);

      assertThat(myInfos).hasSize(1);
      assertThat(myInfos.get(0).name).isEqualTo(PLUGIN_NAME);
      assertThat(myInfos.get(0).message).isEqualTo("matched");
    }
  }

  private static List<PluginDefinedInfo> pluginInfoFromSingletonList(String sshOutput)
      throws Exception {
    List<Map<String, Object>> changeAttrs = new ArrayList<>();
    for (String line : CharStreams.readLines(new StringReader(sshOutput))) {
      Map<String, Object> changeAttr =
          GSON.fromJson(line, new TypeToken<Map<String, Object>>() {}.getType());
      if (!"stats".equals(changeAttr.get("type"))) {
        changeAttrs.add(changeAttr);
      }
    }

    assertThat(changeAttrs).hasSize(1);
    return decodeRawPluginsList(changeAttrs.get(0).get("plugins"));
  }
}
