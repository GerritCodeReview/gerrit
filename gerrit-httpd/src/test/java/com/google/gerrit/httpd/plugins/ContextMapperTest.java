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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import javax.servlet.http.HttpServletRequest;

public class ContextMapperTest {

  private static final String CONTEXT = "/context";
  private static final String PLUGIN_NAME = "my-plugin";
  private static final String RESOURCE = "my-resource";

  @Test
  public void testUnauthorized() throws Exception {
    ContextMapper classUnderTest = new ContextMapper(CONTEXT);

    HttpServletRequest originalRequest =
        createMockRequest("/plugins/", PLUGIN_NAME + "/" + RESOURCE);

    HttpServletRequest result =
        classUnderTest.create(originalRequest, PLUGIN_NAME);

    assertEquals(CONTEXT + "/plugins/" + PLUGIN_NAME, result.getContextPath());
    assertEquals("", result.getServletPath());
    assertEquals("/" + RESOURCE, result.getPathInfo());
    assertEquals(CONTEXT + "/plugins/" + PLUGIN_NAME + "/" + RESOURCE,
        result.getRequestURI());
  }

  @Test
  public void testAuthorized() throws Exception {
    ContextMapper classUnderTest = new ContextMapper(CONTEXT);

    HttpServletRequest originalRequest =
        createMockRequest("/a/plugins/", PLUGIN_NAME + "/" + RESOURCE);

    HttpServletRequest result =
        classUnderTest.create(originalRequest, PLUGIN_NAME);

    assertEquals(CONTEXT + "/a/plugins/" + PLUGIN_NAME,
        result.getContextPath());
    assertEquals("", result.getServletPath());
    assertEquals("/" + RESOURCE, result.getPathInfo());
    assertEquals(CONTEXT + "/a/plugins/" + PLUGIN_NAME + "/" + RESOURCE,
        result.getRequestURI());
  }

  private static HttpServletRequest createMockRequest(String servletPath,
      String pathInfo) {
    HttpServletRequest req = createNiceMock(HttpServletRequest.class);
    expect(req.getContextPath()).andStubReturn(CONTEXT);
    expect(req.getServletPath()).andStubReturn(servletPath);
    expect(req.getPathInfo()).andStubReturn(pathInfo);
    String uri = CONTEXT + servletPath + pathInfo;
    expect(req.getRequestURI()).andStubReturn(uri);
    replay(req);

    return req;
  }
}
