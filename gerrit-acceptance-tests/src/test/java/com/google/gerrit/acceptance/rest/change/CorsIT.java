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
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static com.google.common.net.HttpHeaders.ORIGIN;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.testutil.ConfigSuite;
import org.apache.http.Header;
import org.apache.http.client.fluent.Request;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class CorsIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config allowExampleDotCom() {
    Config cfg = new Config();
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
  public void putWithOriginRefused() throws Exception {
    Result change = createChange();
    String origin = "http://example.com";
    RestResponse r =
        adminRestSession.putWithHeader(
            "/changes/" + change.getChangeId() + "/topic", new BasicHeader(ORIGIN, origin), "A");
    r.assertOK();
    checkCors(r, false, origin);
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

    for (String method : new String[] {"POST", "PUT", "DELETE", "PATCH"}) {
      Request req =
          Request.Options(
              adminRestSession.url() + "/a/changes/" + change.getChangeId() + "/detail");
      req.addHeader(ORIGIN, "http://example.com");
      req.addHeader(ACCESS_CONTROL_REQUEST_METHOD, method);
      adminRestSession.execute(req).assertBadRequest();
    }
  }

  @Test
  public void preflightBadHeader() throws Exception {
    Result change = createChange();

    Request req =
        Request.Options(adminRestSession.url() + "/a/changes/" + change.getChangeId() + "/detail");
    req.addHeader(ORIGIN, "http://example.com");
    req.addHeader(ACCESS_CONTROL_REQUEST_METHOD, "GET");
    req.addHeader(ACCESS_CONTROL_REQUEST_HEADERS, "X-Gerrit-Auth");

    adminRestSession.execute(req).assertBadRequest();
  }

  private RestResponse check(String url, boolean accept, String origin) throws Exception {
    Header hdr = new BasicHeader(ORIGIN, origin);
    RestResponse r = adminRestSession.getWithHeader(url, hdr);
    r.assertOK();
    checkCors(r, accept, origin);
    return r;
  }

  private void checkCors(RestResponse r, boolean accept, String origin) {
    String allowOrigin = r.getHeader(ACCESS_CONTROL_ALLOW_ORIGIN);
    String allowCred = r.getHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS);
    String allowMethods = r.getHeader(ACCESS_CONTROL_ALLOW_METHODS);
    String allowHeaders = r.getHeader(ACCESS_CONTROL_ALLOW_HEADERS);
    if (accept) {
      assertThat(allowOrigin).isEqualTo(origin);
      assertThat(allowCred).isEqualTo("true");
      assertThat(allowMethods).isEqualTo("GET, OPTIONS");
      assertThat(allowHeaders).isEqualTo("X-Requested-With");
    } else {
      assertThat(allowOrigin).isNull();
      assertThat(allowCred).isNull();
      assertThat(allowMethods).isNull();
      assertThat(allowHeaders).isNull();
    }
  }
}
