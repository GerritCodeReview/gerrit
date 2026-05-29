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
import java.util.Arrays;
import org.junit.Test;

@NoHttpd
public class PluginFieldsIT extends AbstractPluginFieldsTest {
  // No tests for /detail via the extension API, since the extension API doesn't have that method.

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
  public void queryChangeWithOptionBulkAttribute() throws Exception {
    getChangeWithPluginDefinedBulkAttributeOption(
        id -> pluginInfosFromChangeInfos(gApi.changes().query(id.toString()).get()),
        (id, opts) ->
            pluginInfosFromChangeInfos(
                gApi.changes().query(id.toString()).withPluginOptions(opts).get()));
  }

  @Test
  public void getChangeWithOptionBulkAttribute() throws Exception {
    getChangeWithPluginDefinedBulkAttributeOption(
        id -> pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.get()).get())),
        (id, opts) ->
            pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.get()).get(opts))));
  }

  @Test
  public void queryMultipleChangesWithPluginDefinedAttribute() throws Exception {
    getMultipleChangesWithPluginDefinedBulkAttribute(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  @Test
  public void queryChangesByCommitMessageWithPluginDefinedBulkAttribute() throws Exception {
    getChangesByCommitMessageWithPluginDefinedBulkAttribute(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  @Test
  public void getMultipleChangesWithPluginDefinedAttributeInSingleCall() throws Exception {
    getMultipleChangesWithPluginDefinedBulkAttributeInSingleCall(
        () -> pluginInfosFromChangeInfos(gApi.changes().query("status:open").get()));
  }

  @Test
  public void getChangeWithPluginDefinedException() throws Exception {
    getChangeWithPluginDefinedBulkAttributeWithException(
        id -> pluginInfosFromChangeInfos(Arrays.asList(gApi.changes().id(id.get()).get())));
  }
}
