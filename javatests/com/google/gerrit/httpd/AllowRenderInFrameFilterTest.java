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
import static com.google.gerrit.httpd.AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gerrit.httpd.AllowRenderInFrameFilter.XFrameOption;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AllowRenderInFrameFilterTest {

  private Config cfg = new Config();
  @Mock ServletRequest request;
  @Mock HttpServletResponse response;
  @Mock FilterChain filterChain;

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalse()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", false);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1)).addHeader(X_FRAME_OPTIONS_HEADER_NAME, "DENY");
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalseAndXFormOptionIsSAMEORIGIN()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", false);
    cfg.setEnum("gerrit", null, "xframeOption", XFrameOption.SAMEORIGIN);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1)).addHeader(X_FRAME_OPTIONS_HEADER_NAME, "DENY");
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalseAndXFormOptionIsALLOW()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", false);
    cfg.setEnum("gerrit", null, "xframeOption", XFrameOption.ALLOW);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1)).addHeader(X_FRAME_OPTIONS_HEADER_NAME, "DENY");
  }

  @Test
  public void shouldRestrictAccessToSAMEORIGINWhenCanRenderInFrameIsTrue()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1)).addHeader(X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
  }

  @Test
  public void shouldSkipHeaderWhenCanRenderInFrameIsTrueAndXFormOptionIsALLOW()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setEnum("gerrit", null, "xframeOption", XFrameOption.ALLOW);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, never()).addHeader(eq(X_FRAME_OPTIONS_HEADER_NAME), anyString());
  }

  @Test
  public void shouldRestrictAccessToSAMEORIGINWhenCanRenderInFrameIsTrueAndXFormOptionIsSAMEORIGIN()
      throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setEnum("gerrit", null, "xframeOption", XFrameOption.SAMEORIGIN);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1)).addHeader(X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
  }

  @Test
  public void shouldIgnoreXFrameOriginCaseSensitivity() throws IOException, ServletException {
    cfg.setBoolean("gerrit", null, "canLoadInIFrame", true);
    cfg.setString("gerrit", null, "xframeOption", "sameOrigin");

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1)).addHeader(X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
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
