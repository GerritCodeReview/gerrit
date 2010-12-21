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

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.util.Collections;

public class FromAddressGeneratorProviderTest extends TestCase {
  private Config config;
  private PersonIdent ident;
  private AccountCache accountCache;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    config = new Config();
    ident = new PersonIdent("NAME", "e@email", 0, 0);
    accountCache = createStrictMock(AccountCache.class);
  }

  private FromAddressGenerator create() {
    return new FromAddressGeneratorProvider(config, ident, accountCache).get();
  }

  private void setFrom(final String newFrom) {
    config.setString("sendemail", null, "from", newFrom);
  }

  public void testDefaultIsMIXED() {
    assertTrue(create() instanceof FromAddressGeneratorProvider.PatternGen);
  }

  public void testSelectUSER() {
    setFrom("USER");
    assertTrue(create() instanceof FromAddressGeneratorProvider.UserGen);

    setFrom("user");
    assertTrue(create() instanceof FromAddressGeneratorProvider.UserGen);

    setFrom("uSeR");
    assertTrue(create() instanceof FromAddressGeneratorProvider.UserGen);
  }

  public void testUSER_FullyConfiguredUser() {
    setFrom("USER");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals(name, r.name);
    assertEquals(email, r.email);
    verify(accountCache);
  }

  public void testUSER_NoFullNameUser() {
    setFrom("USER");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals(null, r.name);
    assertEquals(email, r.email);
    verify(accountCache);
  }

  public void testUSER_NoPreferredEmailUser() {
    setFrom("USER");

    final Account.Id user = user("A U. Thor", null);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals(ident.getName(), r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testUSER_NullUser() {
    setFrom("USER");
    replay(accountCache);
    final Address r = create().from(null);
    assertNotNull(r);
    assertEquals(ident.getName(), r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testSelectSERVER() {
    setFrom("SERVER");
    assertTrue(create() instanceof FromAddressGeneratorProvider.ServerGen);

    setFrom("server");
    assertTrue(create() instanceof FromAddressGeneratorProvider.ServerGen);

    setFrom("sErVeR");
    assertTrue(create() instanceof FromAddressGeneratorProvider.ServerGen);
  }

  public void testSERVER_FullyConfiguredUser() {
    setFrom("SERVER");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = userNoLookup(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals(ident.getName(), r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testSERVER_NullUser() {
    setFrom("SERVER");
    replay(accountCache);
    final Address r = create().from(null);
    assertNotNull(r);
    assertEquals(ident.getName(), r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testSelectMIXED() {
    setFrom("MIXED");
    assertTrue(create() instanceof FromAddressGeneratorProvider.PatternGen);

    setFrom("mixed");
    assertTrue(create() instanceof FromAddressGeneratorProvider.PatternGen);

    setFrom("mIxEd");
    assertTrue(create() instanceof FromAddressGeneratorProvider.PatternGen);
  }

  public void testMIXED_FullyConfiguredUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals(name + " (Code Review)", r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testMIXED_NoFullNameUser() {
    setFrom("MIXED");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals("Anonymous Coward (Code Review)", r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testMIXED_NoPreferredEmailUser() {
    setFrom("MIXED");

    final String name = "A U. Thor";
    final Account.Id user = user(name, null);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals(name + " (Code Review)", r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testMIXED_NullUser() {
    setFrom("MIXED");
    replay(accountCache);
    final Address r = create().from(null);
    assertNotNull(r);
    assertEquals(ident.getName(), r.name);
    assertEquals(ident.getEmailAddress(), r.email);
    verify(accountCache);
  }

  public void testCUSTOM_FullyConfiguredUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String name = "A U. Thor";
    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(name, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals("A " + name + " B", r.name);
    assertEquals("my.server@email.address", r.email);
    verify(accountCache);
  }

  public void testCUSTOM_NoFullNameUser() {
    setFrom("A ${user} B <my.server@email.address>");

    final String email = "a.u.thor@test.example.com";
    final Account.Id user = user(null, email);

    replay(accountCache);
    final Address r = create().from(user);
    assertNotNull(r);
    assertEquals("A Anonymous Coward B", r.name);
    assertEquals("my.server@email.address", r.email);
    verify(accountCache);
  }

  public void testCUSTOM_NullUser() {
    setFrom("A ${user} B <my.server@email.address>");

    replay(accountCache);
    final Address r = create().from(null);
    assertNotNull(r);
    assertEquals(ident.getName(), r.name);
    assertEquals("my.server@email.address", r.email);
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
    final Account account = new Account(userId);
    account.setFullName(name);
    account.setPreferredEmail(email);
    final AccountState s =
        new AccountState(account, Collections.<AccountGroup.UUID> emptySet(),
            Collections.<AccountExternalId> emptySet());
    return s;
  }
}
