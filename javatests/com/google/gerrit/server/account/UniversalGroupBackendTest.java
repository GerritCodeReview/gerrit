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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.plugincontext.PluginContext.PluginMetrics;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class UniversalGroupBackendTest {
  private static final AccountGroup.UUID OTHER_UUID = AccountGroup.uuid("other");

  private UniversalGroupBackend backend;
  private IdentifiedUser user;

  private DynamicSet<GroupBackend> backends;

  @Before
  public void setup() {
    user = mock(IdentifiedUser.class);
    backends = new DynamicSet<>();
    backends.add("gerrit", new SystemGroupBackend(new Config()));
    backend =
        new UniversalGroupBackend(
            new PluginSetContext<>(backends, PluginMetrics.DISABLED_INSTANCE),
            new DisabledMetricMaker());
  }

  @Test
  public void handles() {
    assertTrue(backend.handles(ANONYMOUS_USERS));
    assertTrue(backend.handles(PROJECT_OWNERS));
    assertFalse(backend.handles(OTHER_UUID));
  }

  @Test
  public void get() {
    assertEquals("Registered Users", backend.get(REGISTERED_USERS).getName());
    assertEquals("Project Owners", backend.get(PROJECT_OWNERS).getName());
    assertNull(backend.get(OTHER_UUID));
  }

  @Test
  public void suggest() {
    assertTrue(backend.suggest("X", null).isEmpty());
    assertEquals(1, backend.suggest("project", null).size());
    assertEquals(1, backend.suggest("reg", null).size());
  }

  @Test
  public void sytemGroupMemberships() {
    GroupMembership checker = backend.membershipsOf(user);
    assertTrue(checker.contains(REGISTERED_USERS));
    assertFalse(checker.contains(OTHER_UUID));
    assertFalse(checker.contains(PROJECT_OWNERS));
  }

  @Test
  public void knownGroups() {
    GroupMembership checker = backend.membershipsOf(user);
    Set<UUID> knownGroups = checker.getKnownGroups();
    assertEquals(2, knownGroups.size());
    assertTrue(knownGroups.contains(REGISTERED_USERS));
    assertTrue(knownGroups.contains(ANONYMOUS_USERS));
  }

  @Test
  public void otherMemberships() {
    final AccountGroup.UUID handled = AccountGroup.uuid("handled");
    final AccountGroup.UUID notHandled = AccountGroup.uuid("not handled");
    final IdentifiedUser member = mock(IdentifiedUser.class);
    final IdentifiedUser notMember = mock(IdentifiedUser.class);

    GroupBackend backend = mock(GroupBackend.class);
    when(backend.handles(eq(handled))).thenReturn(true);
    when(backend.handles(not(eq(handled)))).thenReturn(false);
    when(backend.membershipsOf(any(IdentifiedUser.class)))
        .thenAnswer(
            new Answer<GroupMembership>() {
              @Override
              public GroupMembership answer(InvocationOnMock invocation) {
                GroupMembership membership = mock(GroupMembership.class);
                when(membership.contains(eq(handled)))
                    .thenReturn(invocation.getArguments()[0] == member);
                when(membership.contains(eq(notHandled))).thenReturn(false);
                return membership;
              }
            });

    backends = new DynamicSet<>();
    backends.add("gerrit", backend);
    backend =
        new UniversalGroupBackend(
            new PluginSetContext<>(backends, PluginMetrics.DISABLED_INSTANCE),
            new DisabledMetricMaker());

    GroupMembership checker = backend.membershipsOf(member);
    assertFalse(checker.contains(REGISTERED_USERS));
    assertFalse(checker.contains(OTHER_UUID));
    assertTrue(checker.contains(handled));
    assertFalse(checker.contains(notHandled));
    checker = backend.membershipsOf(notMember);
    assertFalse(checker.contains(handled));
    assertFalse(checker.contains(notHandled));
  }

  private static GroupReference newGroup(String name) {
    return GroupReference.create(AccountGroup.uuid(name.toLowerCase().replace(' ', '_')), name);
  }

  private static List<String> suggest(UniversalGroupBackend backend, String name) {
    Collection<GroupReference> suggestions = backend.suggest(name, null);
    return suggestions.stream().map(GroupReference::getName).collect(Collectors.toList());
  }

  @Test
  public void suggestInterleaved() {
    GroupBackend backend1 = mock(GroupBackend.class);
    when(backend1.suggest(any(), any()))
        .thenReturn(
            ImmutableList.of(
                newGroup("Administrators"), // 14
                newGroup("Users") // 5
                ));

    GroupBackend backend2 = mock(GroupBackend.class);
    when(backend2.suggest(any(), any()))
        .thenReturn(
            ImmutableList.of(
                newGroup("Test Users"), // 10
                newGroup("Project Owners") // 14
                ));

    backends = new DynamicSet<>();
    backends.add("gerrit", backend1);
    backends.add("test", backend2);
    backend =
        new UniversalGroupBackend(
            new PluginSetContext<>(backends, PluginMetrics.DISABLED_INSTANCE),
            new DisabledMetricMaker());

    // Test interleaving
    List<String> actual = suggest(backend, "user");
    assertEquals(
        ImmutableList.of("Users", "Test Users", "Administrators", "Project Owners"), actual);

    // Test exact match first
    actual = suggest(backend, "Administrators");
    assertEquals(
        ImmutableList.of("Administrators", "Users", "Test Users", "Project Owners"), actual);

    // Test exact match first (case-insensitive)
    actual = suggest(backend, "aDMINistrators");
    assertEquals(
        ImmutableList.of("Administrators", "Users", "Test Users", "Project Owners"), actual);
  }

  @Test
  public void suggestWithDifferentSizeLists() {
    GroupBackend backend1 = mock(GroupBackend.class);
    when(backend1.suggest(any(), any()))
        .thenReturn(ImmutableList.of(newGroup("a"), newGroup("bbb"), newGroup("eeeee")));

    GroupBackend backend2 = mock(GroupBackend.class);
    when(backend2.suggest(any(), any()))
        .thenReturn(ImmutableList.of(newGroup("cc"), newGroup("dddd")));

    backends = new DynamicSet<>();
    backends.add("gerrit", backend1);
    backends.add("test", backend2);
    backend =
        new UniversalGroupBackend(
            new PluginSetContext<>(backends, PluginMetrics.DISABLED_INSTANCE),
            new DisabledMetricMaker());

    List<String> actual = suggest(backend, "a");
    assertEquals(ImmutableList.of("a", "cc", "bbb", "dddd", "eeeee"), actual);

    actual = suggest(backend, "dddd");
    assertEquals(ImmutableList.of("dddd", "a", "cc", "bbb", "eeeee"), actual);
  }
}
