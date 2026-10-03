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

package com.google.gerrit.acceptance.server.httpd;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

public class JettyProxyProtocolIT extends StandaloneSiteTest {
  @ConfigSuite.Default
  public static Config config() throws IOException {
    Config cfg = new Config();
    cfg.setString("auth", null, "type", "HTTP");
    cfg.setString("auth", null, "httpHeader", "X-Remote-User");
    cfg.setStringList("auth", null, "httpTrustedProxyNetworks", List.of("127.0.0.2/32"));
    cfg.setBoolean("auth", null, "trustContainerAuth", true);
    cfg.setString("httpd", null, "listenUrl", "proxy-http://127.0.0.1:" + getFreePort());
    cfg.setBoolean("httpd", null, "enableProxyProtocol", true);
    return cfg;
  }

  @Inject @GerritServerConfig private Config gerritConfig;

  @Test
  public void proxyProtocolSourceControlsHttpHeaderAuthentication() throws Exception {
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);

      URIish listenUrl = new URIish(gerritConfig.getString("httpd", null, "listenUrl"));
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "Connection: close\r\n\r\n";
      String proxyRequest =
          "PROXY TCP4 127.0.0.2 127.0.0.1 12345 " + listenUrl.getPort() + "\r\n" + request;

      HttpResponse proxyResponse = sendRequest(listenUrl.getPort(), proxyRequest);
      assertThat(proxyResponse.statusCode()).isEqualTo(200);
      assertThat(proxyResponse.body()).contains(admin.username());

      HttpResponse ordinaryHttpResponse = sendRequest(listenUrl.getPort(), request);
      assertThat(ordinaryHttpResponse.statusCode()).isEqualTo(403);
    }
  }

  private static HttpResponse sendRequest(int port, String request) throws IOException {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      return parseResponse(
          new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static HttpResponse parseResponse(String response) throws IOException {
    int headerEnd = response.indexOf("\r\n\r\n");
    if (headerEnd < 0) {
      throw new IOException("HTTP response did not contain a header terminator");
    }
    int statusLineEnd = response.indexOf("\r\n");
    if (statusLineEnd < 0 || statusLineEnd > headerEnd) {
      throw new IOException("HTTP response did not contain a status line");
    }
    String[] statusLineParts = response.substring(0, statusLineEnd).split("\\s+", 3);
    if (statusLineParts.length < 2 || !statusLineParts[0].startsWith("HTTP/")) {
      throw new IOException("Invalid HTTP status line: " + response.substring(0, statusLineEnd));
    }
    try {
      return new HttpResponse(
          Integer.parseInt(statusLineParts[1]), response.substring(headerEnd + 4));
    } catch (NumberFormatException e) {
      throw new IOException("Invalid HTTP status code: " + statusLineParts[1], e);
    }
  }

  private record HttpResponse(int statusCode, String body) {}

  private static int getFreePort() throws IOException {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    }
  }
}
