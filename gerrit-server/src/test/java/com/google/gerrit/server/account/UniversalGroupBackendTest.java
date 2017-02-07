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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.PROJECT_OWNERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.not;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.util.Set;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.Test;

public class UniversalGroupBackendTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private static final AccountGroup.UUID OTHER_UUID = new AccountGroup.UUID("other");

  private UniversalGroupBackend backend;
  private IdentifiedUser user;

  private DynamicSet<GroupBackend> backends;

  @Before
  public void setup() {
    user = createNiceMock(IdentifiedUser.class);
    replay(user);
    backends = new DynamicSet<>();
    backends.add(new SystemGroupBackend());
    backend = new UniversalGroupBackend(backends);
  }

  @Test
  public void testHandles() {
    assertTrue(backend.handles(ANONYMOUS_USERS));
    assertTrue(backend.handles(PROJECT_OWNERS));
    assertFalse(backend.handles(OTHER_UUID));
  }

  @Test
  public void testGet() {
    assertEquals("Registered Users", backend.get(REGISTERED_USERS).getName());
    assertEquals("Project Owners", backend.get(PROJECT_OWNERS).getName());
    assertNull(backend.get(OTHER_UUID));
  }

  @Test
  public void testSuggest() {
    assertTrue(backend.suggest("X", null).isEmpty());
    assertEquals(1, backend.suggest("project", null).size());
    assertEquals(1, backend.suggest("reg", null).size());
  }

  @Test
  public void testSytemGroupMemberships() {
    GroupMembership checker = backend.membershipsOf(user);
    assertTrue(checker.contains(REGISTERED_USERS));
    assertFalse(checker.contains(OTHER_UUID));
    assertFalse(checker.contains(PROJECT_OWNERS));
  }

  @Test
  public void testKnownGroups() {
    GroupMembership checker = backend.membershipsOf(user);
    Set<UUID> knownGroups = checker.getKnownGroups();
    assertEquals(2, knownGroups.size());
    assertTrue(knownGroups.contains(REGISTERED_USERS));
    assertTrue(knownGroups.contains(ANONYMOUS_USERS));
  }

  @Test
  public void testOtherMemberships() {
    final AccountGroup.UUID handled = new AccountGroup.UUID("handled");
    final AccountGroup.UUID notHandled = new AccountGroup.UUID("not handled");
    final IdentifiedUser member = createNiceMock(IdentifiedUser.class);
    final IdentifiedUser notMember = createNiceMock(IdentifiedUser.class);

    GroupBackend backend = createMock(GroupBackend.class);
    expect(backend.handles(handled)).andStubReturn(true);
    expect(backend.handles(not(eq(handled)))).andStubReturn(false);
    expect(backend.membershipsOf(anyObject(IdentifiedUser.class)))
        .andStubAnswer(
            new IAnswer<GroupMembership>() {
              @Override
              public GroupMembership answer() throws Throwable {
                Object[] args = getCurrentArguments();
                GroupMembership membership = createMock(GroupMembership.class);
                expect(membership.contains(eq(handled))).andStubReturn(args[0] == member);
                expect(membership.contains(not(eq(notHandled)))).andStubReturn(false);
                replay(membership);
                return membership;
              }
            });
    replay(member, notMember, backend);

    backends = new DynamicSet<>();
    backends.add(backend);
    backend = new UniversalGroupBackend(backends);

    GroupMembership checker = backend.membershipsOf(member);
    assertFalse(checker.contains(REGISTERED_USERS));
    assertFalse(checker.contains(OTHER_UUID));
    assertTrue(checker.contains(handled));
    assertFalse(checker.contains(notHandled));
    checker = backend.membershipsOf(notMember);
    assertFalse(checker.contains(handled));
    assertFalse(checker.contains(notHandled));
  }
}
