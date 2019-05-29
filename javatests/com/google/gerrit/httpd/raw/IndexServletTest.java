// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import org.junit.Test;

public class IndexServletTest {

  @Test
  public void renderTemplate() throws Exception {
    Accounts accountsApi = createMock(Accounts.class);
    expect(accountsApi.self()).andThrow(new AuthException("user needs to be authenticated"));

    Server serverApi = createMock(Server.class);
    expect(serverApi.getVersion()).andReturn("123");
    expect(serverApi.topMenus()).andReturn(ImmutableList.of());
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.defaultTheme = "my-default-theme";
    expect(serverApi.getInfo()).andReturn(serverInfo);

    Config configApi = createMock(Config.class);
    expect(configApi.server()).andReturn(serverApi);

    GerritApi gerritApi = createMock(GerritApi.class);
    expect(gerritApi.accounts()).andReturn(accountsApi);
    expect(gerritApi.config()).andReturn(configApi);

    String testCanonicalUrl = "foo-url";
    String testCdnPath = "bar-cdn";
    String testFaviconURL = "zaz-url";
    IndexServlet servlet =
        new IndexServlet(testCanonicalUrl, testCdnPath, testFaviconURL, gerritApi);

    FakeHttpServletResponse response = new FakeHttpServletResponse();

    replay(gerritApi);
    replay(configApi);
    replay(serverApi);
    replay(accountsApi);

    servlet.doGet(new FakeHttpServletRequest(), response);

    verify(gerritApi);
    verify(configApi);
    verify(serverApi);
    verify(accountsApi);

    String output = response.getActualBodyString();
    assertThat(output).contains("<!DOCTYPE html>");
    assertThat(output).contains("window.CANONICAL_PATH = '" + testCanonicalUrl);
    assertThat(output).contains("<link rel=\"preload\" href=\"" + testCdnPath);
    assertThat(output)
        .contains(
            "<link rel=\"icon\" type=\"image/x-icon\" href=\""
                + testCanonicalUrl
                + "/"
                + testFaviconURL);
    assertThat(output)
        .contains(
            "window.INITIAL_DATA = JSON.parse("
                + "'\\x7b\\x22\\/config\\/server\\/version\\x22: \\x22123\\x22, "
                + "\\x22\\/config\\/server\\/info\\x22: \\x7b\\x22default_theme\\x22:"
                + "\\x22my-default-theme\\x22\\x7d, \\x22\\/config\\/server\\/top-menus\\x22: "
                + "\\x5b\\x5d\\x7d');</script>");
  }
}
