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

package com.google.gerrit.auth.oauth;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_EXTERNAL;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GOOGLE_OAUTH;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_HTTP;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_HTTPS;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_MAILTO;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_XRI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.auth.oauth.OAuthLoginProvider;
import com.google.gerrit.extensions.auth.oauth.OAuthUserInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class OAuthRealmTest {
  private static final String TEST_PLUGIN = "test-plugin";
  private static final String TEST_PROVIDER = "test-provider";
  private static final String TEST_USERNAME = "alice";
  private static final String TEST_PASSWORD = "secret";

  @Inject private ExternalIdFactory externalIdFactory;
  @Inject private AuthRequest.Factory authRequestFactory;

  @Mock private DynamicMap<OAuthLoginProvider> mockLoginProviders;
  @Mock private OAuthLoginProvider mockLoginProvider;

  private OAuthRealm oauthRealm;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);

    // Set up OAuthRealm with mocked login providers for authenticate tests
    when(mockLoginProviders.get(any(), any())).thenReturn(mockLoginProvider);
    oauthRealm =
        new OAuthRealm(authRequestFactory, mockLoginProviders, new org.eclipse.jgit.lib.Config());
  }

  private ExternalId id(String scheme, String id) {
    return externalIdFactory.create(scheme, id, Account.id(1000));
  }

  private boolean accountBelongsToRealm(ExternalId... ids) {
    return oauthRealm.accountBelongsToRealm(Arrays.asList(ids));
  }

  private boolean accountBelongsToRealm(String scheme, String id) {
    return accountBelongsToRealm(id(scheme, id));
  }

  @Test
  public void accountBelongsToRealm() throws Exception {
    assertThat(accountBelongsToRealm(SCHEME_EXTERNAL, "test")).isTrue();
    assertThat(accountBelongsToRealm(id(SCHEME_USERNAME, "test"), id(SCHEME_EXTERNAL, "test")))
        .isTrue();
    assertThat(accountBelongsToRealm(id(SCHEME_EXTERNAL, "test"), id(SCHEME_USERNAME, "test")))
        .isTrue();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "foo@bar.com")).isFalse();

    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "external")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_USERNAME, "xxexternalxx")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "external.foo@bar.com")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_MAILTO, "bar.external@bar.com")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_HTTP, "example.org/test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_HTTPS, "example.org/test")).isFalse();
    assertThat(accountBelongsToRealm(SCHEME_XRI, "example.org/test")).isFalse();
  }

  @Test
  public void authenticate_withCustomSchemeFromProvider_usesCustomScheme() throws Exception {
    // OAuth provider returns externalId with custom scheme "dex-oauth:alice"
    String expectedEmail = "alice@example.com";
    String expectedDisplayName = "Alice Smith";
    OAuthUserInfo userInfo =
        new OAuthUserInfo(
            "dex-oauth:alice", TEST_USERNAME, expectedEmail, expectedDisplayName, null);
    when(mockLoginProvider.login(TEST_USERNAME, TEST_PASSWORD)).thenReturn(userInfo);

    AuthRequest authRequest = authRequestFactory.createForExternalUser(TEST_USERNAME);
    authRequest.setPassword(TEST_PASSWORD);
    authRequest.setAuthPlugin(TEST_PLUGIN);
    authRequest.setAuthProvider(TEST_PROVIDER);

    AuthRequest result = oauthRealm.authenticate(authRequest);

    assertThat(result.getExternalIdKey().get()).isEqualTo("dex-oauth:alice");
    assertThat(result.getEmailAddress()).isEqualTo(expectedEmail);
    assertThat(result.getDisplayName()).isEqualTo(expectedDisplayName);
  }

  @Test
  public void authenticate_withDifferentCustomScheme_preservesScheme() throws Exception {
    // OAuth provider returns externalId with custom scheme "google-oauth:bob123"
    String expectedEmail = "bob@example.com";
    String expectedDisplayName = "Bob Jones";
    OAuthUserInfo userInfo =
        new OAuthUserInfo(
            SCHEME_GOOGLE_OAUTH + ":bob123", "bob", expectedEmail, expectedDisplayName, null);
    when(mockLoginProvider.login("bob", TEST_PASSWORD)).thenReturn(userInfo);

    AuthRequest authRequest = authRequestFactory.createForExternalUser("bob");
    authRequest.setPassword(TEST_PASSWORD);
    authRequest.setAuthPlugin(TEST_PLUGIN);
    authRequest.setAuthProvider(TEST_PROVIDER);

    AuthRequest result = oauthRealm.authenticate(authRequest);

    assertThat(result.getExternalIdKey().get()).isEqualTo(SCHEME_GOOGLE_OAUTH + ":bob123");
    assertThat(result.getEmailAddress()).isEqualTo(expectedEmail);
    assertThat(result.getDisplayName()).isEqualTo(expectedDisplayName);
  }

  @Test
  public void authenticate_withPlainUsernameFromProvider_usesDefaultScheme() throws Exception {
    // OAuth provider returns plain username without scheme prefix
    String expectedEmail = "charlie@example.com";
    String expectedDisplayName = "Charlie Brown";
    OAuthUserInfo userInfo =
        new OAuthUserInfo("charlie", "charlie", expectedEmail, expectedDisplayName, null);
    when(mockLoginProvider.login("charlie", TEST_PASSWORD)).thenReturn(userInfo);

    AuthRequest authRequest = authRequestFactory.createForExternalUser("charlie");
    authRequest.setPassword(TEST_PASSWORD);
    authRequest.setAuthPlugin(TEST_PLUGIN);
    authRequest.setAuthProvider(TEST_PROVIDER);

    AuthRequest result = oauthRealm.authenticate(authRequest);

    assertThat(result.getExternalIdKey().get()).isEqualTo(SCHEME_GOOGLE_OAUTH + ":charlie");
    assertThat(result.getEmailAddress()).isEqualTo(expectedEmail);
    assertThat(result.getDisplayName()).isEqualTo(expectedDisplayName);
  }
}
