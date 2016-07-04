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

package com.google.gerrit.server.mail;

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

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
    return new FromAddressGeneratorProvider(config, "Anonymous Coward", ident,
        accountCache).get();
  }

  private void setFrom(final String newFrom) {
    config.setString("sendemail", null, "from", newFrom);
  }

  @Test
  public void testDefaultIsMIXED() {
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);
  }

  @Test
  public void testSelectUSER() {
    setFrom("USER");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.UserGen.class);

    setFrom("user");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.UserGen.class);

    setFrom("uSeR");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.UserGen.class);
  }

  @Test
  public void testUSER_FullyConfiguredUser() {
    setFrom("USER");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(name);
    assertThat(r.email).isEqualTo(email);
    verify(accountCache);
  }

  @Test
  public void testUSER_NoFullNameUser() {
    setFrom("USER");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isNull();
    assertThat(r.email).isEqualTo(email);
    verify(accountCache);
  }

  @Test
  public void testUSER_NoPreferredEmailUser() {
    setFrom("USER");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(name);
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testUSER_NullUser() {
    setFrom("USER");
    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(ident.getName());
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testSelectSERVER() {
    setFrom("SERVER");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.ServerGen.class);

    setFrom("server");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.ServerGen.class);

    setFrom("sErVeR");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.ServerGen.class);
  }

  @Test
  public void testSERVER_FullyConfiguredUser() {
    setFrom("SERVER");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = userNoLookup(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(ident.getName());
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testSERVER_NullUser() {
    setFrom("SERVER");
    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(ident.getName());
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testSelectMIXED() {
    setFrom("MIXED");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);

    setFrom("mixed");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);

    setFrom("mIxEd");
    assertThat(create()).isInstanceOf(FromAddressGeneratorProvider.PatternGen.class);
  }

  @Test
  public void testMIXED_FullyConfiguredUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(name + " (Code Review)");
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testMIXED_NoFullNameUser() {
    setFrom("MIXED");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo("Anonymous Coward (Code Review)");
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testMIXED_NoPreferredEmailUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(name + " (Code Review)");
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testMIXED_NullUser() {
    setFrom("MIXED");
    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(ident.getName());
    assertThat(r.email).isEqualTo(ident.getEmailAddress());
    verify(accountCache);
  }

  @Test
  public void testCUSTOM_FullyConfiguredUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo("A " + name + " B");
    assertThat(r.email).isEqualTo("my.server@email.address");
    verify(accountCache);
  }

  @Test
  public void testCUSTOM_NoFullNameUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo("A Anonymous Coward B");
    assertThat(r.email).isEqualTo("my.server@email.address");
    verify(accountCache);
  }

  @Test
  public void testCUSTOM_NullUser() {
    setFrom("A ${user} B <my.server@email.address>");

    replay(accountCache);
    final Address r = create().from(null);
    assertThat(r).isNotNull();
    assertThat(r.name).isEqualTo(ident.getName());
    assertThat(r.email).isEqualTo("my.server@email.address");
    verify(accountCache);
  }

  private Account.Id user(final String name, final String email) {
    final AccountState s = makeUser(name, email);
    expect(accountCache.get(eq(s.getAccount().getId()))).andReturn(s);
    return s.getAccount().getId();
  }

  private Account.Id userNoLookup(final String name, final String email) {
    final AccountState s = makeUser(name, email);
    return s.getAccount().getId();
  }

  private AccountState makeUser(final String name, final String email) {
    final Account.Id userId = new Account.Id(42);
    final Account account = new Account(userId, TimeUtil.nowTs());
    account.setFullName(name);
    account.setPreferredEmail(email);
    return new AccountState(account, Collections.<AccountGroup.UUID> emptySet(),
        Collections.<AccountExternalId> emptySet(),
        Collections.<AccountProjectWatch> emptySet());
  }
}
