package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.AccountGroup.Id;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;

import java.util.HashSet;
import java.util.Set;

public class ReplicationUser extends CurrentUser {
  @Inject
  GroupCache groupCache;

  String[] authGroupNames;

  private Set<Id> effectiveGroups;

  protected ReplicationUser(AuthConfig authConfig, String[] authGroupNames) {
    super(AccessPath.REPLICATION, authConfig);
    this.authGroupNames = authGroupNames;
  }

  @Override
  public Set<Id> getEffectiveGroups() {
    if (effectiveGroups == null) {
      effectiveGroups = new HashSet<Id>();
      if (authGroupNames == null) {
        effectiveGroups.addAll(authConfig.getAnonymousGroups());
      } else {
        effectiveGroups.addAll(authConfig.getRegisteredGroups());

        for (String authGroupName : authGroupNames) {
          AccountGroup group = groupCache.lookup(authGroupName);
          if (group != null) {
            effectiveGroups.add(group.getId());
          }
        }
      }
    }
    return effectiveGroups;
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    return null;
  }

  public static ReplicationUser create(AuthConfig authConfig,
      String[] authGroupNames) {
    return new ReplicationUser(authConfig, authGroupNames);
  }
}
