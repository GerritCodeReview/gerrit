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

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  @Mock Config cfg;
  @Mock ServletRequest request;
  @Mock HttpServletResponse response;
  @Mock FilterChain filterChain;

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalse()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(false);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "DENY");
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalseAndXFormOptionIsSAMEORIGIN()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(false);
    when(cfg.getString("gerrit", null, "xframeOption"))
        .thenReturn(AllowRenderInFrameFilter.X_FRAME_OPTIONS.SAMEORIGIN.name());

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "DENY");
  }

  @Test
  public void shouldDenyInFrameRenderingWhenCanRenderInFrameIsFalseAndXFormOptionIsALLOW()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(false);
    when(cfg.getString("gerrit", null, "xframeOption"))
        .thenReturn(AllowRenderInFrameFilter.X_FRAME_OPTIONS.ALLOW.name());

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "DENY");
  }

  @Test
  public void shouldRestrictAccessHeaderWhenCanRenderInFrameIsTrue()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(true);

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
  }

  @Test
  public void shouldSkipHeaderWhenCanRenderInFrameIsTrueAndXFormOptionIsALLOW()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(true);
    when(cfg.getString("gerrit", null, "xframeOption"))
        .thenReturn(AllowRenderInFrameFilter.X_FRAME_OPTIONS.ALLOW.name());

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, never())
        .addHeader(eq(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME), anyString());
  }

  @Test
  public void shouldRestrictAccessWhenCanRenderInFrameIsTrueAndXFormOptionIsSAMEORIGIN()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(true);
    when(cfg.getString("gerrit", null, "xframeOption"))
        .thenReturn(AllowRenderInFrameFilter.X_FRAME_OPTIONS.SAMEORIGIN.name());

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
  }

  @Test
  public void shouldIgnoreCaseSensivity() throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(true);
    when(cfg.getString("gerrit", null, "xframeOption")).thenReturn("sameOrigin");

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
  }

  @Test
  public void shouldRestrictAccessWhenUnknownXFormOptionValue()
      throws IOException, ServletException {
    when(cfg.getBoolean("gerrit", "canLoadInIFrame", false)).thenReturn(true);
    when(cfg.getString("gerrit", null, "xframeOption")).thenReturn("unsupported value");

    AllowRenderInFrameFilter objectUnderTest = new AllowRenderInFrameFilter(cfg);
    objectUnderTest.doFilter(request, response, filterChain);

    verify(response, times(1))
        .addHeader(AllowRenderInFrameFilter.X_FRAME_OPTIONS_HEADER_NAME, "SAMEORIGIN");
  }
}
