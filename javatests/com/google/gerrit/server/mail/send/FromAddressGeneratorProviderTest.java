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
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.mail.Address;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    ident = new PersonIdent("NAME", "e@email", 0, 0);
    accountCache = createStrictMock(AccountCache.class);
  }

  private FromAddressGenerator create() {
    return new FromAddressGeneratorProvider(config, "Anonymous Coward", ident, accountCache).get();
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

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name);
    assertThat(r.getEmail()).isEqualTo(email);
    verify(accountCache);
  }

  @Test
  public void USER_NoFullNameUser() {
    setFrom("USER");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isNull();
    assertThat(r.getEmail()).isEqualTo(email);
    verify(accountCache);
  }

  @Test
  public void USER_NoPreferredEmailUser() {
    setFrom("USER");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name + " (Code Review)");
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void USER_NullUser() {
    setFrom("USER");
    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(ident.getName());
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void USERAllowDomain() {
    setFrom("USER");
    setDomains(Arrays.asList("*.example.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name);
    assertThat(r.getEmail()).isEqualTo(email);
    verify(accountCache);
  }

  @Test
  public void USERNoAllowDomain() {
    setFrom("USER");
    setDomains(Arrays.asList("example.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@test.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name + " (Code Review)");
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void USERAllowDomainTwice() {
    setFrom("USER");
    setDomains(Arrays.asList("example.com"));
    setDomains(Arrays.asList("test.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@test.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name);
    assertThat(r.getEmail()).isEqualTo(email);
    verify(accountCache);
  }

  @Test
  public void USERAllowDomainTwiceReverse() {
    setFrom("USER");
    setDomains(Arrays.asList("test.com"));
    setDomains(Arrays.asList("example.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@test.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name + " (Code Review)");
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void USERAllowTwoDomains() {
    setFrom("USER");
    setDomains(Arrays.asList("example.com", "test.com"));
    final String name = "A U. Thor";
    final String email = "a.u.thor@test.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name);
    assertThat(r.getEmail()).isEqualTo(email);
    verify(accountCache);
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

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(ident.getName());
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void SERVER_NullUser() {
    setFrom("SERVER");
    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(ident.getName());
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
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

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name + " (Code Review)");
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void MIXED_NoFullNameUser() {
    setFrom("MIXED");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo("Anonymous Coward (Code Review)");
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void MIXED_NoPreferredEmailUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(name + " (Code Review)");
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void MIXED_NullUser() {
    setFrom("MIXED");
    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(ident.getName());
    assertThat(r.getEmail()).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void CUSTOM_FullyConfiguredUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo("A " + name + " B");
    assertThat(r.getEmail()).isEqualTo("my.server@email.address");
    verify(accountCache);
  }

  @Test
  public void CUSTOM_NoFullNameUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo("A Anonymous Coward B");
    assertThat(r.getEmail()).isEqualTo("my.server@email.address");
    verify(accountCache);
  }

  @Test
  public void CUSTOM_NullUser() {
    setFrom("A ${user} B <my.server@email.address>");

    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.getName()).isEqualTo(ident.getName());
    assertThat(r.getEmail()).isEqualTo("my.server@email.address");
    verify(accountCache);
  }

  private Account.Id user(String name, String email) {
    final AccountState s = makeUser(name, email);
    expect(accountCache.get(eq(s.getAccount().getId()))).andReturn(s);
    return s.getAccount().getId();
  }

  private Account.Id userNoLookup(String name, String email) {
    final AccountState s = makeUser(name, email);
    return s.getAccount().getId();
  }

  private AccountState makeUser(String name, String email) {
    final Account.Id userId = new Account.Id(42);
    final Account account = new Account(userId, TimeUtil.nowTs());
    account.setFullName(name);
    account.setPreferredEmail(email);
    return new AccountState(
        account, Collections.emptySet(), Collections.emptySet(), new HashMap<>());
  }
}
