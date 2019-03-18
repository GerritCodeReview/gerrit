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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PluginFieldsRestIT extends AbstractPluginFieldsTest {
  private static final Gson GSON = OutputFormat.JSON.newGson();

  @Test
  public void queryChangeRestWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void getChangeRestWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeUrl(id))));
  }

  @Test
  public void getChangeDetailRestWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeDetailUrl(id))));
  }

  @Test
  public void queryChangeRestWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void getChangeRestWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeUrl(id))));
  }

  @Test
  public void getChangeDetailRestWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeDetailUrl(id))));
  }

  @Test
  public void queryChangeRestWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id))),
        (id, opts) ->
            pluginInfoFromSingletonListRest(adminRestSession.get(changeQueryUrl(id, opts))));
  }

  @Test
  public void getChangeRestWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeUrl(id))),
        (id, opts) -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeUrl(id, opts))));
  }

  @Test
  public void getChangeDetailRestWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromChangeInfoRest(adminRestSession.get(changeDetailUrl(id))),
        (id, opts) ->
            pluginInfoFromChangeInfoRest(adminRestSession.get(changeDetailUrl(id, opts))));
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
  private static List<MyInfo> pluginInfoFromSingletonListRest(RestResponse res) throws Exception {
    res.assertOK();

    // Don't deserialize to ChangeInfo directly, since that would treat the plugins field as
    // List<PluginDefinedInfo> and ignore the unknown keys found in MyInfo.
    List<Map<String, Object>> changeInfos =
        GSON.fromJson(res.getReader(), new TypeToken<List<Map<String, Object>>>() {}.getType());
    assertThat(changeInfos).hasSize(1);
    return myInfo(changeInfos.get(0));
  }

  @Nullable
  private List<MyInfo> pluginInfoFromChangeInfoRest(RestResponse res) throws Exception {
    res.assertOK();

    // Don't deserialize to ChangeInfo directly, since that would treat the plugins field as
    // List<PluginDefinedInfo> and ignore the unknown keys found in MyInfo.
    return myInfo(
        GSON.fromJson(res.getReader(), new TypeToken<Map<String, Object>>() {}.getType()));
  }

  private static List<MyInfo> myInfo(Map<String, Object> changeInfo) {
    Object plugins = changeInfo.get("plugins");
    if (plugins == null) {
      return null;
    }
    return GSON.fromJson(GSON.toJson(plugins), new TypeToken<List<MyInfo>>() {}.getType());
  }
}
