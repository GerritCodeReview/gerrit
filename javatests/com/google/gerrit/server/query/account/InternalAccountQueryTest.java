// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.query.account;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;

import com.google.common.collect.Multimap;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.AccountConfig;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.InMemoryModule;
import com.google.gerrit.testing.IndexConfig;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InternalAccountQueryTest extends AbstractQueryAccountsTest {
  @Inject private Provider<AccountQueryProcessor> queryProcessorProvider;
  @Inject private com.google.gerrit.index.IndexConfig indexConfig;

  @Mock private AccountConfig accountConfig;

  @ConfigSuite.Default
  public static Config defaultConfig() {
    return IndexConfig.createForFake();
  }

  @Test
  @GerritConfig(name = "accounts.caseInsensitiveLocalPart", value = "example.com")
  public void internalByPreferredEmail() throws Exception {
    String mixedCaseEmail = "MixedCase@example.com";
    Account.Id mixedCaseUserId =
        createUser("mixedcase", "Mixed Case", mixedCaseEmail).getAccountId();

    String mixedCaseEmailOtherDomain = "MixedCase@other.com";
    Account.Id mixedCaseOtherDomainUserId =
        createUser("othermixedcase", "Other Mixed Case", mixedCaseEmailOtherDomain).getAccountId();

    String lowerCaseEmail = "lowercase@example.com";
    Account.Id lowerCaseUserId =
        createUser("lowercase", "Lower Case", lowerCaseEmail).getAccountId();

    doReturn(new String[] {}).when(accountConfig).getCaseInsensitiveLocalParts();

    assertAccountQueryByPreferredEmail(lowerCaseEmail, List.of(lowerCaseUserId));
    assertAccountQueryByPreferredEmail(mixedCaseEmail, List.of(mixedCaseUserId));
    assertAccountQueryByPreferredEmail(mixedCaseEmail.toLowerCase(Locale.US), List.of());
    assertAccountQueryByPreferredEmail(
        mixedCaseEmailOtherDomain, List.of(mixedCaseOtherDomainUserId));
    assertAccountQueryByPreferredEmail(mixedCaseEmailOtherDomain.toLowerCase(Locale.US), List.of());
    assertAccountQueryByPreferredEmail(
        List.of(lowerCaseEmail, mixedCaseEmail), List.of(lowerCaseUserId, mixedCaseUserId));
    assertAccountQueryByPreferredEmail(
        List.of(lowerCaseEmail, mixedCaseEmail.toLowerCase(Locale.US)), List.of(lowerCaseUserId));

    doReturn(new String[] {"example.com"}).when(accountConfig).getCaseInsensitiveLocalParts();

    assertAccountQueryByPreferredEmail(lowerCaseEmail, List.of(lowerCaseUserId));
    assertAccountQueryByPreferredEmail(mixedCaseEmail, List.of(mixedCaseUserId));
    assertAccountQueryByPreferredEmail(
        mixedCaseEmail.toLowerCase(Locale.US), List.of(mixedCaseUserId));
    assertAccountQueryByPreferredEmail(
        mixedCaseEmailOtherDomain, List.of(mixedCaseOtherDomainUserId));
    assertAccountQueryByPreferredEmail(mixedCaseEmailOtherDomain.toLowerCase(Locale.US), List.of());
    assertAccountQueryByPreferredEmail(
        List.of(lowerCaseEmail, mixedCaseEmail), List.of(lowerCaseUserId, mixedCaseUserId));
    assertAccountQueryByPreferredEmail(
        List.of(lowerCaseEmail, mixedCaseEmail.toLowerCase(Locale.US)),
        List.of(lowerCaseUserId, mixedCaseUserId));
  }

  private IdentifiedUser createUser(String username, String fullName, String email)
      throws Exception {
    Account.Id id = createAccount(username, fullName, email, true);
    return userFactory.create(id);
  }

  private InternalAccountQuery createInternalAccountQuery() {
    return new InternalAccountQuery(
        queryProcessorProvider.get(), indexes, indexConfig, externalIdKeyFactory, accountConfig);
  }

  private void assertAccountQueryByPreferredEmail(String email, List<Account.Id> expectedIds) {
    List<AccountState> result = createInternalAccountQuery().byPreferredEmail(email);
    assertThat(result.stream().map(r -> r.account().id()).collect(Collectors.toList()))
        .containsExactlyElementsIn(expectedIds);
  }

  private void assertAccountQueryByPreferredEmail(
      List<String> emails, List<Account.Id> expectedIds) {
    Multimap<String, AccountState> result = createInternalAccountQuery().byPreferredEmail(emails);
    assertThat(result.values().stream().map(r -> r.account().id()).collect(Collectors.toList()))
        .containsExactlyElementsIn(expectedIds);
  }

  @Override
  protected Injector createInjector() {
    Config fakeConfig = new Config();
    InMemoryModule.setDefaults(fakeConfig);
    fakeConfig.setString("index", null, "type", "fake");
    return Guice.createInjector(new InMemoryModule(fakeConfig));
  }
}
