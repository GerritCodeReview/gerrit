// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.AccountGroup;
import java.util.Collections;
import java.util.Set;

/**
 * Implementations of GroupMembership provide methods to test the presence of a user in a particular
 * group.
 */
public interface GroupMembership {
  GroupMembership EMPTY = new ListGroupMembership(Collections.<AccountGroup.UUID>emptySet());

  /**
   * Returns {@code true} when the user this object was created for is a member of the specified
   * group.
   */
  boolean contains(AccountGroup.UUID groupId);

  /**
   * Returns {@code true} when the user this object was created for is a member of any of the
   * specified group.
   */
  boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds);

  /**
   * Returns a set containing an input member of {@code contains(id)} is true.
   *
   * <p>This is batch form of contains that returns specific group information. Implementors may
   * implement the method as:
   *
   * <pre>
   * Set&lt;AccountGroup.UUID&gt; r = new HashSet&lt;&gt;();
   * for (AccountGroup.UUID id : groupIds)
   *   if (contains(id)) r.add(id);
   * </pre>
   */
  Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupIds);

  /**
   * Returns the set of groups that can be determined by the implementation. This may not return all
   * groups the {@link #contains(AccountGroup.UUID)} would return {@code true} for, but will at
   * least contain all top level groups. This restriction stems from the API of some group systems,
   * which make it expensive to enumerate the members of a group.
   */
  Set<AccountGroup.UUID> getKnownGroups();
}
