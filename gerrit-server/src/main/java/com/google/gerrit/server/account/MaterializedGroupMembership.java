// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.server.account;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Creates a GroupMembership object from materialized collection of groups.
 *
 * @author cranger@google.com (Colby Ranger)
 */
public class MaterializedGroupMembership implements GroupMembership {
  public interface Factory {
    MaterializedGroupMembership create(Collection<AccountGroup.UUID> groupIds);
  }

  private final GroupIncludeCache groupIncludeCache;
  private final Set<AccountGroup.UUID> includes;
  private final Queue<AccountGroup.UUID> groupQueue;

  @Inject
  MaterializedGroupMembership(
      GroupIncludeCache groupIncludeCache,
      @Assisted Collection<AccountGroup.UUID> seedGroups) {
    this.groupIncludeCache = groupIncludeCache;
    this.includes = new HashSet<AccountGroup.UUID> (seedGroups);
    this.groupQueue = new LinkedList<AccountGroup.UUID> (seedGroups);
  }

  @Override
  public boolean contains(AccountGroup.UUID id) {
    if (id == null) {
      return false;
    }
    if (includes.contains(id)) {
      return true;
    }
    return findIncludedGroup(id);
  }

  @Override
  public boolean containsAnyOf(Iterable<AccountGroup.UUID> ids) {
    for (AccountGroup.UUID groupId : ids) {
      if (contains(groupId)) {
        return true;
      }
    }
    return false;
  }

  private boolean findIncludedGroup(AccountGroup.UUID query) {
    boolean found = false;
    while (!found && groupQueue.size() > 0) {
      AccountGroup.UUID id = groupQueue.remove();

      for (final AccountGroup.UUID groupId : groupIncludeCache.getByInclude(id)) {
        if (includes.add(groupId)) {
          groupQueue.add(groupId);
          found |= groupId.equals(query);
        }
      }
    }

    return found;
  }

  @Override
  public Set<UUID> getKnownGroups() {
    findIncludedGroup(null); // find all
    return Sets.newHashSet(includes);
  }
}
