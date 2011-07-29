package com.google.gerrit.server.auth.crowd;

import static java.util.concurrent.TimeUnit.HOURS;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;

import java.util.Set;

public class CrowdModule extends CacheModule {
  static final String USERNAME_CACHE = "crowd_usernames";
  static final String GROUP_CACHE = "crowd_groups";

  @Override
  protected void configure() {
    final TypeLiteral<Cache<String, Set<AccountGroup.UUID>>> groups =
        new TypeLiteral<Cache<String, Set<AccountGroup.UUID>>>() {};
    core(groups, GROUP_CACHE).maxAge(1, HOURS).populateWith(CrowdMemberLoader.class);

    final TypeLiteral<Cache<String, Account.Id>> usernames =
        new TypeLiteral<Cache<String, Account.Id>>() {};
    core(usernames, USERNAME_CACHE).populateWith(CrowdUserLoader.class);

    bind(Realm.class).to(CrowdRealm.class).in(Scopes.SINGLETON);
    bind(CrowdHelper.class);
  }

}
