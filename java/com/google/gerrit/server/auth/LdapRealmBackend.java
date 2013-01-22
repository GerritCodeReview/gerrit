// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.auth.ldap;

import com.google.common.cache.LoadingCache;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.AuthUser.UUID;
import com.google.gerrit.server.auth.RealmBackend;
import com.google.gerrit.server.auth.UserData;
import com.google.gerrit.server.auth.UserData.Builder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.io.IOException;
import java.util.Set;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.security.auth.login.LoginException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class LdapRealmBackend implements RealmBackend {
  public static String DOMAIN = "ldap";
  static final Logger log = LoggerFactory.getLogger(LdapRealmBackend.class);

  private Helper helper;
  private LoadingCache<String, Set<com.google.gerrit.reviewdb.client.AccountGroup.UUID>>
      membershipCache;

  @Inject
  LdapRealmBackend(
      final Helper helper,
      @Named(LdapModule.GROUP_CACHE)
          final LoadingCache<String, Set<AccountGroup.UUID>> membershipCache) {
    this.helper = helper;
    this.membershipCache = membershipCache;
  }

  @Override
  public boolean handles(UUID uuid) {
    return uuid.uuid().startsWith(DOMAIN);
  }

  @Override
  @Nullable
  public UserData getUserData(AuthUser user) {
    String username = user.getUsername();
    try {
      final DirContext ctx;
      if (user instanceof AuthUserLdapBind) {
        ctx = helper.authenticate(username, ((AuthUserLdapBind) user).getPassword());
      } else {
        ctx = helper.open();
      }
      try {
        final Helper.LdapSchema schema = helper.getSchema(ctx);
        final LdapQuery.Result m = helper.findAccount(schema, ctx, username, true);
        final Builder userData = new UserData.Builder(username);
        userData.setDisplayName(LdapRealm.apply(schema.accountFullName, m));
        userData.setExternalId(user.getUUID().uuid());

        if (schema.accountEmailAddress != null) {
          userData.setEmailAddress(LdapRealm.apply(schema.accountEmailAddress, m));
        }
        membershipCache.put(username, helper.queryForGroups(ctx, username, m));
        return userData.build();
      } finally {
        try {
          ctx.close();
        } catch (NamingException e) {
          log.warn("Cannot close LDAP query handle", e);
        }
      }
    } catch (NamingException e) {
      log.error("Cannot query LDAP to autenticate user", e);
    } catch (LoginException e) {
      log.error("Cannot authenticate server via JAAS", e);
    } catch (IOException | AccountException e) {
      log.error("Cannot get data for user:" + username, e);
    }
    return null;
  }
}
