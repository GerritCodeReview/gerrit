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

import com.google.gerrit.acceptance.AbstractPluginFieldsTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.change.ChangeAttributeFactory;
import com.google.inject.AbstractModule;
import java.util.Arrays;
import org.junit.Test;

@NoHttpd
public class PluginFieldsIT extends AbstractPluginFieldsTest {
  // No tests for /detail via the extension API, since the extension API doesn't have that method.

  @Test
  public void queryChangeWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromSingletonList(gApi.changes().query(id.toString()).get()));
  }

  @Test
  public void getChangeWithNullAttribute() throws Exception {
    getChangeWithNullAttribute(
        id -> pluginInfoFromChangeInfo(gApi.changes().id(id.toString()).get()));
  }

  @Test
  public void pluginDefinedQueryChangeWithNullAttribute() throws Exception {
    getChangeWithNullBulkAttribute(
        id -> pluginInfosFromChangeInfos(gApi.changes().query(id.toString()).get()));
  }

  @Test
  public void pluginDefinedGetChangeWithNullAttribute() throws Exception {
    getChangeWithNullBulkAttribute(
        id -> pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.toString()).get())));
  }

  @Test
  public void queryChangeWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromSingletonList(gApi.changes().query(id.toString()).get()));
  }

  @Test
  public void getChangeWithSimpleAttribute() throws Exception {
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromChangeInfo(gApi.changes().id(id.toString()).get()));
  }

  @Test
  public void querySingleChangeWithBulkAttribute() throws Exception {
    getSingleChangeWithPluginDefinedBulkAttribute(
        id -> pluginInfosFromChangeInfos(gApi.changes().query(id.toString()).get()));
  }

  @Test
  public void getSingleChangeWithBulkAttribute() throws Exception {
    getSingleChangeWithPluginDefinedBulkAttribute(
        id -> pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.toString()).get())));
  }

  @Test
  public void queryChangeWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromSingletonList(gApi.changes().query(id.toString()).get()),
        (id, opts) ->
            pluginInfoFromSingletonList(
                gApi.changes().query(id.toString()).withPluginOptions(opts).get()));
  }

  @Test
  public void getChangeWithOption() throws Exception {
    getChangeWithOption(
        id -> pluginInfoFromChangeInfo(gApi.changes().id(id.get()).get()),
        (id, opts) -> pluginInfoFromChangeInfo(gApi.changes().id(id.get()).get(opts)));
  }

  @Test
  public void queryChangeWithOptionBulkAttribute() throws Exception {
    getChangeWithPluginDefinedAttributeOption(
        id -> pluginInfosFromChangeInfos(gApi.changes().query(id.toString()).get()),
        (id, opts) ->
            pluginInfosFromChangeInfos(
                gApi.changes().query(id.toString()).withPluginOptions(opts).get()));
  }

  @Test
  public void getChangeWithOptionBulkAttribute() throws Exception {
    getChangeWithPluginDefinedAttributeOption(
        id -> pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.get()).get())),
        (id, opts) ->
            pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.get()).get(opts))));
  }

  @Test
  public void queryMultipleChangesWithPluginDefinedAttribute() throws Exception {
    getMultipleChangesWithPluginDefinedAttribute(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  @Test
  public void queryEvenChangesWithPluginDefinedAttribute() throws Exception {
    getEvenChangesWithPluginDefinedAttribute(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  @Test
  public void getMultipleChangesWithPluginDefinedAndChangeAttributes() throws Exception {
    getMultipleChangesWithPluginDefinedAndChangeAttributes(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  @Test
  public void getMultipleChangesWithPluginDefinedAttributeInSingleCall() throws Exception {
    getMultipleChangesWithPluginDefinedAttributeInSingleCall(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  static class SimpleAttributeWithExplicitExportModule extends AbstractModule {
    @Override
    public void configure() {
      bind(ChangeAttributeFactory.class)
          .annotatedWith(Exports.named("simple"))
          .toInstance((cd, bp, p) -> new MyInfo("change " + cd.getId()));
    }
  }

  @Test
  public void getChangeWithSimpleAttributeWithExplicitExport() throws Exception {
    // For backwards compatibility with old plugins, allow modules to bind into the
    // DynamicSet<ChangeAttributeFactory> as if it were a DynamicMap. We only need one variant of
    // this test to prove that the mapping works.
    getChangeWithSimpleAttribute(
        id -> pluginInfoFromChangeInfo(gApi.changes().id(id.toString()).get()),
        SimpleAttributeWithExplicitExportModule.class);
  }
}
