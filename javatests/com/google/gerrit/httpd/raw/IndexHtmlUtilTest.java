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

package com.google.gerrit.httpd.raw;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.dynamicTemplateData;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.experimentData;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.staticTemplateData;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.config.Server;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class IndexHtmlUtilTest {

  @Test
  public void noPathAndNoCDN() throws Exception {
    assertThat(
            staticTemplateData(
                "http://example.com/",
                null,
                null,
                new HashMap<>(),
                IndexHtmlUtilTest::ordain,
                null))
        .containsExactly("canonicalPath", "", "staticResourcePath", ordain(""));
  }

  @Test
  public void pathAndNoCDN() throws Exception {
    assertThat(
            staticTemplateData(
                "http://example.com/gerrit/",
                null,
                null,
                new HashMap<>(),
                IndexHtmlUtilTest::ordain,
                null))
        .containsExactly("canonicalPath", "/gerrit", "staticResourcePath", ordain("/gerrit"));
  }

  @Test
  public void noPathAndCDN() throws Exception {
    assertThat(
            staticTemplateData(
                "http://example.com/",
                "http://my-cdn.com/foo/bar/",
                null,
                new HashMap<>(),
                IndexHtmlUtilTest::ordain,
                null))
        .containsExactly(
            "canonicalPath", "", "staticResourcePath", ordain("http://my-cdn.com/foo/bar/"));
  }

  @Test
  public void pathAndCDN() throws Exception {
    assertThat(
            staticTemplateData(
                "http://example.com/gerrit",
                "http://my-cdn.com/foo/bar/",
                null,
                new HashMap<>(),
                IndexHtmlUtilTest::ordain,
                null))
        .containsExactly(
            "canonicalPath", "/gerrit", "staticResourcePath", ordain("http://my-cdn.com/foo/bar/"));
  }

  @Test
  public void useGoogleFonts() throws Exception {
    Map<String, String[]> urlParms = new HashMap<>();
    urlParms.put("gf", new String[0]);
    assertThat(
            staticTemplateData(
                "http://example.com/", null, null, urlParms, IndexHtmlUtilTest::ordain, null))
        .containsExactly(
            "canonicalPath", "", "staticResourcePath", ordain(""), "useGoogleFonts", "true");
  }

  @Test
  public void usePreloadRest() throws Exception {
    Accounts accountsApi = mock(Accounts.class);
    when(accountsApi.self()).thenThrow(new AuthException("user needs to be authenticated"));

    Server serverApi = mock(Server.class);
    when(serverApi.getVersion()).thenReturn("123");
    when(serverApi.topMenus()).thenReturn(ImmutableList.of());
    ServerInfo serverInfo = new ServerInfo();
    serverInfo.defaultTheme = "my-default-theme";
    when(serverApi.getInfo()).thenReturn(serverInfo);

    Config configApi = mock(Config.class);
    when(configApi.server()).thenReturn(serverApi);

    GerritApi gerritApi = mock(GerritApi.class);
    when(gerritApi.accounts()).thenReturn(accountsApi);
    when(gerritApi.config()).thenReturn(configApi);

    assertThat(dynamicTemplateData(gerritApi, "/c/project/+/123"))
        .containsAtLeast(
            "defaultChangeDetailHex", "916314",
            "changeRequestsPath", "changes/project~123");
  }

  private static SanitizedContent ordain(String s) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        s, SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI);
  }

  @Test
  public void useExperiments() throws Exception {
    Map<String, String[]> urlParms = new HashMap<>();
    String[] experiments = new String[] {"foo", "bar", "foo"};
    Set<String> expected = new HashSet<>();
    for (String exp : experiments) {
      expected.add(exp);
    }
    urlParms.put("experiment", experiments);
    Set<String> data = experimentData(urlParms);
    assertThat(data).isEqualTo(expected);
  }
}
