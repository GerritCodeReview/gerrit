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

import com.google.template.soy.data.SanitizedContent;
import org.eclipse.jgit.lib.Config;

import org.junit.Test;

public class IndexServletTest {

  class TestIndexServlet extends IndexServlet {
    private static final long serialVersionUID = 1L;

    TestIndexServlet(String canonicalURL, SanitizedContent staticPath) {
      super(canonicalURL, staticPath);
    }

    String getIndexSource() {
      return new String(indexSource);
    }
  }

  @Test
  public void renderTemplate() {
    String testStaticPath = "http://my-cdn.com/foo/bar/";
    String testCanonicalUrl = "foo-url";
    Config cfg = new Config();
    cfg.setString("gerrit", null, "cdnPath", testStaticPath);
    TestIndexServlet servlet = new TestIndexServlet(testCanonicalUrl,
        StaticPathOrdainer.ordainStaticPath("foo", cfg));
    String output = servlet.getIndexSource();
    assertThat(output).contains("<!DOCTYPE html>");
    assertThat(output).contains("window.CANONICAL_PATH = '" + testCanonicalUrl);
    assertThat(output).contains("<link rel=\"preload\" href=\"" + testStaticPath);
  }
}
