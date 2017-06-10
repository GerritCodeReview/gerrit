// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_MAX_AGE;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.google.common.net.HttpHeaders.ORIGIN;
import static com.google.common.net.HttpHeaders.VARY;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.testutil.ConfigSuite;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class CorsIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config allowExampleDotCom() {
    Config cfg = new Config();
    cfg.setString("auth", null, "type", "DEVELOPMENT_BECOME_ANY_ACCOUNT");
    cfg.setStringList(
        "site",
        null,
        "allowOriginRegex",
        ImmutableList.of("https?://(.+[.])?example[.]com", "http://friend[.]ly"));
    return cfg;
  }

  @Test
  public void origin() throws Exception {
    Result change = createChange();

    String url = "/changes/" + change.getChangeId() + "/detail";
    RestResponse r = adminRestSession.get(url);
    r.assertOK();
    assertThat(r.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    assertThat(r.getHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();

    check(url, true, "http://example.com");
    check(url, true, "https://sub.example.com");
    check(url, true, "http://friend.ly");

    check(url, false, "http://evil.attacker");
    check(url, false, "http://friendsly");
  }

  @Test
  public void putWithOriginAccepted() throws Exception {
    Result change = createChange();
    String origin = "http://example.com";
    RestResponse r =
        adminRestSession.putWithHeader(
            "/changes/" + change.getChangeId() + "/topic", new BasicHeader(ORIGIN, origin), "A");
    r.assertOK();
    checkCors(r, true, origin);
  }

  @Test
  public void preflightOk() throws Exception {
    Result change = createChange();

    String origin = "http://example.com";
    Request req =
        Request.Options(adminRestSession.url() + "/a/changes/" + change.getChangeId() + "/detail");
    req.addHeader(ORIGIN, origin);
    req.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "GET");
    req.addHeader(ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With");

    RestResponse res = adminRestSession.execute(req);
    res.assertOK();

    String vary = res.getHeader(VARY);
    assertThat(vary).named(VARY).isNotNull();
    assertThat(Splitter.on(", ").splitToList(vary))
        .containsExactly(ORIGIN, ACCESS_CONTROL_REQUEST_METHOD, ACCESS_CONTROL_REQUEST_HEADERS);
    checkCors(res, true, origin);
  }

  @Test
  public void preflightBadOrigin() throws Exception {
    Result change = createChange();
    Request req =
        Request.Options(adminRestSession.url() + "/a/changes/" + change.getChangeId() + "/detail");
    req.addHeader(ORIGIN, "http://evil.attacker");
    req.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "GET");
    adminRestSession.execute(req).assertBadRequest();
  }

  @Test
  public void preflightBadMethod() throws Exception {
    Result change = createChange();
    Request req =
        Request.Options(adminRestSession.url() + "/a/changes/" + change.getChangeId() + "/detail");
    req.addHeader(ORIGIN, "http://example.com");
    req.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "CALL");
    adminRestSession.execute(req).assertBadRequest();
  }

  @Test
  public void preflightBadHeader() throws Exception {
    Result change = createChange();
    Request req =
        Request.Options(adminRestSession.url() + "/a/changes/" + change.getChangeId() + "/detail");
    req.addHeader(ORIGIN, "http://example.com");
    req.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "GET");
    req.addHeader(ACCESS_CONTROL_REQUEST_HEADERS, "X-Secret-Auth-Token");
    adminRestSession.execute(req).assertBadRequest();
  }

  @Test
  public void xdPutTopic() throws Exception {
    Result change = createChange();
    BasicCookieStore cookies = new BasicCookieStore();
    Executor http = Executor.newInstance().cookieStore(cookies);

    Request req = Request.Get(canonicalWebUrl.get() + "/login/?account_id=" + admin.id.get());
    HttpResponse r = http.execute(req).returnResponse();
    String auth = null;
    for (Cookie c : cookies.getCookies()) {
      if ("GerritAccount".equals(c.getName())) {
        auth = c.getValue();
      }
    }
    assertThat(auth).named("GerritAccount cookie").isNotNull();
    cookies.clear();

    UrlEncoded url =
        new UrlEncoded(canonicalWebUrl.get() + "/changes/" + change.getChangeId() + "/topic");
    url.put("$m", "PUT");
    url.put("$ct", "application/json; charset=US-ASCII");
    url.put("access_token", auth);

    String origin = "http://example.com";
    req = Request.Post(url.toString());
    req.setHeader(CONTENT_TYPE, "text/plain");
    req.setHeader(ORIGIN, origin);
    req.bodyByteArray("{\"topic\":\"test-xd\"}".getBytes(StandardCharsets.US_ASCII));

    r = http.execute(req).returnResponse();
    assertThat(r.getStatusLine().getStatusCode()).isEqualTo(200);

    Header vary = r.getFirstHeader(VARY);
    assertThat(vary).named(VARY).isNotNull();
    assertThat(Splitter.on(", ").splitToList(vary.getValue())).named(VARY).contains(ORIGIN);

    Header allowOrigin = r.getFirstHeader(ACCESS_CONTROL_ALLOW_ORIGIN);
    assertThat(allowOrigin).named(ACCESS_CONTROL_ALLOW_ORIGIN).isNotNull();
    assertThat(allowOrigin.getValue()).named(ACCESS_CONTROL_ALLOW_ORIGIN).isEqualTo(origin);

    ChangeInfo info = gApi.changes().id(change.getChangeId()).get();
    assertThat(info.topic).named("toppic").isEqualTo("test-xd");
  }

  private RestResponse check(String url, boolean accept, String origin) throws Exception {
    Header hdr = new BasicHeader(ORIGIN, origin);
    RestResponse r = adminRestSession.getWithHeader(url, hdr);
    if (accept) {
      r.assertOK();
    } else {
      r.assertBadRequest();
    }
    checkCors(r, accept, origin);
    return r;
  }

  private void checkCors(RestResponse r, boolean accept, String origin) {
    String vary = r.getHeader(VARY);
    assertThat(vary).named(VARY).isNotNull();
    assertThat(Splitter.on(", ").splitToList(vary)).named(VARY).contains(ORIGIN);

    String allowOrigin = r.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN);
    String allowCred = r.getHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS);
    String maxAge = r.getHeader(ACCESS_CONTROL_MAX_AGE);
    String allowMethods = r.getHeader(ACCESS_CONTROL_ALLOW_METHODS);
    String allowHeaders = r.getHeader(ACCESS_CONTROL_ALLOW_HEADERS);
    if (accept) {
      assertThat(allowOrigin).named(ACCESS_CONTROL_ALLOW_ORIGIN).isEqualTo(origin);
      assertThat(allowCred).named(ACCESS_CONTROL_ALLOW_CREDENTIALS).isEqualTo("true");
      assertThat(maxAge).named(ACCESS_CONTROL_MAX_AGE).isEqualTo("600");

      assertThat(allowMethods).named(ACCESS_CONTROL_ALLOW_METHODS).isNotNull();
      assertThat(Splitter.on(", ").splitToList(allowMethods))
          .named(ACCESS_CONTROL_ALLOW_METHODS)
          .containsExactly("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS");

      assertThat(allowHeaders).named(ACCESS_CONTROL_ALLOW_HEADERS).isNotNull();
      assertThat(Splitter.on(", ").splitToList(allowHeaders))
          .named(ACCESS_CONTROL_ALLOW_HEADERS)
          .containsExactlyElementsIn(
              Stream.of(AUTHORIZATION, CONTENT_TYPE, "X-Gerrit-Auth", "X-Requested-With")
                  .map(s -> s.toLowerCase(Locale.US))
                  .collect(ImmutableSet.toImmutableSet()));
    } else {
      assertThat(allowOrigin).named(ACCESS_CONTROL_ALLOW_ORIGIN).isNull();
      assertThat(allowCred).named(ACCESS_CONTROL_ALLOW_CREDENTIALS).isNull();
      assertThat(maxAge).named(ACCESS_CONTROL_MAX_AGE).isNull();
      assertThat(allowMethods).named(ACCESS_CONTROL_ALLOW_METHODS).isNull();
      assertThat(allowHeaders).named(ACCESS_CONTROL_ALLOW_HEADERS).isNull();
    }
  }
}
