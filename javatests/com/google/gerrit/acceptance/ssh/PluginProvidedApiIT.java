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
import static com.google.gerrit.server.query.change.OutputStreamQuery.GSON;

import com.google.gerrit.acceptance.AbstractPluginProvidedApiTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.data.ChangeAttribute;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

@NoHttpd
@UseSsh
public class PluginProvidedApiIT extends AbstractPluginProvidedApiTest {

  @Test
  public void testPluginProvidedApi() throws Exception {
    try (AutoCloseable ignored1 =
            installPlugin(PLUGIN_PROVIDING_API, PluginProvidedApiModule.class);
        AutoCloseable ignored2 = installPlugin(PLUGIN_USING_API, PluginUsingApiModule.class)) {
      testChangeApi();
    }
  }

  @Test
  public void testPluginProvidedApiUsingProxyInstance() throws Exception {
    try (AutoCloseable ignored1 =
            installPlugin(PLUGIN_PROVIDING_API, PluginProvidedApiModule.class);
        AutoCloseable ignored2 =
            installPlugin(PLUGIN_USING_API_TYPE_CAST, PluginUsingApiWithTypeCastModule.class)) {
      testChangeApi();
    }
  }

  protected void testChangeApi() throws Exception {
    createChange();
    List<ChangeAttribute> changes =
        getChanges(adminSshSession.exec("gerrit query --format json status:open"));
    adminSshSession.assertSuccess();
    assertThat(changes).isNotEmpty();
    assertThat(changes.get(0)).isNotNull();
    assertThat(changes.get(0).plugins).isNotNull();
    assertThat(changes.get(0).plugins).hasSize(1);
    assertThat(changes.get(0).plugins.get(0).message).isEqualTo("test_data");
  }

  protected static List<ChangeAttribute> getChanges(String rawResponse) {
    String[] lines = rawResponse.split("\\n");
    List<ChangeAttribute> changes = new ArrayList<>(lines.length - 1);
    for (int i = 0; i < lines.length - 1; i++) {
      changes.add(GSON.fromJson(lines[i], ChangeAttribute.class));
    }
    return changes;
  }
}
