// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.junit.Before;
import org.junit.Test;

public class HttpLogoutServletTest extends StandaloneSiteTest {
  private static final String LOCALHOST = InetAddress.getLoopbackAddress().getHostName();

  @ConfigSuite.Config
  public static Config secondConfig() throws IOException {
    Config cfg = new Config();
    cfg.setString("auth", null, "logouturl", "/test-logout");
    cfg.setString("gerrit", null, "canonicalWebUrl", "https://" + LOCALHOST + ":8443/");
    cfg.setString("httpd", null, "listenUrl", "proxy-https://" + LOCALHOST + ":" + getFreePort());
    return cfg;
  }

  @Inject @GerritServerConfig private Config gerritConfig;

  private HttpClient httpClient;

  @Before
  public void setUp() {
    httpClient = HttpClientBuilder.create().disableRedirectHandling().build();
  }

  @Test
  public void shouldHonourCanonicalWebUrlProxyWhenRedirectAfterLogout() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      URIish listenUrl = new URIish(gerritConfig.getString("httpd", null, "listenUrl"));
      URIish canonicalWebUrl =
          new URIish(gerritConfig.getString("gerrit", null, "canonicalWebUrl"));

      String logoutPath =
          Optional.ofNullable(baseConfig.getString("auth", null, "logouturl")).orElse("/");

      HttpGet getLogout = new HttpGet("/logout");
      getLogout.addHeader("X-Forwarded-Host", canonicalWebUrl.getHost());
      getLogout.addHeader("X-Forwarded-Port", "" + canonicalWebUrl.getPort());
      getLogout.addHeader("X-Forwarded-Proto", canonicalWebUrl.getScheme());

      HttpResponse logoutResponse =
          httpClient.execute(new HttpHost(listenUrl.getHost(), listenUrl.getPort()), getLogout);

      assertThat(logoutResponse.getStatusLine().getStatusCode())
          .isEqualTo(HttpStatus.SC_MOVED_TEMPORARILY);
      assertThat(getLocationHeaderURIish(logoutResponse))
          .containsExactly(canonicalWebUrl.setPath(logoutPath));
    }
  }

  private List<URIish> getLocationHeaderURIish(HttpResponse logoutResponse) {
    List<URIish> locationHeaders =
        Arrays.stream(logoutResponse.getHeaders("Location"))
            .map(h -> h.getValue())
            .map(HttpLogoutServletTest::unsafeNewURIish)
            .filter(u -> u.isPresent())
            .map(u -> u.get())
            .collect(Collectors.toList());
    return locationHeaders;
  }

  private static Optional<URIish> unsafeNewURIish(String uri) {
    try {
      return Optional.of(new URIish(uri));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static int getFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
