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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.testing.GerritBaseTests;
import java.net.URISyntaxException;
import java.util.Map;
import org.junit.Test;

public class IndexServletTest extends GerritBaseTests {
  static class TestIndexServlet extends IndexServlet {
    private static final long serialVersionUID = 1L;

    TestIndexServlet(String canonicalURL, String cdnPath, String faviconPath)
        throws URISyntaxException {
      super(canonicalURL, cdnPath, faviconPath);
    }

    String getIndexSource() {
      return new String(indexSource, UTF_8);
    }
  }

  @Test
  public void noPathAndNoCDN() throws URISyntaxException {
    Map<String, Object> data = IndexServlet.getTemplateData("http://example.com/", null, null);
    assertThat(data.get("canonicalPath")).isEqualTo("");
    assertThat(data.get("staticResourcePath").toString()).isEqualTo("");
  }

  @Test
  public void pathAndNoCDN() throws URISyntaxException {
    Map<String, Object> data =
        IndexServlet.getTemplateData("http://example.com/gerrit/", null, null);
    assertThat(data.get("canonicalPath")).isEqualTo("/gerrit");
    assertThat(data.get("staticResourcePath").toString()).isEqualTo("/gerrit");
  }

  @Test
  public void noPathAndCDN() throws URISyntaxException {
    Map<String, Object> data =
        IndexServlet.getTemplateData("http://example.com/", "http://my-cdn.com/foo/bar/", null);
    assertThat(data.get("canonicalPath")).isEqualTo("");
    assertThat(data.get("staticResourcePath").toString()).isEqualTo("http://my-cdn.com/foo/bar/");
  }

  @Test
  public void pathAndCDN() throws URISyntaxException {
    Map<String, Object> data =
        IndexServlet.getTemplateData(
            "http://example.com/gerrit", "http://my-cdn.com/foo/bar/", null);
    assertThat(data.get("canonicalPath")).isEqualTo("/gerrit");
    assertThat(data.get("staticResourcePath").toString()).isEqualTo("http://my-cdn.com/foo/bar/");
  }

  @Test
  public void renderTemplate() throws URISyntaxException {
    String testCanonicalUrl = "foo-url";
    String testCdnPath = "bar-cdn";
    String testFaviconURL = "zaz-url";
    TestIndexServlet servlet = new TestIndexServlet(testCanonicalUrl, testCdnPath, testFaviconURL);
    String output = servlet.getIndexSource();
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
