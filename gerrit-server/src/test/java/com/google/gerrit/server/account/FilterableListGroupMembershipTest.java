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

import com.google.common.base.Predicate;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;

import java.util.Arrays;
import java.util.Set;

import org.junit.Test;
import org.junit.Before;

import static org.junit.Assert.*;
import static java.util.Arrays.asList;

public class FilterableListGroupMembershipTest {
  private static final AccountGroup.UUID VISIBLE = new AccountGroup.UUID("visible");
  private static final AccountGroup.UUID HIDDEN = new AccountGroup.UUID("hidden");
  private static final AccountGroup.UUID OTHER = new AccountGroup.UUID("other");
  private FilterableListGroupMembership membershipChecker;

  @Before
  public void setup() {
    Iterable<AccountGroup.UUID> groupIds = asList(VISIBLE, HIDDEN);
    Predicate<AccountGroup.UUID> isMember = new Predicate<AccountGroup.UUID>() {

      @Override
      public boolean apply(AccountGroup.UUID input) {
        return VISIBLE.equals(input);
      }
    };
    membershipChecker = new FilterableListGroupMembership(groupIds, isMember);
  }

  @Test
  public void testContains() throws Exception {
    assertTrue(membershipChecker.contains(VISIBLE));
    assertFalse(membershipChecker.contains(HIDDEN));
    assertFalse(membershipChecker.contains(OTHER));
  }

  @Test
  public void testContainsIsNotPrecalculated() throws Exception {
    final boolean[] contained = new boolean[1];
    Predicate<AccountGroup.UUID> test = new Predicate<AccountGroup.UUID>() {
      @Override
      public boolean apply(AccountGroup.UUID input) {
        return contained[0];
      }
    };
    membershipChecker = new FilterableListGroupMembership(asList(VISIBLE), test);
    contained[0] = true;
    assertTrue(membershipChecker.contains(VISIBLE));

    contained[0] = false;
    assertFalse(membershipChecker.contains(VISIBLE));
  }

  @Test
  public void testKnownGroups() throws Exception {
    Set<UUID> result = membershipChecker.getKnownGroups();
    assertEquals(1, result.size());
    assertTrue(result.contains(VISIBLE));
  }

  @Test
  public void testContainsAnyOf() throws Exception {
    assertFalse(membershipChecker.containsAnyOf(Arrays.<AccountGroup.UUID>asList()));
    assertFalse(membershipChecker.containsAnyOf(asList(OTHER)));
    assertFalse(membershipChecker.containsAnyOf(asList(HIDDEN)));
    assertTrue(membershipChecker.containsAnyOf(asList(HIDDEN, VISIBLE)));
  }

  @Test
  public void testIntersect() throws Exception {
    assertTrue(membershipChecker.intersection(Arrays.<AccountGroup.UUID>asList()).isEmpty());
    assertTrue(membershipChecker.intersection(asList(OTHER)).isEmpty());
    assertTrue(membershipChecker.intersection(asList(HIDDEN)).isEmpty());
    Set<UUID> result = membershipChecker.intersection(asList(HIDDEN, VISIBLE));
    assertEquals(1, result.size());
    assertTrue(result.contains(VISIBLE));
  }
}

