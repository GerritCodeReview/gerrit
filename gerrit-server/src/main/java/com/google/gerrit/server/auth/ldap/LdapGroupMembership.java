/**
 *
 */
package com.google.gerrit.server.auth.ldap;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.project.ProjectCache;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

class LdapGroupMembership implements GroupMembership {
  private final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache;
  private final ProjectCache projectCache;
  private final String id;
  private GroupMembership membership;

  LdapGroupMembership(
      LoadingCache<String, Set<AccountGroup.UUID>> membershipCache,
      ProjectCache projectCache,
      String id) {
    this.membershipCache = membershipCache;
    this.projectCache = projectCache;
    this.id = id;
  }

  @Override
  public boolean contains(AccountGroup.UUID groupId) {
    return get().contains(groupId);
  }

  @Override
  public boolean containsAnyOf(Iterable<AccountGroup.UUID> groupIds) {
    return get().containsAnyOf(groupIds);
  }

  @Override
  public Set<AccountGroup.UUID> intersection(Iterable<AccountGroup.UUID> groupIds) {
    return get().intersection(groupIds);
  }

  @Override
  public Set<AccountGroup.UUID> getKnownGroups() {
    Set<AccountGroup.UUID> g = new HashSet<>(get().getKnownGroups());
    g.retainAll(projectCache.guessRelevantGroupUUIDs());
    return g;
  }

  private synchronized GroupMembership get() {
    if (membership == null) {
      try {
        membership = new ListGroupMembership(membershipCache.get(id));
      } catch (ExecutionException e) {
        LdapGroupBackend.log.warn(String.format(
            "Cannot lookup membershipsOf %s in LDAP", id), e);
        membership = GroupMembership.EMPTY;
      }
    }
    return membership;
  }
}
