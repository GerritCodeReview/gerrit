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

package com.google.gerrit.acceptance.rest.binding;

import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

/**
 * Tests for checking plugin-provided REST API bindings.
 *
 * <p>These tests only verify that the plugin-provided REST endpoints are correctly bound, they do
 * not test the functionality of the plugin REST endpoints.
 */
public class PluginProvidedRestApiBindingsIT extends AbstractDaemonTest {

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
}
