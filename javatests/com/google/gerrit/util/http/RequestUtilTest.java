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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.util.http.RequestUtil.getEncodedPathInfo;
import static com.google.gerrit.util.http.RequestUtil.getRestPathWithoutIds;

import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import org.junit.Test;

public class RequestUtilTest {
  @Test
  public void getEncodedPathInfo_emptyContextPath() {
    assertThat(getEncodedPathInfo(fakeRequest("", "/s", "/foo/bar"))).isEqualTo("/foo/bar");
    assertThat(getEncodedPathInfo(fakeRequest("", "/s", "/foo%2Fbar"))).isEqualTo("/foo%2Fbar");
  }

  @Test
  public void getEncodedPathInfo_emptyServletPath() {
    assertThat(getEncodedPathInfo(fakeRequest("", "/c", "/foo/bar"))).isEqualTo("/foo/bar");
    assertThat(getEncodedPathInfo(fakeRequest("", "/c", "/foo%2Fbar"))).isEqualTo("/foo%2Fbar");
  }

  @Test
  public void getEncodedPathInfo_trailingSlashes() {
    assertThat(getEncodedPathInfo(fakeRequest("/c", "/s", "/foo/bar/"))).isEqualTo("/foo/bar/");
    assertThat(getEncodedPathInfo(fakeRequest("/c", "/s", "/foo/bar///"))).isEqualTo("/foo/bar/");
    assertThat(getEncodedPathInfo(fakeRequest("/c", "/s", "/foo%2Fbar/"))).isEqualTo("/foo%2Fbar/");
    assertThat(getEncodedPathInfo(fakeRequest("/c", "/s", "/foo%2Fbar///")))
        .isEqualTo("/foo%2Fbar/");
  }

  @Test
  public void emptyPathInfo() {
    assertThat(getEncodedPathInfo(fakeRequest("/c", "/s", ""))).isNull();
  }

  @Test
  public void getRestPathWithoutIds_emptyContextPath() {
    assertThat(getRestPathWithoutIds(fakeRequest("", "/a/accounts", "/123/test")))
        .isEqualTo("/accounts/test");
    assertThat(getRestPathWithoutIds(fakeRequest("", "/accounts", "/123/test")))
        .isEqualTo("/accounts/test");
  }

  @Test
  public void getRestPathWithoutIds_nonEmptyContextPath() {
    assertThat(getRestPathWithoutIds(fakeRequest("/c", "/a/accounts", "/123/test")))
        .isEqualTo("/accounts/test");
    assertThat(getRestPathWithoutIds(fakeRequest("/c", "/accounts", "/123/test")))
        .isEqualTo("/accounts/test");
  }

  private FakeHttpServletRequest fakeRequest(
      String contextPath, String servletPath, String pathInfo) {
    FakeHttpServletRequest req =
        new FakeHttpServletRequest("gerrit.example.com", 80, contextPath, servletPath);
    return req.setPathInfo(pathInfo);
  }
}
