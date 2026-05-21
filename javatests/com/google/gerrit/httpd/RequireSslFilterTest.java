// Copyright (C) 2026 The Android Open Source Project
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RequireSslFilterTest {
  @Mock FilterChain filterChain;

  @Test
  public void shouldRedirectWithoutDoubleSlashWhenCanonicalUrlEndsWithSlash()
      throws IOException, ServletException {
    FakeHttpServletRequest request =
        new FakeHttpServletRequest("gerrit.example.com", 80, "", "/a/changes/12345");
    FakeHttpServletResponse response = new FakeHttpServletResponse();

    RequireSslFilter filter = new RequireSslFilter(() -> "https://gerrit.example.com/");

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_MOVED_PERMANENTLY);
    assertThat(response.getHeader("Location"))
        .isEqualTo("https://gerrit.example.com/a/changes/12345");
    verify(filterChain, never()).doFilter(request, response);
  }
}
