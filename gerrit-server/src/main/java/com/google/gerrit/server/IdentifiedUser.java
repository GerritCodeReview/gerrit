// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountDiffPreference;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.StarredChange;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nullable;

/** An authenticated user. */
public class IdentifiedUser extends CurrentUser {
  /** Create an IdentifiedUser, ignoring any per-request state. */
  @Singleton
  public static class GenericFactory {
    private final AuthConfig authConfig;
    private final Provider<String> canonicalUrl;
    private final Realm realm;
    private final AccountCache accountCache;
    private final GroupIncludeCache groupIncludeCache;

    @Inject
    GenericFactory(final AuthConfig authConfig,
        final @CanonicalWebUrl Provider<String> canonicalUrl,
        final Realm realm, final AccountCache accountCache,
        final GroupIncludeCache groupIncludeCache) {
      this.authConfig = authConfig;
      this.canonicalUrl = canonicalUrl;
      this.realm = realm;
      this.accountCache = accountCache;
      this.groupIncludeCache = groupIncludeCache;
    }

    public IdentifiedUser create(final Account.Id id) {
      return create(AccessPath.UNKNOWN, null, id);
    }

    public IdentifiedUser create(Provider<ReviewDb> db, Account.Id id) {
      return new IdentifiedUser(AccessPath.UNKNOWN, authConfig, canonicalUrl,
          realm, accountCache, groupIncludeCache, null, db, id);
    }

    public IdentifiedUser create(AccessPath accessPath,
        Provider<SocketAddress> remotePeerProvider, Account.Id id) {
      return new IdentifiedUser(accessPath, authConfig, canonicalUrl, realm,
          accountCache, groupIncludeCache, remotePeerProvider, null, id);
    }
  }

  /**
   * Create an IdentifiedUser, relying on current request state.
   * <p>
   * Can only be used from within a module that has defined request scoped
   * {@code @RemotePeer SocketAddress} and {@code ReviewDb} providers.
   */
  @Singleton
  public static class RequestFactory {
    private final AuthConfig authConfig;
    private final Provider<String> canonicalUrl;
    private final Realm realm;
    private final AccountCache accountCache;
    private final GroupIncludeCache groupIncludeCache;

    private final Provider<SocketAddress> remotePeerProvider;
    private final Provider<ReviewDb> dbProvider;

    @Inject
    RequestFactory(final AuthConfig authConfig,
        final @CanonicalWebUrl Provider<String> canonicalUrl,
        final Realm realm, final AccountCache accountCache,
        final GroupIncludeCache groupIncludeCache,

        final @RemotePeer Provider<SocketAddress> remotePeerProvider,
        final Provider<ReviewDb> dbProvider) {
      this.authConfig = authConfig;
      this.canonicalUrl = canonicalUrl;
      this.realm = realm;
      this.accountCache = accountCache;
      this.groupIncludeCache = groupIncludeCache;

      this.remotePeerProvider = remotePeerProvider;
      this.dbProvider = dbProvider;
    }

    public IdentifiedUser create(final AccessPath accessPath,
        final Account.Id id) {
      return new IdentifiedUser(accessPath, authConfig, canonicalUrl, realm,
          accountCache, groupIncludeCache, remotePeerProvider, dbProvider, id);
    }
  }

  private static final Logger log =
      LoggerFactory.getLogger(IdentifiedUser.class);

  private final Provider<String> canonicalUrl;
  private final Realm realm;
  private final AccountCache accountCache;
  private final GroupIncludeCache groupIncludeCache;

  @Nullable
  private final Provider<SocketAddress> remotePeerProvider;

  @Nullable
  private final Provider<ReviewDb> dbProvider;

  private final Account.Id accountId;

  private AccountState state;
  private Set<String> emailAddresses;
  private Set<AccountGroup.Id> effectiveGroups;
  private Set<Change.Id> starredChanges;
  private Collection<AccountProjectWatch> notificationFilters;

  private IdentifiedUser(final AccessPath accessPath,
      final AuthConfig authConfig, final Provider<String> canonicalUrl,
      final Realm realm, final AccountCache accountCache,
      final GroupIncludeCache groupIncludeCache,
      @Nullable final Provider<SocketAddress> remotePeerProvider,
      @Nullable final Provider<ReviewDb> dbProvider, final Account.Id id) {
    super(accessPath, authConfig);
    this.canonicalUrl = canonicalUrl;
    this.realm = realm;
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.remotePeerProvider = remotePeerProvider;
    this.dbProvider = dbProvider;
    this.accountId = id;
  }

  private AccountState state() {
    if (state == null) {
      state = accountCache.get(getAccountId());
    }
    return state;
  }

  /** The account identity for the user. */
  public Account.Id getAccountId() {
    return accountId;
  }

  /** @return the user's user name; null if one has not been selected/assigned. */
  public String getUserName() {
    return state().getUserName();
  }

  public Account getAccount() {
    return state().getAccount();
  }

  public AccountDiffPreference getAccountDiffPreference() {
    AccountDiffPreference diffPref;
    try {
      diffPref = dbProvider.get().accountDiffPreferences().get(getAccountId());
      if (diffPref == null) {
        diffPref = AccountDiffPreference.createDefault(getAccountId());
      }
    } catch (OrmException e) {
      log.warn("Cannot query account diff preferences", e);
      diffPref = AccountDiffPreference.createDefault(getAccountId());
    }
    return diffPref;
  }

  public Set<String> getEmailAddresses() {
    if (emailAddresses == null) {
      emailAddresses = state().getEmailAddresses();
    }
    return emailAddresses;
  }

  @Override
  public Set<AccountGroup.Id> getEffectiveGroups() {
    if (effectiveGroups == null) {
      Set<AccountGroup.Id> seedGroups;

      if (authConfig.isIdentityTrustable(state().getExternalIds())) {
        seedGroups = realm.groups(state());
      } else {
        seedGroups = authConfig.getRegisteredGroups();
      }

      effectiveGroups = getIncludedGroups(seedGroups);
    }

    return effectiveGroups;
  }

  private Set<AccountGroup.Id> getIncludedGroups(Set<AccountGroup.Id> seedGroups)
  {
    Set<AccountGroup.Id> includes = new HashSet<AccountGroup.Id> (seedGroups);
    Queue<AccountGroup.Id> groupQueue = new LinkedList<AccountGroup.Id> (seedGroups);

    while (groupQueue.size() > 0) {
      AccountGroup.Id id = groupQueue.remove();

      for (final AccountGroup.Id groupId : groupIncludeCache.getByInclude(id)) {
        if (includes.add(groupId)) {
          groupQueue.add(groupId);
        }
      }
    }

    return Collections.unmodifiableSet(includes);
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    if (starredChanges == null) {
      if (dbProvider == null) {
        throw new OutOfScopeException("Not in request scoped user");
      }
      final Set<Change.Id> h = new HashSet<Change.Id>();
      try {
        for (final StarredChange sc : dbProvider.get().starredChanges()
            .byAccount(getAccountId())) {
          h.add(sc.getChangeId());
        }
      } catch (OrmException e) {
        log.warn("Cannot query starred by user changes", e);
      }
      starredChanges = Collections.unmodifiableSet(h);
    }
    return starredChanges;
  }

  @Override
  public Collection<AccountProjectWatch> getNotificationFilters() {
    if (notificationFilters == null) {
      if (dbProvider == null) {
        throw new OutOfScopeException("Not in request scoped user");
      }
      List<AccountProjectWatch> r;
      try {
        r = dbProvider.get().accountProjectWatches() //
            .byAccount(getAccountId()).toList();
      } catch (OrmException e) {
        log.warn("Cannot query notification filters of a user", e);
        r = Collections.emptyList();
      }
      notificationFilters = Collections.unmodifiableList(r);
    }
    return notificationFilters;
  }

  public PersonIdent newRefLogIdent() {
    return newRefLogIdent(new Date(), TimeZone.getDefault());
  }

  public PersonIdent newRefLogIdent(final Date when, final TimeZone tz) {
    final Account ua = getAccount();

    String name = ua.getFullName();
    if (name == null || name.isEmpty()) {
      name = ua.getPreferredEmail();
    }
    if (name == null || name.isEmpty()) {
      name = "Anonymous Coward";
    }

    String user = getUserName();
    if (user == null) {
      user = "";
    }
    user = user + "|" + "account-" + ua.getId().toString();

    String host = null;
    if (remotePeerProvider != null) {
      final SocketAddress remotePeer = remotePeerProvider.get();
      if (remotePeer instanceof InetSocketAddress) {
        final InetSocketAddress sa = (InetSocketAddress) remotePeer;
        final InetAddress in = sa.getAddress();

        host = in != null ? in.getCanonicalHostName() : sa.getHostName();
      }
    }
    if (host == null || host.isEmpty()) {
      host = "unknown";
    }

    return new PersonIdent(name, user + "@" + host, when, tz);
  }

  public PersonIdent newCommitterIdent(final Date when, final TimeZone tz) {
    final Account ua = getAccount();
    String name = ua.getFullName();
    String email = ua.getPreferredEmail();

    if (email == null || email.isEmpty()) {
      // No preferred email is configured. Use a generic identity so we
      // don't leak an address the user may have given us, but doesn't
      // necessarily want to publish through Git records.
      //
      String user = getUserName();
      if (user == null || user.isEmpty()) {
        user = "account-" + ua.getId().toString();
      }

      String host;
      if (canonicalUrl.get() != null) {
        try {
          host = new URL(canonicalUrl.get()).getHost();
        } catch (MalformedURLException e) {
          host = SystemReader.getInstance().getHostname();
        }
      } else {
        host = SystemReader.getInstance().getHostname();
      }

      email = user + "@" + host;
    }

    if (name == null || name.isEmpty()) {
      final int at = email.indexOf('@');
      if (0 < at) {
        name = email.substring(0, at);
      } else {
        name = "Anonymous Coward";
      }
    }

    return new PersonIdent(name, email, when, tz);
  }

  @Override
  public String toString() {
    return "IdentifiedUser[account " + getAccountId() + "]";
  }
}
