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

package com.google.gerrit.acceptance.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.restapi.RawInput;
import org.junit.Test;

/**
 * Tests for checking the bindings of the plugins REST API.
 *
 * <p>These tests only verify that the plugin REST endpoints are correctly bound, they do no test
 * the functionality of the plugin REST endpoints (for details see JavaDoc on {@link
 * AbstractRestApiBindingsTest}).
 */
public class PluginsRestApiBindingsIT extends AbstractRestApiBindingsTest {
  /**
   * Plugin REST endpoints to be tested, each URL contains a placeholder for the plugin identifier.
   */
  private static final ImmutableList<RestCall> PLUGIN_ENDPOINTS =
      ImmutableList.of(
          RestCall.put("/plugins/%s"),

          // For GET requests prefixing the view name with 'gerrit~' is required.
          RestCall.get("/plugins/%s/gerrit~status"),

          // POST (and PUT) requests don't require the 'gerrit~' prefix in front of the view name.
          RestCall.post("/plugins/%s/gerrit~enable"),
          RestCall.post("/plugins/%s/gerrit~disable"),
          RestCall.post("/plugins/%s/gerrit~reload"),

          // Plugin deletion must be tested last
          RestCall.delete("/plugins/%s"));

  private static final String JS_PLUGIN = "Gerrit.install(function(self){});\n";
  private static final RawInput JS_PLUGIN_CONTENT = RawInputUtil.create(JS_PLUGIN.getBytes(UTF_8));

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void pluginEndpoints() throws Exception {
    String pluginName = "my-plugin";
    installPlugin(pluginName);
    execute(PLUGIN_ENDPOINTS, pluginName);
  }

  private void installPlugin(String pluginName) throws Exception {
    InstallPluginInput input = new InstallPluginInput();
    input.raw = JS_PLUGIN_CONTENT;
    gApi.plugins().install(pluginName + ".js", input);
  }
}
