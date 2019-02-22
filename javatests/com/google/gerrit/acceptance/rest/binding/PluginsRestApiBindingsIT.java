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

package com.google.gerrit.acceptance.rest.binding;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.restapi.RawInput;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * Tests for checking the bindings of the plugins REST API.
 *
 * <p>These tests only verify that the plugin REST endpoints are correctly bound, they do no test
 * the functionality of the plugin REST endpoints.
 */
public class PluginsRestApiBindingsIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("plugins", null, "allowRemoteAdmin", true);
    return cfg;
  }

  /**
   * Plugin REST endpoints to be tested.
   *
   * <p>Each URL contains a placeholder for the plugin identifier.
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

  /**
   * Plugin REST endpoints bound by {@link MyPluginModule} with Guice serlvet definitions.
   *
   * <p>Each URL contains a placeholder for the plugin identifier.
   *
   * <p>Currently does not include any resource or documentation URLs, since those would require
   * installing a plugin from a jar, which is trickier than just defining a module in this file.
   */
  private static final ImmutableList<RestCall> SERVER_TOP_LEVEL_PLUGIN_ENDPOINTS =
      ImmutableList.of(RestCall.get("/plugins/%s/hello"));

  private static final String JS_PLUGIN = "Gerrit.install(function(self){});\n";
  private static final RawInput JS_PLUGIN_CONTENT = RawInputUtil.create(JS_PLUGIN.getBytes(UTF_8));

  @Test
  public void pluginAdminEndpoints() throws Exception {
    String pluginName = "my-plugin";
    try (AutoCloseable ignored = installJsPlugin(pluginName)) {
      RestApiCallHelper.execute(adminRestSession, PLUGIN_ENDPOINTS, pluginName);
    }
  }

  static class MyPluginModule extends ServletModule {
    @Override
    public void configureServlets() {
      serve("/hello").with(HelloServlet.class);
    }
  }

  @Singleton
  static class HelloServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
      res.setStatus(SC_OK);
      res.getWriter().println("Hello world");
    }
  }

  @Test
  public void serverPluginTopLevelEndpoints() throws Exception {
    String pluginName = "my-plugin";
    try (AutoCloseable ignored = installPlugin(pluginName, null, MyPluginModule.class, null)) {
      RestApiCallHelper.execute(adminRestSession, SERVER_TOP_LEVEL_PLUGIN_ENDPOINTS, pluginName);
    }
  }

  private AutoCloseable installJsPlugin(String pluginName) throws Exception {
    InstallPluginInput input = new InstallPluginInput();
    input.raw = JS_PLUGIN_CONTENT;
    gApi.plugins().install(pluginName + ".js", input);
    return () -> gApi.plugins().name(pluginName).disable();
  }
}
