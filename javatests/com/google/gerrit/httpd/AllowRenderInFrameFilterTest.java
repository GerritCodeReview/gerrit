// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.easymock.EasyMock.expectLastCall;

import com.google.gerrit.httpd.AllowRenderInFrameFilter.XFrameOption;
import com.google.gerrit.testing.GerritBaseTests;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.easymock.EasyMockSupport;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class AllowRenderInFrameFilterTest extends GerritBaseTests {

  Config cfg;
  ServletRequest request;
  HttpServletResponse response;
  FilterChain filterChain;

  EasyMockSupport ems = new EasyMockSupport();

  @Before
  public void setup() throws IOException, ServletException {
    cfg = new Config();
    request = ems.createMock(ServletRequest.class);
    response = ems.createMock(HttpServletResponse.class);
    filterChain = ems.createMock(FilterChain.class);
    ems.resetAll();
    // we want to make sure that doFilter is always called
    filterChain.doFilter(request, response);
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalse()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", false);
    cfg.setEnum("gerrit", null, "xframeOption", XFrameOption.SAMEORIGIN);

    response.addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "DENY");
    expectLastCall().times(1);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalseAndXFormOptionIsSAMEORIGIN()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", false);
    cfg.setEnum("gerrit", null, "xframeOption", AllowRenderInFrameFilter.XFrameOption.SAMEORIGIN);

    response.addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "DENY");
    expectLastCall().times(1);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalseAndXFormOptionIsALLOW()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", false);
    cfg.setEnum("gerrit", null, "xframeOption", AllowRenderInFrameFilter.XFrameOption.ALLOW);

    response.addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "DENY");
    expectLastCall().times(1);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldRestrictAccessHeaderWhenCanRenderInFrameIsTrue()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);

    response.addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
    expectLastCall().times(1);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldSkipHeaderWhenCanRenderInFrameIsTrueAndXFormOptionIsALLOW()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setEnum("gerrit", null, "xframeOption", AllowRenderInFrameFilter.XFrameOption.ALLOW);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldRestrictAccessWhenCanRenderInFrameIsTrueAndXFormOptionIsSAMEORIGIN()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setEnum("gerrit", null, "xframeOption", AllowRenderInFrameFilter.XFrameOption.SAMEORIGIN);

    response.addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
    expectLastCall().times(1);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldIgnoreCaseSensivity() throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setString("gerrit", null, "xframeOption", "sameOrigin");

    response.addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
    expectLastCall().times(1);
    ems.replayAll();

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    ems.verifyAll();
  }

  @Test
  public void shouldThrowExceptionWhenUnknownXFormOptionValue() {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setString("gerrit", null, "xframeOption", "unsupported value");

    IllegalArgumentException e =
        assertThrows(IllegalArgumentException.class, () -> new AllowRenderInFrameFilter(cfg));
    assertThat(e).hasMessageThat().contains("gerrit.xframeOption=unsupported value");
  }
}
