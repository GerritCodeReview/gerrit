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
import static com.google.gerrit.httpd.raw.IndexHtmlUtil.staticTemplateData;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.UnsafeSanitizedContentOrdainer;
import java.util.HashMap;
import org.junit.Test;

public class IndexHtmlUtilTest {

  @Test
  public void noPathAndNoCDN() throws Exception {
    assertThat(
        staticTemplateData(
            "http://example.com/", null, null, new HashMap<>(), IndexHtmlUtilTest::ordain))
        .containsExactly("canonicalPath", "", "polymer2", "true", "staticResourcePath", ordain(""));
  }

  @Test
  public void pathAndNoCDN() throws Exception {
    assertThat(
        staticTemplateData(
            "http://example.com/gerrit/",
            null,
            null,
            new HashMap<>(),
            IndexHtmlUtilTest::ordain))
        .containsExactly("canonicalPath", "/gerrit", "polymer2", "true", "staticResourcePath",
            ordain("/gerrit"));
  }

  @Test
  public void noPathAndCDN() throws Exception {
    assertThat(
        staticTemplateData(
            "http://example.com/",
            "http://my-cdn.com/foo/bar/",
            null,
            new HashMap<>(),
            IndexHtmlUtilTest::ordain))
        .containsExactly(
            "canonicalPath", "", "polymer2", "true", "staticResourcePath",
            ordain("http://my-cdn.com/foo/bar/"));
  }

  @Test
  public void pathAndCDN() throws Exception {
    assertThat(
        staticTemplateData(
            "http://example.com/gerrit",
            "http://my-cdn.com/foo/bar/",
            null,
            new HashMap<>(),
            IndexHtmlUtilTest::ordain))
        .containsExactly(
            "canonicalPath", "/gerrit", "polymer2", "true", "staticResourcePath",
            ordain("http://my-cdn.com/foo/bar/"));
  }

  private static SanitizedContent ordain(String s) {
    return UnsafeSanitizedContentOrdainer.ordainAsSafe(
        s, SanitizedContent.ContentKind.TRUSTED_RESOURCE_URI);
  }
}
