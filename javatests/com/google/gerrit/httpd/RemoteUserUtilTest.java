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
import static com.google.gerrit.httpd.RemoteUserUtil.PROXY_REMOTE_ADDRESS_ATTR;
import static com.google.gerrit.httpd.RemoteUserUtil.extractUsername;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.base.Suppliers;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.util.http.testutil.FakeHttpServletRequest;
import com.google.inject.ProvisionException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RemoteUserUtilTest {
  private static final String CUSTOM_LOGIN_HEADER = "MY_HEADER";
  private static final String EXPECTED_USER = "user";
  private static final String BASIC_AUTHENTICATION_USER_HEADER =
      "Basic "
          + Base64.getEncoder()
              .encodeToString((EXPECTED_USER + ":pass").getBytes(StandardCharsets.UTF_8));

  private Supplier<RemoteUserUtil> remoteUserUtil;

  @Mock AuthConfig authConfigMock;

  @Before
  public void setup() {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of());
    remoteUserUtil = Suppliers.memoize(() -> new RemoteUserUtil(authConfigMock));
  }

  @Test
  public void testExtractUsername() {
    assertThat(extractUsername(null)).isNull();
    assertThat(extractUsername("")).isNull();
    assertThat(extractUsername("Basic dXNlcjpwYXNzd29yZA==")).isEqualTo("user");
    assertThat(extractUsername("Digest username=\"user\", realm=\"test\"")).isEqualTo("user");
  }

  @Test
  public void testExtractUserFromRequestWithCustomHeaderAllowedByDefault() throws Exception {
    FakeHttpServletRequest fakeRequest = new FakeHttpServletRequest();
    fakeRequest.addHeader(CUSTOM_LOGIN_HEADER, EXPECTED_USER);
    assertThat(remoteUserUtil.get().getRemoteUser(fakeRequest, CUSTOM_LOGIN_HEADER))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void testExtractUserFromRequestWithAuthenticationHeaderAllowedByDefault()
      throws Exception {
    FakeHttpServletRequest fakeRequest = new FakeHttpServletRequest();
    fakeRequest.addHeader(HttpHeaders.AUTHORIZATION, BASIC_AUTHENTICATION_USER_HEADER);
    assertThat(remoteUserUtil.get().getRemoteUser(fakeRequest, HttpHeaders.AUTHORIZATION))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void testExtractUserFromRequestWithCustomHeaderAllowedUsingProxyExactIPv4Matching()
      throws Exception {
    String clientIP = "192.168.1.2";
    String proxyId = "80.78.1.3";
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of(proxyId + "/32"));
    FakeHttpServletRequest fakeRequest = newFakeHttpRequest(clientIP, EXPECTED_USER);
    fakeRequest.setAttribute(PROXY_REMOTE_ADDRESS_ATTR, proxyId);
    assertThat(remoteUserUtil.get().getRemoteUser(fakeRequest, CUSTOM_LOGIN_HEADER))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void testExtractUserFromRequestWithCustomHeaderAllowedWithExactIPv4Matching()
      throws Exception {
    String remoteIp = "192.168.1.2";
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of(remoteIp + "/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(newFakeHttpRequest(remoteIp, EXPECTED_USER), CUSTOM_LOGIN_HEADER))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void testExtractUserFromRequestWithAuthenticationHeaderAllowedWithExactIPv4Matching()
      throws Exception {
    String remoteIp = "192.168.1.2";
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of(remoteIp + "/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(
                    newFakeAuthHttpRequest(remoteIp, BASIC_AUTHENTICATION_USER_HEADER),
                    HttpHeaders.AUTHORIZATION))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void testExtractUserFromRequestWithCustomHeaderAllowedWithExactIPv4InAcceptedRange()
      throws Exception {
    when(authConfigMock.getTrustedProxyNetworks())
        .thenReturn(Set.of("10.16.0.0/16", "192.168.1.0/24", "8.8.8.8/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(newFakeHttpRequest("10.16.5.1", EXPECTED_USER), CUSTOM_LOGIN_HEADER))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void
      testExtractUserFromRequestWithAuthenticationHeaderAllowedWithExactIPv4InAcceptedRange()
          throws Exception {
    when(authConfigMock.getTrustedProxyNetworks())
        .thenReturn(Set.of("10.16.0.0/16", "192.168.1.0/24", "8.8.8.8/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(
                    newFakeAuthHttpRequest("10.16.5.1", BASIC_AUTHENTICATION_USER_HEADER),
                    HttpHeaders.AUTHORIZATION))
        .isEqualTo(EXPECTED_USER);
  }

  @Test
  public void testExtractUserFromRequestWithCustomHeaderRejectedWithNonMatchingExactIPv4()
      throws Exception {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of("2.2.2.2/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(newFakeHttpRequest("1.1.1.1", EXPECTED_USER), CUSTOM_LOGIN_HEADER))
        .isNull();
  }

  @Test
  public void testExtractUserFromRequestWithAuthenticationHeaderRejectedWithNonMatchingExactIPv4()
      throws Exception {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of("2.2.2.2/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(
                    newFakeAuthHttpRequest("1.1.1.1", BASIC_AUTHENTICATION_USER_HEADER),
                    HttpHeaders.AUTHORIZATION))
        .isNull();
  }

  @Test
  public void testExtractUserFromRequestRejectedWithIPv6() throws Exception {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of("255.255.255.255/32"));
    assertThat(
            remoteUserUtil
                .get()
                .getRemoteUser(
                    newFakeHttpRequest("2001:0db8:85a3:0000:0000:8a2e:0370:7334", "user"),
                    CUSTOM_LOGIN_HEADER))
        .isNull();
  }

  @Test
  public void testFailWhenUsingAnInvalidProxyNetworkCIDR() throws Exception {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of("invalid-network"));
    assertThrows(ProvisionException.class, () -> remoteUserUtil.get());
  }

  @Test
  public void testFailWhenUsingSingleIPAsProxyNetworkCIDR() throws Exception {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of("192.168.0.1"));
    assertThrows(ProvisionException.class, () -> remoteUserUtil.get());
  }

  @Test
  public void testFailWhenUsingIPv6AsProxyNetworkCIDR() throws Exception {
    when(authConfigMock.getTrustedProxyNetworks()).thenReturn(Set.of("2000::/3"));
    assertThrows(ProvisionException.class, () -> remoteUserUtil.get());
  }

  private static FakeHttpServletRequest newFakeHttpRequest(String remoteIp, String expectedUser) {
    FakeHttpServletRequest fakeRequest =
        new FakeHttpServletRequest() {
          @Override
          public String getRemoteAddr() {
            return remoteIp;
          }
        };
    fakeRequest.addHeader(CUSTOM_LOGIN_HEADER, expectedUser);
    return fakeRequest;
  }

  private static FakeHttpServletRequest newFakeAuthHttpRequest(
      String remoteIp, String basicAuthHeader) {
    FakeHttpServletRequest fakeRequest =
        new FakeHttpServletRequest() {
          @Override
          public String getRemoteAddr() {
            return remoteIp;
          }
        };
    fakeRequest.addHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader);
    return fakeRequest;
  }
}
