// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;

import java.util.Set;

/**
 * Implementations of GroupMembership provide methods to test
 * the presence of a user in a particular group.
 *
 * @author cranger@google.com (Colby Ranger)
 */
public interface GroupMembership {

  /**
   * Returns {@code true} when the user this object was created for is a member
   * of the specified group.
   */
  boolean contains(AccountGroup.UUID groupId);

  /**
   * Returns {@code true} when the user this object was created for is a member
   * of any of the specified group.
   */
  boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds);

  Set<AccountGroup.UUID> getKnownGroups();
}
