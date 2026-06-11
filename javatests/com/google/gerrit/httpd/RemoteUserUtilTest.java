// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.httpd.RemoteUserUtil.extractUsername;
import static com.google.gerrit.httpd.RemoteUserUtil.getRemoteUser;

import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import org.junit.Test;

import java.util.Set;

public class RemoteUserUtilTest {
  private static final String CUSTOM_LOGIN_HEADER = "MY_HEADER";

  @Test
  public void testExtractUsername() {
    assertThat(extractUsername(null)).isNull();
    assertThat(extractUsername("")).isNull();
    assertThat(extractUsername("Basic dXNlcjpwYXNzd29yZA==")).isEqualTo("user");
    assertThat(extractUsername("Digest username=\"user\", realm=\"test\"")).isEqualTo("user");
  }

  @Test
  public void testExtractUserFromRequestAllowedByDefault()
    throws Exception {
    FakeHttpServletRequest fakeRequest = new FakeHttpServletRequest();
    String expectedUser = "user";
    fakeRequest.addHeader(CUSTOM_LOGIN_HEADER, expectedUser);
    assertThat(getRemoteUser(fakeRequest, CUSTOM_LOGIN_HEADER, Set.of())).isEqualTo(expectedUser);
  }

  @Test
  public void testExtractUserFromRequestAllowedWithExactIPv4Matching()
          throws Exception {
    FakeHttpServletRequest fakeRequest = new FakeHttpServletRequest();
    String expectedUser = "user";
    String remoteIp = fakeRequest.getRemoteAddr();
    fakeRequest.addHeader(CUSTOM_LOGIN_HEADER, expectedUser);
    assertThat(getRemoteUser(fakeRequest, CUSTOM_LOGIN_HEADER, Set.of(remoteIp + "/32"))).isEqualTo(expectedUser);
  }

  @Test
  public void testExtractUserFromRequestRejectedWithNonMatchingExactIPv4()
          throws Exception {
    FakeHttpServletRequest fakeRequest = new FakeHttpServletRequest();
    String expectedUser = "user";
    fakeRequest.addHeader(CUSTOM_LOGIN_HEADER, expectedUser);
    assertThat(getRemoteUser(fakeRequest, CUSTOM_LOGIN_HEADER, Set.of("255.255.255.255/32"))).isNull();
  }
}
