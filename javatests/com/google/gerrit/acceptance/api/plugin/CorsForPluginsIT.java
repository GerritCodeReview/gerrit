// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.plugin;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.HttpHeaders.ORIGIN;
import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.plugins.PluginContentScanner;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.client.fluent.Request;
import org.junit.Test;

public class CorsForPluginsIT extends AbstractDaemonTest {

  static class FooPluginHttpModule extends ServletModule {
    @Override
    public void configureServlets() {
      serve("/bar").with(BarServlet.class);
    }
  }

  @Singleton
  static class BarServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      resp.setContentType("text/plain");
      try (PrintWriter out = resp.getWriter()) {
        out.println("Hi!");
      }
    }
  }

  @Test
  public void noCorsConfig_CorsNotAllowed() throws Exception {
    installPlugin("foo", null, FooPluginHttpModule.class, null, PluginContentScanner.EMPTY);

    RestResponse rsp = execute("/plugins/foo/Documentation/foo.html", "evil");
    assertThat(rsp.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();

    rsp = execute("/plugins/foo/bar", "evil");
    assertThat(rsp.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
  }

  @Test
  @GerritConfig(name = "site.allowOriginRegex", value = "friend")
  public void configConfigured_onlyMatchingOriginAllowed() throws Exception {
    installPlugin("foo", null, FooPluginHttpModule.class, null, PluginContentScanner.EMPTY);

    RestResponse rsp;

    rsp = execute("/plugins/foo/Documentation/foo.html", "evil");
    assertThat(rsp.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    rsp = execute("/plugins/foo/bar", "evil");
    assertThat(rsp.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();

    rsp = execute("/plugins/foo/static/resource", "friend");
    assertThat(rsp.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNotNull();

    // TODO: this should also work
    // rsp = execute("/plugins/foo/bar", "friend");
    // assertThat(rsp.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNotNull();
  }

  private RestResponse execute(String path, String origin) throws Exception {
    UrlEncoded url = new UrlEncoded(canonicalWebUrl.get() + path);
    Request req = Request.Get(url.toString());
    req.setHeader(ORIGIN, origin);
    return adminRestSession.execute(req);
  }
}
