package com.google.gerrit.server.auth.crowd;


import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Account.FieldName;
import com.google.gerrit.reviewdb.Account.Id;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroup.ExternalNameKey;
import com.google.gerrit.reviewdb.AccountGroup.UUID;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.auth.AuthUtils;
import com.google.gerrit.server.cache.Cache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.atlassian.crowd.model.group.Group;

import com.atlassian.crowd.model.user.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@Singleton
class CrowdRealm implements Realm {

  static final Logger log = LoggerFactory.getLogger(CrowdRealm.class);

  private final CrowdHelper crowdHelper;
  private final Cache<String, Id> usernameCache;
  private final Cache<String, Set<UUID>> membershipCache;

  @Inject
  CrowdRealm(CrowdHelper crowdHelper,
      @Named(CrowdModule.GROUP_CACHE) final Cache<String, Set<AccountGroup.UUID>> membershipCache,
      @Named(CrowdModule.USERNAME_CACHE) final Cache<String, Account.Id> usernameCache) {
    this.crowdHelper = crowdHelper;
    this.membershipCache = membershipCache;
    this.usernameCache = usernameCache;
  }

  @Override
  public boolean allowsEdit(FieldName field) {
    return false; // Crowd would allow it but I'm trying to move quickly
  }

  @Override
  public AuthRequest authenticate(AuthRequest who) throws AccountException {
    User user = crowdHelper.authenticate(who.getUserName(), who.getPassword());

    who.setDisplayName(user.getDisplayName());
    who.setEmailAddress(user.getEmailAddress());
    who.setUserName(user.getName());

    membershipCache.put(who.getUserName(), crowdHelper.groups(who.getUserName()));

    return who;
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {
    usernameCache.put(who.getLocalUser(), account.getId());
  }

  @Override
  public Set<UUID> groups(AccountState who) {
    final HashSet<AccountGroup.UUID> r = new HashSet<AccountGroup.UUID>();

    // I don't understand this hoop I'm jumping through but the LdapRealm does it...
    String username = AuthUtils.findUsername(AccountExternalId.SCHEME_GERRIT, who.getExternalIds());
    r.addAll(membershipCache.get(username));
    r.addAll(who.getInternalGroups());
    return r;
  }

  @Override
  public Id lookup(String accountName) {
    return usernameCache.get(accountName);
  }

  @Override
  public Set<ExternalNameKey> lookupGroups(String name) {
    HashSet<ExternalNameKey> ret = new HashSet<AccountGroup.ExternalNameKey>();

    Group g = crowdHelper.groupByName(name);

    if (g != null)
      ret.add(new ExternalNameKey(g.getName()));

    return ret;
  }

}
