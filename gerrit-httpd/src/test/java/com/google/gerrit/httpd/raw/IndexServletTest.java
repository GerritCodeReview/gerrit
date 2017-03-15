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

import com.google.gerrit.common.Nullable;
import com.google.template.soy.data.SoyMapData;
import javax.servlet.ServletException;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class IndexServletTest {
  private static class Servlet extends IndexServlet {
    Servlet(final String canonicalURL, @Nullable String cdnPath)
        throws ServletException {
      super(canonicalURL, cdnPath);
    }
  }

  @Test
  public void noPathAndNoCDN() throws ServletException {
    Servlet servlet = new Servlet("http://example.com/", null);
    SoyMapData data = servlet.getTemplateData();
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("");
    assertThat(data.getSingle("staticResourcePath").stringValue()).isEqualTo("");
  }

  @Test
  public void pathAndNoCDN() throws ServletException {
    Servlet servlet = new Servlet("http://example.com/gerrit/", null);
    SoyMapData data = servlet.getTemplateData();
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("/gerrit");
    assertThat(data.getSingle("staticResourcePath").stringValue()).isEqualTo("/gerrit");
  }

  @Test
  public void noPathAndCDN() throws ServletException {
    Servlet servlet = new Servlet("http://example.com/", "http://my-cdn.com/foo/bar/");
    SoyMapData data = servlet.getTemplateData();
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("");
    assertThat(data.getSingle("staticResourcePath").stringValue())
        .isEqualTo("http://my-cdn.com/foo/bar/");
  }

  @Test
  public void pathAndCDN() throws ServletException {
    Servlet servlet = new Servlet("http://example.com/gerrit", "http://my-cdn.com/foo/bar/");
    SoyMapData data = servlet.getTemplateData();
    assertThat(data.getSingle("canonicalPath").stringValue()).isEqualTo("/gerrit");
    assertThat(data.getSingle("staticResourcePath").stringValue())
        .isEqualTo("http://my-cdn.com/foo/bar/");
  }
}
