// Copyright (C) 2014 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.httpd.plugins;

package com.google.gerrit.httpd.plugins;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import javax.servlet.http.HttpServletRequest;
import org.junit.Test;

public class ContextMapperTest {

  private static final String CONTEXT = "/context";
  private static final String PLUGIN_NAME = "my-plugin";
  private static final String RESOURCE = "my-resource";

  @Test
  public void testUnauthorized() throws Exception {
    ContextMapper classUnderTest = new ContextMapper(CONTEXT);

    HttpServletRequest originalRequest =
        createFakeRequest("/plugins/", PLUGIN_NAME + "/" + RESOURCE);

    HttpServletRequest result = classUnderTest.create(originalRequest, PLUGIN_NAME);

    assertThat(result.getContextPath()).isEqualTo(CONTEXT + "/plugins/" + PLUGIN_NAME);
    assertThat(result.getServletPath()).isEqualTo("");
    assertThat(result.getPathInfo()).isEqualTo("/" + RESOURCE);
    assertThat(result.getRequestURI())
        .isEqualTo(CONTEXT + "/plugins/" + PLUGIN_NAME + "/" + RESOURCE);
  }

  @Test
  public void testAuthorized() throws Exception {
    ContextMapper classUnderTest = new ContextMapper(CONTEXT);

    HttpServletRequest originalRequest =
        createFakeRequest("/a/plugins/", PLUGIN_NAME + "/" + RESOURCE);

    HttpServletRequest result = classUnderTest.create(originalRequest, PLUGIN_NAME);

    assertThat(result.getContextPath()).isEqualTo(CONTEXT + "/a/plugins/" + PLUGIN_NAME);
    assertThat(result.getServletPath()).isEqualTo("");
    assertThat(result.getPathInfo()).isEqualTo("/" + RESOURCE);
    assertThat(result.getRequestURI())
        .isEqualTo(CONTEXT + "/a/plugins/" + PLUGIN_NAME + "/" + RESOURCE);
  }

  private static FakeHttpServletRequest createFakeRequest(String servletPath, String pathInfo) {
    return new FakeHttpServletRequest("gerrit.example.com", 80, CONTEXT, servletPath)
        .setPathInfo(pathInfo);
  }
}
