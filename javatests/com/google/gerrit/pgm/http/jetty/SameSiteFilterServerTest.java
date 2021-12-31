// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.pgm.http.jetty;

import static com.google.common.truth.Truth.assertThat;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

/**
 * End-to-end check that SameSiteFilter survives the round-trip through Jetty 12 ee10's
 * cookie-rendering pipeline -- i.e. that the {@code SameSite} attribute set via
 * {@code Cookie.setAttribute("SameSite", ...)} actually appears in the {@code Set-Cookie} header on
 * the wire. The unit test in {@link SameSiteFilterTest} only verifies that the filter mutates the
 * cookie; this test verifies that ee10's {@code ServletApiResponse.HttpCookieFacade} reads that
 * attribute back out when serializing.
 */
public class SameSiteFilterServerTest {

  @Test
  public void laxAppearsInSetCookieHeader() throws Exception {
    assertThat(setCookieHeaderFor("lax")).contains("SameSite=Lax");
  }

  @Test
  public void strictAppearsInSetCookieHeader() throws Exception {
    assertThat(setCookieHeaderFor("strict")).contains("SameSite=Strict");
  }

  @Test
  public void noneAppearsInSetCookieHeader() throws Exception {
    assertThat(setCookieHeaderFor("none")).contains("SameSite=None");
  }

  @Test
  public void unsetConfigOmitsSameSiteFromSetCookieHeader() throws Exception {
    assertThat(setCookieHeaderFor(null)).doesNotContain("SameSite");
  }

  private static String setCookieHeaderFor(String sameSiteConfig) throws Exception {
    Config cfg = new Config();
    if (sameSiteConfig != null) {
      cfg.setString("httpd", null, "sameSite", sameSiteConfig);
    }

    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setHost("127.0.0.1");
    connector.setPort(0);
    server.addConnector(connector);

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    context.addFilter(new FilterHolder(new SameSiteFilter(cfg)), "/*", EnumSet.of(DispatcherType.REQUEST));
    context.addServlet(new ServletHolder(new CookieEmittingServlet()), "/");
    server.setHandler(context);

    server.start();
    try {
      int port = connector.getLocalPort();
      HttpResponse<String> rsp =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
      return rsp.headers().firstValue("Set-Cookie").orElse("");
    } finally {
      server.stop();
    }
  }

  private static final class CookieEmittingServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp) {
      rsp.addCookie(new Cookie("session", "abc"));
      rsp.setStatus(HttpServletResponse.SC_OK);
    }
  }
}
