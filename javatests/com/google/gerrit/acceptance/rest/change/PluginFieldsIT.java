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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.acceptance.AbstractPluginFieldsTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PluginFieldsIT extends AbstractPluginFieldsTest {
  private static final Gson GSON = OutputFormat.JSON.newGson();

  @Test
  public void queryChangeWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromSingletonList(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void getChangeWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(id -> pluginInfoFromChangeInfo(adminRestSession.get(changeUrl(id))));
  }

  @Test
  public void getChangeDetailWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromChangeInfo(adminRestSession.get(changeDetailUrl(id))));
  }

  @Test
  public void queryChangeWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonList(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void getChangeWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromChangeInfo(adminRestSession.get(changeUrl(id))));
  }

  @Test
  public void getChangeDetailWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromChangeInfo(adminRestSession.get(changeDetailUrl(id))));
  }

  @Test
  public void queryChangeWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromSingletonList(adminRestSession.get(changeQueryUrl(id))),
        (id, opts) -> pluginInfoFromSingletonList(adminRestSession.get(changeQueryUrl(id, opts))));
  }

  @Test
  public void getChangeWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromChangeInfo(adminRestSession.get(changeUrl(id))),
        (id, opts) -> pluginInfoFromChangeInfo(adminRestSession.get(changeUrl(id, opts))));
  }

  @Test
  public void getChangeDetailWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromChangeInfo(adminRestSession.get(changeDetailUrl(id))),
        (id, opts) -> pluginInfoFromChangeInfo(adminRestSession.get(changeDetailUrl(id, opts))));
  }

  private String changeQueryUrl(Change.Id id) {
    return changeQueryUrl(id, ImmutableListMultimap.of());
  }

  private String changeQueryUrl(Change.Id id, ImmutableListMultimap<String, String> opts) {
    String url = "/changes/?q=" + id;
    String queryString = buildQueryString(opts);
    if (!queryString.isEmpty()) {
      url += "&" + queryString;
    }
    return url;
  }

  private String changeUrl(Change.Id id) {
    return changeUrl(id, ImmutableListMultimap.of());
  }

  private String changeUrl(Change.Id id, ImmutableListMultimap<String, String> pluginOptions) {
    return changeUrl(id, "", pluginOptions);
  }

  private String changeDetailUrl(Change.Id id) {
    return changeDetailUrl(id, ImmutableListMultimap.of());
  }

  private String changeDetailUrl(
      Change.Id id, ImmutableListMultimap<String, String> pluginOptions) {
    return changeUrl(id, "/detail", pluginOptions);
  }

  private String changeUrl(
      Change.Id id, String suffix, ImmutableListMultimap<String, String> pluginOptions) {
    String url = "/changes/" + project + "~" + id + suffix;
    String queryString = buildQueryString(pluginOptions);
    if (!queryString.isEmpty()) {
      url += "?" + queryString;
    }
    return url;
  }

  private static String buildQueryString(ImmutableListMultimap<String, String> opts) {
    return Joiner.on('&').withKeyValueSeparator('=').join(opts.entries());
  }

  @Nullable
  private static List<MyInfo> pluginInfoFromSingletonList(RestResponse res) throws Exception {
    res.assertOK();
    List<Map<String, Object>> changeInfos =
        GSON.fromJson(res.getReader(), new TypeToken<List<Map<String, Object>>>() {}.getType());
    assertThat(changeInfos).hasSize(1);
    return decodeRawPluginsList(GSON, changeInfos.get(0).get("plugins"));
  }

  @Nullable
  private List<MyInfo> pluginInfoFromChangeInfo(RestResponse res) throws Exception {
    res.assertOK();
    Map<String, Object> changeInfo =
        GSON.fromJson(res.getReader(), new TypeToken<Map<String, Object>>() {}.getType());
    return decodeRawPluginsList(GSON, changeInfo.get("plugins"));
  }
}
