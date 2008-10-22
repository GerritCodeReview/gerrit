// Copyright 2008 Google Inc.
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

package com.google.codereview.rpc;

import com.google.codereview.NeedRetry.RetryRequestLaterResponse;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Message.Builder;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.spearce.jgit.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Simple protocol buffer RPC embedded inside HTTP POST.
 * <p>
 * Request messages are POSTed to "/proto/$serviceName/$methodName".
 * Authentication for the user is always obtained via the user's Google Account,
 * with session cookies automatically renewing themselves if expired.
 * <p>
 * This implementation is thread safe. Callers may use a single HttpRpc over
 * multiple threads in order to pool and reuse connections across threads.
 * <p>
 * All remote calls are performed synchronously. This is a simplification within
 * the implementation, as it is unlikely the remote side can support parallel
 * requests over the same top level entities.
 * <p>
 * Authentication is tied to Google accounts.
 */
public class HttpRpc implements RpcChannel {
  private static final String ENC = "UTF-8";

  private static final Message RETRY_LATER =
      RetryRequestLaterResponse.getDefaultInstance();

  /** URL required to authenticate the user into their Google account. */
  private static final URI LOGIN;

  /**
   * Maximum number of connections we should make to 'production' servers.
   * <p>
   * If the servers are running on Google owned infrastructure such as the
   * www.google.com or Google App Engine we can generally push at least one or
   * two connections per thread without causing any trouble.
   */
  private static final int MAX_PROD_CONNS = 10;

  /** Special token used in authentication to verify we were successful. */
  private static final String AUTH_CONTINUE_TOKEN = "http://localhost/";

  static {
    try {
      LOGIN = new URI("https://www.google.com/accounts/ClientLogin", true);
    } catch (URIException err) {
      final ExceptionInInitializerError le;
      le = new ExceptionInInitializerError("Bad LOGIN URL");
      le.initCause(err);
      throw le;
    }
  }

  private final HttpClient http;
  private final URI server;
  private final String userEmail;
  private final String userPassword;
  private final byte[] apiKey;
  private boolean authenticated;

  /**
   * Create a new RPC implementation.
   * 
   * @param host protocol, server host, and port number. The path component is
   *        ignored as requests are made absolute ('/proto/$service/$method').
   * @param user name to authenticate to Google with. This is very likely the
   *        user's email address.
   * @param pass password for the user's Google account.
   * @param apiKeyStr (optional) the internal API key. If supplied all requests
   *        will be signed with the key, in case the request requires the
   *        internal API authentication.
   */
  public HttpRpc(final URL host, final String user, final String pass,
      final String apiKeyStr) {
    http = new HttpClient(new MultiThreadedHttpConnectionManager());
    userEmail = user;
    userPassword = pass;
    apiKey = apiKeyStr != null ? Base64.decode(apiKeyStr) : null;

    final HttpConnectionManagerParams params =
        http.getHttpConnectionManager().getParams();
    try {
      server = new URI(host.toExternalForm(), true);

      final HostConfiguration serverConfig = new HostConfiguration();
      serverConfig.setHost(server);

      // The development server cannot do normal authentication so we must
      // fake it here by injecting a development server specific cookie.
      //
      if ("localhost".equals(server.getHost())) {
        if (userEmail != null) {
          final Cookie c = new Cookie();
          c.setDomain(server.getHost());
          c.setPath("/");
          c.setName("dev_appserver_login");
          c.setValue(userEmail + ":False");
          http.getState().addCookie(c);
        }
        authenticated = true;
        params.setMaxConnectionsPerHost(serverConfig, 1);
      } else {
        params.setMaxConnectionsPerHost(serverConfig, MAX_PROD_CONNS);
      }

      final HostConfiguration clientLoginConfig = new HostConfiguration();
      clientLoginConfig.setHost(LOGIN);
      params.setMaxConnectionsPerHost(clientLoginConfig, MAX_PROD_CONNS);
    } catch (URIException e) {
      throw new IllegalArgumentException("Bad URL: " + host.toExternalForm(), e);
    }
  }

  public void callMethod(final MethodDescriptor method,
      final RpcController controller, final Message request,
      final Message responsePrototype, final RpcCallback<Message> done) {
    final URI uri;
    final String svcName = method.getService().getName();
    final String methodName = method.getName();
    try {
      uri = new URI(server, "/proto/" + svcName + "/" + methodName, true);
    } catch (URIException urlError) {
      controller.setFailed(urlError.toString());
      return;
    }

    final MessageRequestEntity entity;
    try {
      entity = new MessageRequestEntity(request);
    } catch (IOException err) {
      controller.setFailed("cannot encode request: " + err);
      return;
    }

    if (controller instanceof SimpleController) {
      ((SimpleController) controller).markFirstRequest();
    }

    for (;;) {
      final PostMethod conn = new PostMethod();
      Message responseMessage = null;
      try {
        conn.setDoAuthentication(false);
        conn.setURI(uri);
        conn.setRequestEntity(entity);

        ensureAuthenticated();
        for (int attempts = 1; responseMessage == null; attempts++) {
          if (apiKey != null) {
            sign(conn);
          }

          final int status = http.executeMethod(conn);
          if (HttpStatus.SC_OK == status) {
            responseMessage = parseResponse(conn, responsePrototype);
          } else if (HttpStatus.SC_UNAUTHORIZED == status) {
            if (attempts == 2) {
              responseMessage = null;
              controller.setFailed("Authentication required");
              break;
            }
            synchronized (this) {
              authenticated = false;
              ensureAuthenticated();
            }
          } else if (HttpStatus.SC_FORBIDDEN == status
              || HttpStatus.SC_NOT_FOUND == status
              || HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE == status) {
            String body = conn.getResponseBodyAsString(60);
            if (body.indexOf('\n') > 0)
              body = body.substring(0, body.indexOf('\n'));
            responseMessage = null;
            controller.setFailed(svcName + ": " + body);
            break;
          } else {
            responseMessage = null;
            controller.setFailed("HTTP failure: " + status);
            break;
          }
        }
      } catch (ConnectException ce) {
        final String why = ce.getMessage() + " " + server;
        if (controller instanceof SimpleController) {
          final SimpleController sc = (SimpleController) controller;
          if (sc.retry()) {
            continue;
          } else {
            controller.setFailed(controller.errorText() + ": " + why);
            break;
          }
        } else {
          controller.setFailed(why);
          break;
        }
      } catch (IOException err) {
        controller.setFailed("HTTP failure: " + err.getMessage());
        break;
      } finally {
        conn.releaseConnection();
      }

      if (responseMessage instanceof RetryRequestLaterResponse) {
        if (controller instanceof SimpleController) {
          final SimpleController sc = (SimpleController) controller;
          if (sc.retry()) {
            continue;
          }
        }

        controller.setFailed("remote requested retry");
        break;
      }

      if (responseMessage != null) {
        done.run(responseMessage);
      }

      break;
    }

    entity.destroy();
  }

  private Message parseResponse(final PostMethod conn,
      final Message responsePrototype) throws IOException {
    final Header typeHeader = conn.getResponseHeader("Content-Type");
    if (typeHeader == null) {
      throw new IOException("No Content-Type in response");
    }
    final String[] typeTokens = typeHeader.getValue().split("; ");
    if (!MessageRequestEntity.TYPE.equals(typeTokens[0])) {
      throw new IOException("Invalid Content-Type " + typeHeader.getValue());
    }

    String rspName = null;
    String rspCompress = null;
    for (int i = 1; i < typeTokens.length; i++) {
      final String tok = typeTokens[i];
      if (tok.startsWith("name=")) {
        rspName = tok.substring("name=".length());
      } else if (tok.startsWith("compress=")) {
        rspCompress = tok.substring("compress=".length());
      }
    }

    String expName = responsePrototype.getDescriptorForType().getFullName();
    final Builder builder;
    if (expName.equals(rspName)) {
      builder = responsePrototype.newBuilderForType();
    } else if (rspName.equals(RETRY_LATER.getDescriptorForType().getFullName())) {
      builder = RETRY_LATER.newBuilderForType();
    } else {
      throw new IOException("Expected a " + expName + " got " + rspName);
    }

    InputStream in = conn.getResponseBodyAsStream();
    if ("deflate".equals(rspCompress)) {
      in = new InflaterInputStream(in);
    } else if (rspCompress != null) {
      throw new IOException("Unsupported compression " + rspCompress);
    }

    try {
      builder.mergeFrom(in);
    } finally {
      in.close();
    }
    return builder.build();
  }

  private synchronized void ensureAuthenticated() throws IOException {
    if (!authenticated) {
      if (userEmail != null && userPassword != null) {
        computeAuthCookie(computeAuthToken());
      }
      authenticated = true;
    }
  }

  private String computeAuthToken() throws IOException {
    String accountType = "GOOGLE";
    if (server.getHost().endsWith(".google.com")) {
      accountType = "HOSTED";
    }

    final PostMethod conn = new PostMethod();
    try {
      conn.setDoAuthentication(false);
      conn.setURI(LOGIN);
      conn.setRequestBody(new NameValuePair[] {
          new NameValuePair("Email", userEmail),
          new NameValuePair("Passwd", userPassword),
          new NameValuePair("service", "ah"),
          new NameValuePair("source", "gerrit-codereview-manager"),
          new NameValuePair("accountType", accountType)});

      final int status = http.executeMethod(conn);
      final Map<String, String> rsp =
          splitPairs(conn.getResponseBodyAsStream());
      if (status != HttpStatus.SC_OK) {
        throw new IOException("Authentication failed: " + rsp.get("Error"));
      }
      return rsp.get("Auth");
    } finally {
      conn.releaseConnection();
    }
  }

  private void computeAuthCookie(final String authToken) throws IOException {
    final GetMethod conn = new GetMethod();
    try {
      conn.setFollowRedirects(false);
      conn.setDoAuthentication(false);
      conn.setURI(new URI(server, "/_ah/login", true));
      conn.setQueryString(new NameValuePair[] {
          new NameValuePair("continue", AUTH_CONTINUE_TOKEN),
          new NameValuePair("auth", authToken),});

      final int status = http.executeMethod(conn);
      final Header location = conn.getResponseHeader("Location");
      if (status != HttpStatus.SC_MOVED_TEMPORARILY || location == null
          || !AUTH_CONTINUE_TOKEN.equals(location.getValue())) {
        throw new IOException("Obtaining authentication cookie failed: "
            + status);
      }
    } finally {
      conn.releaseConnection();
    }
  }

  private Map<String, String> splitPairs(final InputStream cin)
      throws IOException {
    final HashMap<String, String> rsp = new HashMap<String, String>();
    final BufferedReader in =
        new BufferedReader(new InputStreamReader(cin, ENC));
    try {
      String line;
      while ((line = in.readLine()) != null) {
        final int eq = line.indexOf('=');
        if (eq >= 0) {
          rsp.put(line.substring(0, eq), line.substring(eq + 1));
        }
      }
    } finally {
      in.close();
    }
    return rsp;
  }

  private void sign(final PostMethod conn) throws IOException {
    conn.setRequestHeader("X-Date-UTC", xDateUTC());

    final StringBuilder b = new StringBuilder();

    b.append("POST ");
    b.append(conn.getPath());
    b.append('\n');

    b.append("X-Date-UTC: ");
    b.append(conn.getRequestHeader("X-Date-UTC").getValue());
    b.append('\n');

    b.append("Content-Type: ");
    b.append(conn.getRequestEntity().getContentType());
    b.append('\n');

    b.append('\n');

    final String sec;
    try {
      final Mac m = Mac.getInstance("HmacSHA1");
      m.init(new SecretKeySpec(apiKey, "HmacSHA1"));
      m.update(b.toString().getBytes("UTF-8"));
      conn.getRequestEntity().writeRequest(new OutputStream() {
        @Override
        public void write(byte[] b, int off, int len) {
          m.update(b, off, len);
        }

        @Override
        public void write(int b) {
          m.update((byte) b);
        }
      });
      sec = Base64.encodeBytes(m.doFinal());
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("No HmacSHA1 support:" + e.getMessage());
    } catch (InvalidKeyException e) {
      throw new IOException("Invalid key: " + e.getMessage());
    }
    conn.setRequestHeader("Authorization", "proto :" + sec);
  }

  private String xDateUTC() {
    return String.valueOf(System.currentTimeMillis() / 1000L);
  }
}
