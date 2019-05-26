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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.io.CharStreams;
import com.google.gerrit.acceptance.AbstractPluginFieldsTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.query.change.OutputStreamQuery;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Test;

@UseSsh
public class PluginChangeFieldsIT extends AbstractPluginFieldsTest {
  // No tests for getting a single change over SSH, since the only API is the query API.

  private static final Gson GSON = OutputStreamQuery.GSON;

  @Test
  public void queryChangeWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromSingletonList(adminSshSession.exec(changeQueryCmd(id))));
  }

  @Test
  public void queryChangeWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonList(adminSshSession.exec(changeQueryCmd(id))));
  }

  @Test
  public void queryChangeWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromSingletonList(adminSshSession.exec(changeQueryCmd(id))),
        (id, opts) -> pluginInfoFromSingletonList(adminSshSession.exec(changeQueryCmd(id, opts))));
  }

  private String changeQueryCmd(Change.Id id) {
    return changeQueryCmd(id, ImmutableListMultimap.of());
  }

  private String changeQueryCmd(Change.Id id, ImmutableListMultimap<String, String> pluginOptions) {
    return "gerrit query --format json "
        + pluginOptions.entries().stream()
            .flatMap(e -> Stream.of("--" + e.getKey(), e.getValue()))
            .collect(joining(" "))
        + " "
        + id;
  }

  @Nullable
  private static List<MyInfo> pluginInfoFromSingletonList(String sshOutput) throws Exception {
    List<Map<String, Object>> changeAttrs = new ArrayList<>();
    for (String line : CharStreams.readLines(new StringReader(sshOutput))) {
      Map<String, Object> changeAttr =
          GSON.fromJson(line, new TypeToken<Map<String, Object>>() {}.getType());
      if (!"stats".equals(changeAttr.get("type"))) {
        changeAttrs.add(changeAttr);
      }
    }

    assertThat(changeAttrs).hasSize(1);
    return decodeRawPluginsList(GSON, changeAttrs.get(0).get("plugins"));
  }
}
