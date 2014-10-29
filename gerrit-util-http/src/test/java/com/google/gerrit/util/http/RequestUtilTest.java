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
// limitations under the License.

package com.google.gerrit.util.http;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

public class RequestUtilTest {
  private List<Object> mocks;

  @Before
  public void setUp() {
    mocks = Collections.synchronizedList(new ArrayList<>());
  }

  @After
  public void tearDown() {
    for (Object mock : mocks) {
      verify(mock);
    }
  }

  @Test
  public void emptyContextPath() {
    assertEquals("/foo/bar", RequestUtil.getEncodedPathInfo(
        mockRequest("/s/foo/bar", "", "/s")));
    assertEquals("/foo%2Fbar", RequestUtil.getEncodedPathInfo(
        mockRequest("/s/foo%2Fbar", "", "/s")));
  }

  @Test
  public void emptyServletPath() {
    assertEquals("/foo/bar", RequestUtil.getEncodedPathInfo(
        mockRequest("/c/foo/bar", "/c", "")));
    assertEquals("/foo%2Fbar", RequestUtil.getEncodedPathInfo(
        mockRequest("/c/foo%2Fbar", "/c", "")));
  }

  @Test
  public void trailingSlashes() {
    assertEquals("/foo/bar/", RequestUtil.getEncodedPathInfo(
        mockRequest("/c/s/foo/bar/", "/c", "/s")));
    assertEquals("/foo/bar/", RequestUtil.getEncodedPathInfo(
        mockRequest("/c/s/foo/bar///", "/c", "/s")));
    assertEquals("/foo%2Fbar/", RequestUtil.getEncodedPathInfo(
        mockRequest("/c/s/foo%2Fbar/", "/c", "/s")));
    assertEquals("/foo%2Fbar/", RequestUtil.getEncodedPathInfo(
        mockRequest("/c/s/foo%2Fbar///", "/c", "/s")));
  }

  @Test
  public void servletPathMatchesRequestPath() {
    assertEquals(null, RequestUtil.getEncodedPathInfo(
        mockRequest("/c/s", "/c", "/s")));
  }

  private HttpServletRequest mockRequest(String uri, String contextPath, String servletPath) {
    HttpServletRequest req = createMock(HttpServletRequest.class);
    expect(req.getRequestURI()).andStubReturn(uri);
    expect(req.getContextPath()).andStubReturn(contextPath);
    expect(req.getServletPath()).andStubReturn(servletPath);
    replay(req);
    mocks.add(req);
    return req;
  }
}
