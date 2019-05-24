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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import java.util.Map;
import org.junit.Test;

public class IndexServletTest {
  static class TestIndexServlet extends IndexServlet {
    private static final long serialVersionUID = 1L;

    TestIndexServlet(String canonicalURL, String cdnPath, String faviconPath) {
      super(canonicalURL, cdnPath, faviconPath, null);
    }

    @Override
    Map<String, SanitizedContent> getInitialData() {
      // Don't render any initial data. This would require a mock of the Gerrit API which is
      // cumbersome to keep in sync. Alternatively one could make this an integration test, but
      // this would unnecessarily slow it down.
      return ImmutableMap.of();
    }
  }

  @Test
  public void noPathAndNoCDN() throws Exception {
    SoyMapData data = IndexServlet.getStaticTemplateData("http://example.com/", null, null);
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("");
    assertThat(data.getSingle("staticResourcePath").stringValue()).isEqualTo("");
  }

  @Test
  public void pathAndNoCDN() throws Exception {
    SoyMapData data = IndexServlet.getStaticTemplateData("http://example.com/gerrit/", null, null);
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("/gerrit");
    assertThat(data.getSingle("staticResourcePath").stringValue()).isEqualTo("/gerrit");
  }

  @Test
  public void noPathAndCDN() throws Exception {
    SoyMapData data =
        IndexServlet.getStaticTemplateData(
            "http://example.com/", "http://my-cdn.com/foo/bar/", null);
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("");
    assertThat(data.getSingle("staticResourcePath").stringValue())
        .isEqualTo("http://my-cdn.com/foo/bar/");
  }

  @Test
  public void pathAndCDN() throws Exception {
    SoyMapData data =
        IndexServlet.getStaticTemplateData(
            "http://example.com/gerrit", "http://my-cdn.com/foo/bar/", null);
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("/gerrit");
    assertThat(data.getSingle("staticResourcePath").stringValue())
        .isEqualTo("http://my-cdn.com/foo/bar/");
  }

  @Test
  public void renderTemplate() throws Exception {
    String testCanonicalUrl = "foo-url";
    String testCdnPath = "bar-cdn";
    String testFaviconURL = "zaz-url";
    TestIndexServlet servlet = new TestIndexServlet(testCanonicalUrl, testCdnPath, testFaviconURL);

    FakeHttpServletResponse response = new FakeHttpServletResponse();
    servlet.doGet(new FakeHttpServletRequest(), response);
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
  }
}
