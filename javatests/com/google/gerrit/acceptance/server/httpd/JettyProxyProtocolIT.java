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

import com.google.gerrit.acceptance.GerritServer.TestHttpServerAddress;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Assume;
import org.junit.Test;

public class JettyProxyProtocolIT extends StandaloneSiteTest {
  private static final String SERVER_ADDRESS = "127.0.0.1";

  // Carried in the PROXY header, never bound as a socket address. Kept distinct from the
  // connecting peer (127.0.0.1) so the trusted tests prove trust is based on the real peer, not
  // this header value.
  private static final String PROXY_SOURCE_ADDRESS = "198.51.100.1";
  private static final String FORWARDED_CLIENT_ADDRESS = "192.0.2.1";

  // Trust is config-driven, not bound to source addresses. macOS assigns only 127.0.0.1 to lo0 by
  // default, so binding any other 127.x fails with EADDRNOTAVAIL even though the range routes to
  // lo0 (see bind(2)). Linux lets a socket bind to any 127.0.0.0/8 address.
  private static final String TRUSTED_PROXY_NETWORK = SERVER_ADDRESS + "/32";
  private static final String UNTRUSTED_PROXY_NETWORK = "127.0.0.2/32";
  private static final int PROXY_SOURCE_PORT = 12345; // In the PROXY header, not asserted.
  private static final byte[] PROXY_PROTOCOL_V2_SIGNATURE = {
    0x0d, 0x0a, 0x0d, 0x0a, 0x00, 0x0d, 0x0a, 0x51, 0x55, 0x49, 0x54, 0x0a
  };
  private static final String PROXY_PROTOCOL_HEADER_PREFIX =
      "PROXY TCP4 " + PROXY_SOURCE_ADDRESS + " " + SERVER_ADDRESS + " " + PROXY_SOURCE_PORT + " ";

  @ConfigSuite.Default
  public static Config config() throws IOException {
    return config(true);
  }

  @ConfigSuite.Config
  public static Config proxyProtocolDisabledConfig() throws IOException {
    return config(false);
  }

  private static Config config(boolean enableProxyProtocol) throws IOException {
    Config cfg = new Config();
    cfg.setString("auth", null, "type", "HTTP");
    cfg.setString("auth", null, "httpHeader", "X-Remote-User");
    // Must stay non-empty, or every peer is trusted. The untrusted tests replace it via
    // untrustConnectingPeer() with a network that excludes the connecting peer.
    cfg.setStringList("auth", null, "httpTrustedProxyNetworks", List.of(TRUSTED_PROXY_NETWORK));
    cfg.setBoolean("auth", null, "trustContainerAuth", true);
    int serverPort = HttpTestUtil.getFreePort();
    cfg.setString("httpd", null, "listenUrl", "proxy-http://" + SERVER_ADDRESS + ":" + serverPort);
    cfg.setBoolean("httpd", null, "enableProxyProtocol", enableProxyProtocol);
    cfg.setStringList(
        "httpd", null, "filterClass", List.of(HttpTestUtil.RemoteAddressFilter.class.getName()));
    return cfg;
  }

  @Inject @TestHttpServerAddress private InetSocketAddress httpAddress;

  private enum ProxyProtocolFormat {
    V1_PROXY,
    V2_PROXY,
    V2_LOCAL
  }

  private static final List<ProxyProtocolFormat> PROXY_FORMATS =
      List.of(ProxyProtocolFormat.V1_PROXY, ProxyProtocolFormat.V2_PROXY);

  @Test
  public void trustedProxyAllowsHttpHeaderAuthenticationWithProxyProtocol() throws Exception {
    Assume.assumeTrue(baseConfig.getBoolean("httpd", null, "enableProxyProtocol", false));

    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      int serverPort = httpAddress.getPort();
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "Connection: close\r\n\r\n";
      for (ProxyProtocolFormat format : PROXY_FORMATS) {
        byte[] proxyRequest = createProxyRequest(format, serverPort, request);

        HttpResponse proxyResponse = sendRequest(serverPort, proxyRequest);
        assertThat(proxyResponse.statusCode()).isEqualTo(200);
        assertThat(proxyResponse.body()).contains(admin.username());
        assertThat(proxyResponse.headers())
            .contains(HttpTestUtil.REMOTE_ADDRESS_HEADER + ": " + PROXY_SOURCE_ADDRESS);
      }
    }
  }

  @Test
  public void directHttpFromUntrustedPeerCannotUseHttpHeaderAuthentication() throws Exception {
    Assume.assumeTrue(baseConfig.getBoolean("httpd", null, "enableProxyProtocol", false));

    untrustConnectingPeer();
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      int serverPort = httpAddress.getPort();
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "Connection: close\r\n\r\n";

      HttpResponse response = sendRequest(serverPort, request);
      assertThat(response.statusCode()).isEqualTo(403);
    }
  }

  @Test
  public void untrustedProxyCannotUseProxyProtocolForHttpHeaderAuthentication() throws Exception {
    Assume.assumeTrue(baseConfig.getBoolean("httpd", null, "enableProxyProtocol", false));

    untrustConnectingPeer();
    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      int serverPort = httpAddress.getPort();
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "Connection: close\r\n\r\n";
      for (ProxyProtocolFormat format : PROXY_FORMATS) {
        byte[] proxyRequest = createProxyRequest(format, serverPort, request);

        HttpResponse response = sendRequest(serverPort, proxyRequest);
        assertThat(response.statusCode()).isEqualTo(403);
      }
    }
  }

  @Test
  public void xForwardedForTakesPrecedenceOverProxyProtocolAddress() throws Exception {
    Assume.assumeTrue(baseConfig.getBoolean("httpd", null, "enableProxyProtocol", false));

    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      int serverPort = httpAddress.getPort();
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "X-Forwarded-For: "
              + FORWARDED_CLIENT_ADDRESS
              + "\r\n"
              + "Connection: close\r\n\r\n";
      for (ProxyProtocolFormat format : PROXY_FORMATS) {
        byte[] proxyRequest = createProxyRequest(format, serverPort, request);

        HttpResponse response = sendRequest(serverPort, proxyRequest);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(admin.username());
        assertThat(response.headers())
            .contains(HttpTestUtil.REMOTE_ADDRESS_HEADER + ": " + FORWARDED_CLIENT_ADDRESS);
      }
    }
  }

  // A v2 LOCAL command describes a connection made by the proxy itself, not a relayed client.
  // The receiver must ignore the address block and keep the actual socket peer address.
  @Test
  public void localCommandUsesActualPeerAddress() throws Exception {
    Assume.assumeTrue(baseConfig.getBoolean("httpd", null, "enableProxyProtocol", false));

    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      int serverPort = httpAddress.getPort();
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "Connection: close\r\n\r\n";
      byte[] localRequest = createProxyRequest(ProxyProtocolFormat.V2_LOCAL, serverPort, request);

      HttpResponse response = sendRequest(serverPort, localRequest);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains(admin.username());
      assertThat(response.headers())
          .contains(HttpTestUtil.REMOTE_ADDRESS_HEADER + ": " + SERVER_ADDRESS);
      assertThat(response.headers()).doesNotContain(PROXY_SOURCE_ADDRESS);
    }
  }

  @Test
  public void proxyProtocolHeaderIsRejectedWhenDisabled() throws Exception {
    Assume.assumeFalse(baseConfig.getBoolean("httpd", null, "enableProxyProtocol", false));

    try (ServerContext ctx = startServer()) {
      ctx.getInjector().injectMembers(this);
      int serverPort = httpAddress.getPort();
      String request =
          "GET /a/accounts/self HTTP/1.1\r\n"
              + "Host: localhost\r\n"
              + "X-Remote-User: "
              + admin.username()
              + "\r\n"
              + "Connection: close\r\n\r\n";
      for (ProxyProtocolFormat format : PROXY_FORMATS) {
        byte[] proxyRequest = createProxyRequest(format, serverPort, request);

        HttpResponse response = sendRequest(serverPort, proxyRequest);
        assertThat(response.statusCode()).isEqualTo(400);
      }
    }
  }

  // Narrow the trusted-proxy networks to exclude the connecting peer (SERVER_ADDRESS), so it is
  // treated as untrusted.
  private void untrustConnectingPeer() throws IOException, ConfigInvalidException {
    FileBasedConfig cfg = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.DETECTED);
    cfg.load();
    cfg.setStringList("auth", null, "httpTrustedProxyNetworks", List.of(UNTRUSTED_PROXY_NETWORK));
    cfg.save();
  }

  private static HttpResponse sendRequest(int port, String request) throws IOException {
    return sendRequest(port, request.getBytes(StandardCharsets.US_ASCII));
  }

  private static HttpResponse sendRequest(int port, byte[] request) throws IOException {
    try (Socket socket = new Socket(SERVER_ADDRESS, port)) {
      socket.getOutputStream().write(request);
      socket.getOutputStream().flush();
      return parseResponse(
          new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  private static byte[] createProxyRequest(
      ProxyProtocolFormat format, int targetPort, String request) throws IOException {
    if (format == ProxyProtocolFormat.V1_PROXY) {
      return (PROXY_PROTOCOL_HEADER_PREFIX + targetPort + "\r\n" + request)
          .getBytes(StandardCharsets.US_ASCII);
    }
    byte command = format == ProxyProtocolFormat.V2_LOCAL ? (byte) 0x20 : (byte) 0x21;
    return concat(createProxyProtocolV2Header(command, targetPort), request);
  }

  private static byte[] createProxyProtocolV2Header(byte command, int targetPort)
      throws IOException {
    ByteBuffer header = ByteBuffer.allocate(28);
    header.put(PROXY_PROTOCOL_V2_SIGNATURE);
    header.put(command); // Version 2, PROXY or LOCAL command.
    header.put((byte) 0x11); // IPv4, stream transport.
    header.putShort((short) 12); // IPv4 addresses and ports: 4 + 4 + 2 + 2 bytes.
    header.put(InetAddress.getByName(PROXY_SOURCE_ADDRESS).getAddress());
    header.put(InetAddress.getByName(SERVER_ADDRESS).getAddress());
    header.putShort((short) PROXY_SOURCE_PORT);
    header.putShort((short) targetPort);
    return header.array();
  }

  private static byte[] concat(byte[] header, String request) {
    byte[] requestBytes = request.getBytes(StandardCharsets.US_ASCII);
    byte[] result = new byte[header.length + requestBytes.length];
    System.arraycopy(header, 0, result, 0, header.length);
    System.arraycopy(requestBytes, 0, result, header.length, requestBytes.length);
    return result;
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
          Integer.parseInt(statusLineParts[1]),
          response.substring(statusLineEnd + 2, headerEnd),
          response.substring(headerEnd + 4));
    } catch (NumberFormatException e) {
      throw new IOException("Invalid HTTP status code: " + statusLineParts[1], e);
    }
  }

  private record HttpResponse(int statusCode, String headers, String body) {}
}
