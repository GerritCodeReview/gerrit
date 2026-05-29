// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.send.FromAddressGeneratorProvider.DefaultUserAddressGenFactory;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

public class FromAddressGeneratorProviderTest {
  private Config config;
  private PersonIdent ident;
  private AccountCache accountCache;

  @Before
  public void setUp() throws Exception {
    config = new Config();
    ident = new PersonIdent("NAME", "e@email", Instant.EPOCH, ZoneOffset.ofHoursMinutes(0, 0));
    accountCache = mock(AccountCache.class);
  }

  private FromAddressGenerator create() {
    return new FromAddressGeneratorProvider(
            config, "Anonymous Coward", ident, accountCache, new DefaultUserAddressGenFactory())
        .get();
  }

  private void setFrom(String newFrom) {
    config.setString("sendemail", null, "from", newFrom);
  }

  private void setDomains(List<String> domains) {
    config.setStringList("sendemail", null, "allowedDomain", domains);
  }

  @Test
  public void defaultIsMIXED() {
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);
  }

  @Test
  public void selectUSER() {
    setFrom("USER");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.UserGen.class);

    setFrom("user");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.UserGen.class);

    setFrom("uSeR");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.UserGen.class);
  }

  @Test
  public void USER_FullyConfiguredUser() {
    setFrom("USER");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name);
    assertThat(r.email()).isEqualTo(email);
    verifyAccountCacheGet(user);
  }

  @Test
  public void USER_NoFullNameUser() {
    setFrom("USER");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isNull();
    assertThat(r.email()).isEqualTo(email);
    verifyAccountCacheGet(user);
  }

  @Test
  public void USER_NoPreferredEmailUser() {
    setFrom("USER");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name + " (Code Review)");
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyAccountCacheGet(user);
  }

  @Test
  public void USER_NullUser() {
    setFrom("USER");
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(ident.getName());
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyNoInteractions(accountCache);
  }

  @Test
  public void USERAllowDomain() {
    setFrom("USER");
    setDomains(Arrays.asList("*.example.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name);
    assertThat(r.email()).isEqualTo(email);
    verifyAccountCacheGet(user);
  }

  @Test
  public void USERNoAllowDomain() {
    setFrom("USER");
    setDomains(Arrays.asList("example.net"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name + " (Code Review)");
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyAccountCacheGet(user);
  }

  @Test
  public void USERAllowDomainTwice() {
    setFrom("USER");
    setDomains(Arrays.asList("example.net"));
    setDomains(Arrays.asList("example.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name);
    assertThat(r.email()).isEqualTo(email);
    verifyAccountCacheGet(user);
  }

  @Test
  public void USERAllowDomainTwiceReverse() {
    setFrom("USER");
    setDomains(Arrays.asList("example.com"));
    setDomains(Arrays.asList("example.net"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name + " (Code Review)");
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyAccountCacheGet(user);
  }

  @Test
  public void USERAllowTwoDomains() {
    setFrom("USER");
    setDomains(Arrays.asList("example.com", "example.net"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name);
    assertThat(r.email()).isEqualTo(email);
    verifyAccountCacheGet(user);
  }

  @Test
  public void selectSERVER() {
    setFrom("SERVER");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.ServerGen.class);

    setFrom("server");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.ServerGen.class);

    setFrom("sErVeR");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.ServerGen.class);
  }

  @Test
  public void SERVER_FullyConfiguredUser() {
    setFrom("SERVER");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = userNoLookup(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(ident.getName());
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyNoInteractions(accountCache);
  }

  @Test
  public void SERVER_NullUser() {
    setFrom("SERVER");
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(ident.getName());
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyNoInteractions(accountCache);
  }

  @Test
  public void selectMIXED() {
    setFrom("MIXED");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);

    setFrom("mixed");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);

    setFrom("mIxEd");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);
  }

  @Test
  public void MIXED_FullyConfiguredUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name + " (Code Review)");
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyAccountCacheGet(user);
  }

  @Test
  public void MIXED_NoFullNameUser() {
    setFrom("MIXED");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo("Anonymous Coward (Code Review)");
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyAccountCacheGet(user);
  }

  @Test
  public void MIXED_NoPreferredEmailUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(name + " (Code Review)");
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyAccountCacheGet(user);
  }

  @Test
  public void MIXED_NullUser() {
    setFrom("MIXED");
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(ident.getName());
    assertThat(r.email()).isEqualTo(ident.getEmailAddress());
    verifyNoInteractions(accountCache);
  }

  @Test
  public void CUSTOM_FullyConfiguredUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo("A " + name + " B");
    assertThat(r.email()).isEqualTo("my.server@email.address");
    verifyAccountCacheGet(user);
  }

  @Test
  public void CUSTOM_NoFullNameUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo("A Anonymous Coward B");
    assertThat(r.email()).isEqualTo("my.server@email.address");
  }

  @Test
  public void CUSTOM_NullUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name()).isEqualTo(ident.getName());
    assertThat(r.email()).isEqualTo("my.server@email.address");
  }

  private Account.Id user(String name, String email) {
    final AccountState s = makeUser(name, email);
    when(accountCache.get(eq(s.account().id()))).thenReturn(Optional.of(s));
    return s.account().id();
  }

  private void verifyAccountCacheGet(Account.Id id) {
    verify(accountCache).get(eq(id));
  }

  private Account.Id userNoLookup(String name, String email) {
    final AccountState s = makeUser(name, email);
    return s.account().id();
  }

  private AccountState makeUser(String name, String email) {
    final Account.Id userId = Account.id(42);
    final Account.Builder account = Account.builder(userId, TimeUtil.now());
    account.setFullName(name);
    account.setPreferredEmail(email);
    return AccountState.forAccount(account.build());
  }
}
