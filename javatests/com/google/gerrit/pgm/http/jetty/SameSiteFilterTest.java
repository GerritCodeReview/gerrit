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

package com.google.gerrit.pgm.http.jetty;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class SameSiteFilterTest {

  @Test
  public void laxSetsSameSiteAttribute() throws Exception {
    assertThat(runFilterAndCapture("lax").getAttribute("SameSite")).isEqualTo("Lax");
  }

  @Test
  public void strictSetsSameSiteAttribute() throws Exception {
    assertThat(runFilterAndCapture("strict").getAttribute("SameSite")).isEqualTo("Strict");
  }

  @Test
  public void noneSetsSameSiteAttribute() throws Exception {
    assertThat(runFilterAndCapture("none").getAttribute("SameSite")).isEqualTo("None");
  }

  @Test
  public void valueIsCaseInsensitive() throws Exception {
    assertThat(runFilterAndCapture("LAX").getAttribute("SameSite")).isEqualTo("Lax");
  }

  @Test
  public void unsetConfigDoesNotMutateCookie() throws Exception {
    assertThat(runFilterAndCapture(null).getAttribute("SameSite")).isNull();
  }

  @Test
  public void invalidValueThrows() {
    assertThrows(ServletException.class, () -> runFilterAndCapture("bogus"));
  }

  /**
   * Runs the filter with the given httpd.sameSite config, simulates a downstream that adds a cookie
   * named "session", and returns the cookie as it reached the underlying HttpServletResponse.
   */
  private static Cookie runFilterAndCapture(String sameSiteConfig) throws Exception {
    Config cfg = new Config();
    if (sameSiteConfig != null) {
      cfg.setString("httpd", null, "sameSite", sameSiteConfig);
    }
    SameSiteFilter filter = new SameSiteFilter(cfg);

    Cookie cookie = new Cookie("session", "abc");
    HttpServletResponse rsp = mock(HttpServletResponse.class);
    Cookie[] captured = new Cookie[1];
    HttpServletResponseWrapper capturingRsp =
        new HttpServletResponseWrapper(rsp) {
          @Override
          public void addCookie(Cookie c) {
            captured[0] = c;
          }
        };

    // The downstream of the chain calls response.addCookie(cookie) on the wrapper the filter
    // installed; that wrapper mutates the cookie (setAttribute) and forwards to capturingRsp.
    filter.doFilter(
        mock(jakarta.servlet.ServletRequest.class),
        capturingRsp,
        (req, downstream) -> ((HttpServletResponse) downstream).addCookie(cookie));

    return captured[0] != null ? captured[0] : cookie;
  }
}
