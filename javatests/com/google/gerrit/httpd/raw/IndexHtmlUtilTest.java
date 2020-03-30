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
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.changeUrlPattern;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.computeChangeRequestsPath;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.diffUrlPattern;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.experimentData;
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.staticTemplateData;

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
    Map<String, String[]> urlParms = new HashMap<>();
    urlParms.put("pl", new String[0]);
    assertThat(
            staticTemplateData(
                "http://example.com/",
                null,
                null,
                urlParms,
                IndexHtmlUtilTest::ordain,
                "/c/project/+/123"))
        .containsExactly(
            "canonicalPath", "",
            "staticResourcePath", ordain(""),
            "defaultChangeDetailHex", "916314",
            "defaultDiffDetailHex", "800014",
            "preloadChangePage", "true",
            "changeRequestsPath", "changes/project~123");
  }

  @Test
  public void computeChangePath() throws Exception {
    assertThat(computeChangeRequestsPath("/c/project/+/123", changeUrlPattern))
        .isEqualTo("changes/project~123");

    assertThat(computeChangeRequestsPath("/c/project/+/124/2", changeUrlPattern))
        .isEqualTo("changes/project~124");

    assertThat(computeChangeRequestsPath("/c/project/src/+/23", changeUrlPattern))
        .isEqualTo("changes/project%2Fsrc~23");

    assertThat(computeChangeRequestsPath("/q/project/src/+/23", changeUrlPattern)).isEqualTo(null);

    assertThat(computeChangeRequestsPath("/c/Scripts/+/232/1//COMMIT_MSG", changeUrlPattern))
        .isEqualTo(null);
    assertThat(computeChangeRequestsPath("/c/Scripts/+/232/1//COMMIT_MSG", diffUrlPattern))
        .isEqualTo("changes/Scripts~232");
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
