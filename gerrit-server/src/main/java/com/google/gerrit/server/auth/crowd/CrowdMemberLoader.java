package com.google.gerrit.server.auth.crowd;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.cache.EntryCreator;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.Set;

class CrowdMemberLoader extends EntryCreator<String, Set<AccountGroup.UUID>> {
  private final CrowdHelper helper;

  @Inject
  CrowdMemberLoader(final CrowdHelper helper) {
    this.helper = helper;
  }

  @Override
  public Set<AccountGroup.UUID> createEntry(final String username) throws Exception {
      return helper.groups(username);
  }

  @Override
  public Set<AccountGroup.UUID> missing(final String key) {
    return Collections.emptySet();
  }
}