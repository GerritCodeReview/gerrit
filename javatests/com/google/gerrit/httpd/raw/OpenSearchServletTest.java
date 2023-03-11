// Copyright (C) 2018 The Android Open Source Project
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

import com.google.template.soy.data.SoyMapData;
import org.junit.Test;

public class OpenSearchServletTest {
  static class TestOpenSearchServlet extends OpenSearchServlet {
    private static final long serialVersionUID = 1L;

    TestOpenSearchServlet(String canonicalURL, String instanceName) {
      super(canonicalURL, instanceName);
    }

    String getIndexSource() {
      return new String(indexSource, UTF_8);
    }
  }

  @Test
  public void noPathAndNoInstanceName() throws Exception {
    SoyMapData data = OpenSearchServlet.getTemplateData("http://example.com", null);
    assertThat(data.getSingle("canonicalUrl").stringValue()).isEqualTo("http://example.com");
    assertThat(data.getSingle("instanceName").stringValue()).isEqualTo("Gerrit");
  }

  @Test
  public void pathAndNoInstanceName() throws Exception {
    SoyMapData data = OpenSearchServlet.getTemplateData("http://example.com/gerrit/", null);
    assertThat(data.getSingle("canonicalUrl").stringValue()).isEqualTo("http://example.com/gerrit");
    assertThat(data.getSingle("instanceName").stringValue()).isEqualTo("Gerrit");
  }

  @Test
  public void noPathAndInstanceName() throws Exception {
    SoyMapData data = OpenSearchServlet.getTemplateData("http://example.com/", "InstanceName");
    assertThat(data.getSingle("canonicalUrl").stringValue()).isEqualTo("http://example.com");
    assertThat(data.getSingle("instanceName").stringValue()).isEqualTo("InstanceName");
  }

  @Test
  public void pathAndInstanceName() throws Exception {
    SoyMapData data =
        OpenSearchServlet.getTemplateData("http://example.com/gerrit", "InstanceName");
    assertThat(data.getSingle("canonicalUrl").stringValue()).isEqualTo("http://example.com/gerrit");
    assertThat(data.getSingle("instanceName").stringValue()).isEqualTo("InstanceName");
  }

  @Test
  public void renderTemplate() throws Exception {
    String testCanonicalUrl = "foo-url/";
    String testInstanceName = "InstanceName";
    TestOpenSearchServlet servlet = new TestOpenSearchServlet(testCanonicalUrl, testInstanceName);
    String output = servlet.getIndexSource();
    assertThat(output).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    assertThat(output).contains("template=\"foo-url/q/{searchTerms}\"");
    assertThat(output).contains("<ShortName>" + testInstanceName + "</ShortName>");
    assertThat(output).contains("<Description>" + testInstanceName + " search</Description>");
  }
}
