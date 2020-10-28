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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.acceptance.AbstractPluginFieldsTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import com.google.gerrit.json.OutputFormat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PluginFieldsIT extends AbstractPluginFieldsTest {
  private static final Gson GSON = OutputFormat.JSON.newGson();

  @Test
  public void querySingleChangeWithBulkAttribute() throws Exception {
    getSingleChangeWithPluginDefinedBulkAttribute(
        id -> pluginInfosFromChangeInfos(adminRestSession.get(changeQueryUrl(id))));
  }

  @Test
  public void pluginDefinedGetChangeWithSimpleAttribute() throws Exception {
    getSingleChangeWithPluginDefinedBulkAttribute(
        id -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeUrl(id))));
  }

  @Test
  public void pluginDefinedGetChangeDetailWithSimpleAttribute() throws Exception {
    getSingleChangeWithPluginDefinedBulkAttribute(
        id -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeDetailUrl(id))));
  }

  @Test
  public void pluginDefinedQueryChangeWithOption() throws Exception {
    getChangeWithPluginDefinedBulkAttributeOption(
        id -> pluginInfosFromChangeInfos(adminRestSession.get(changeQueryUrl(id))),
        (id, opts) -> pluginInfosFromChangeInfos(adminRestSession.get(changeQueryUrl(id, opts))));
  }

  @Test
  public void pluginDefinedGetChangeWithOption() throws Exception {
    getChangeWithPluginDefinedBulkAttributeOption(
        id -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeUrl(id))),
        (id, opts) -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeUrl(id, opts))));
  }

  @Test
  public void pluginDefinedGetChangeDetailWithOption() throws Exception {
    getChangeWithPluginDefinedBulkAttributeOption(
        id -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeDetailUrl(id))),
        (id, opts) -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeDetailUrl(id, opts))));
  }

  @Test
  public void queryMultipleChangesWithPluginDefinedAttribute() throws Exception {
    getMultipleChangesWithPluginDefinedBulkAttribute(
        () -> pluginInfosFromChangeInfos(adminRestSession.get("/changes/?q=status:open")));
  }

  @Test
  public void queryChangesByCommitMessageWithPluginDefinedBulkAttribute() throws Exception {
    getChangesByCommitMessageWithPluginDefinedBulkAttribute(
        () -> pluginInfosFromChangeInfos(adminRestSession.get("/changes/?q=status:open")));
  }

  @Test
  public void getMultipleChangesWithPluginDefinedAttributeInSingleCall() throws Exception {
    getMultipleChangesWithPluginDefinedBulkAttributeInSingleCall(
        () -> pluginInfosFromChangeInfos(adminRestSession.get("/changes/?q=status:open")));
  }

  @Test
  public void getChangeWithPluginDefinedException() throws Exception {
    getChangeWithPluginDefinedBulkAttributeWithException(
        id -> pluginInfoMapFromChangeInfo(adminRestSession.get(changeUrl(id))));
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
  private Map<Change.Id, List<PluginDefinedInfo>> pluginInfoMapFromChangeInfo(RestResponse res)
      throws Exception {
    res.assertOK();
    Map<String, Object> changeInfo =
        GSON.fromJson(res.getReader(), new TypeToken<Map<String, Object>>() {}.getType());
    return getPluginInfosFromChangeInfos(GSON, Arrays.asList(changeInfo));
  }

  @Nullable
  private Map<Change.Id, List<PluginDefinedInfo>> pluginInfosFromChangeInfos(RestResponse res)
      throws Exception {
    res.assertOK();
    List<Map<String, Object>> changeInfos =
        GSON.fromJson(res.getReader(), new TypeToken<List<Map<String, Object>>>() {}.getType());
    return getPluginInfosFromChangeInfos(GSON, changeInfos);
  }
}
