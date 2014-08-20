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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;

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
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/** An authenticated user. */
public class IdentifiedUser extends CurrentUser {
  /** Create an IdentifiedUser, ignoring any per-request state. */
  @Singleton
  public static class GenericFactory {
    private final CapabilityControl.Factory capabilityControlFactory;
    private final AuthConfig authConfig;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean disableReverseDnsLookup;

    @Inject
    public GenericFactory(
        @Nullable CapabilityControl.Factory capabilityControlFactory,
        AuthConfig authConfig,
        @AnonymousCowardName String anonymousCowardName,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        @DisableReverseDnsLookup Boolean disableReverseDnsLookup,
        AccountCache accountCache,
        GroupBackend groupBackend) {
      this.capabilityControlFactory = capabilityControlFactory;
      this.authConfig = authConfig;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.disableReverseDnsLookup = disableReverseDnsLookup;
    }

    public IdentifiedUser create(final Account.Id id) {
      return create((SocketAddress) null, id);
    }

    public IdentifiedUser create(Provider<ReviewDb> db, Account.Id id) {
      return new IdentifiedUser(capabilityControlFactory, authConfig,
          anonymousCowardName, canonicalUrl, accountCache, groupBackend,
          disableReverseDnsLookup, null, db, id, null);
    }

    public IdentifiedUser create(SocketAddress remotePeer, Account.Id id) {
      return new IdentifiedUser(capabilityControlFactory, authConfig,
          anonymousCowardName, canonicalUrl, accountCache, groupBackend,
          disableReverseDnsLookup, Providers.of(remotePeer), null, id, null);
    }

    public CurrentUser runAs(SocketAddress remotePeer, Account.Id id,
        @Nullable CurrentUser caller) {
      return new IdentifiedUser(capabilityControlFactory, authConfig,
          anonymousCowardName, canonicalUrl, accountCache, groupBackend,
          disableReverseDnsLookup, Providers.of(remotePeer), null, id, caller);
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
    private final CapabilityControl.Factory capabilityControlFactory;
    private final AuthConfig authConfig;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean disableReverseDnsLookup;

    private final Provider<SocketAddress> remotePeerProvider;
    private final Provider<ReviewDb> dbProvider;

    @Inject
    RequestFactory(
        CapabilityControl.Factory capabilityControlFactory,
        final AuthConfig authConfig,
        final @AnonymousCowardName String anonymousCowardName,
        final @CanonicalWebUrl Provider<String> canonicalUrl,
        final AccountCache accountCache,
        final GroupBackend groupBackend,
        final @DisableReverseDnsLookup Boolean disableReverseDnsLookup,

        final @RemotePeer Provider<SocketAddress> remotePeerProvider,
        final Provider<ReviewDb> dbProvider) {
      this.capabilityControlFactory = capabilityControlFactory;
      this.authConfig = authConfig;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.disableReverseDnsLookup = disableReverseDnsLookup;

      this.remotePeerProvider = remotePeerProvider;
      this.dbProvider = dbProvider;
    }

    public IdentifiedUser create(Account.Id id) {
      return new IdentifiedUser(capabilityControlFactory, authConfig,
          anonymousCowardName, canonicalUrl, accountCache, groupBackend,
          disableReverseDnsLookup, remotePeerProvider, dbProvider, id, null);
    }

    public IdentifiedUser runAs(Account.Id id, CurrentUser caller) {
      return new IdentifiedUser(capabilityControlFactory, authConfig,
          anonymousCowardName, canonicalUrl, accountCache, groupBackend,
          disableReverseDnsLookup, remotePeerProvider, dbProvider, id, caller);
    }
  }

  private static final Logger log =
      LoggerFactory.getLogger(IdentifiedUser.class);

  private static final GroupMembership registeredGroups =
      new ListGroupMembership(ImmutableSet.of(
          SystemGroupBackend.ANONYMOUS_USERS,
          SystemGroupBackend.REGISTERED_USERS));

  private final Provider<String> canonicalUrl;
  private final AccountCache accountCache;
  private final AuthConfig authConfig;
  private final GroupBackend groupBackend;
  private final String anonymousCowardName;
  private final Boolean disableReverseDnsLookup;

  @Nullable
  private final Provider<SocketAddress> remotePeerProvider;

  @Nullable
  private final Provider<ReviewDb> dbProvider;

  private final Account.Id accountId;

  private AccountState state;
  private Set<String> emailAddresses;
  private GroupMembership effectiveGroups;
  private Set<Change.Id> starredChanges;
  private ResultSet<StarredChange> starredQuery;
  private Collection<AccountProjectWatch> notificationFilters;
  private CurrentUser realUser;


  private IdentifiedUser(
      CapabilityControl.Factory capabilityControlFactory,
      final AuthConfig authConfig,
      final String anonymousCowardName,
      final Provider<String> canonicalUrl,
      final AccountCache accountCache,
      final GroupBackend groupBackend,
      final Boolean disableReverseDnsLookup,
      @Nullable final Provider<SocketAddress> remotePeerProvider,
      @Nullable final Provider<ReviewDb> dbProvider,
      final Account.Id id,
      @Nullable CurrentUser realUser) {
    super(capabilityControlFactory);
    this.canonicalUrl = canonicalUrl;
    this.accountCache = accountCache;
    this.groupBackend = groupBackend;
    this.authConfig = authConfig;
    this.anonymousCowardName = anonymousCowardName;
    this.disableReverseDnsLookup = disableReverseDnsLookup;
    this.remotePeerProvider = remotePeerProvider;
    this.dbProvider = dbProvider;
    this.accountId = id;
    this.realUser = realUser != null ? realUser : this;
  }

  @Override
  public CurrentUser getRealUser() {
    return realUser;
  }

  // TODO(cranger): maybe get the state through the accountCache instead.
  public AccountState state() {
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
  @Override
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

  public String getName() {
    return new AccountInfo(getAccount()).getName(anonymousCowardName);
  }

  public String getNameEmail() {
    return new AccountInfo(getAccount()).getNameEmail(anonymousCowardName);
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    if (effectiveGroups == null) {
      if (authConfig.isIdentityTrustable(state().getExternalIds())) {
        effectiveGroups = groupBackend.membershipsOf(this);
      } else {
        effectiveGroups = registeredGroups;
      }
    }
    return effectiveGroups;
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    if (starredChanges == null) {
      if (dbProvider == null) {
        throw new OutOfScopeException("Not in request scoped user");
      }
      Set<Change.Id> h = Sets.newHashSet();
      try {
        if (starredQuery != null) {
          for (StarredChange sc : starredQuery) {
            h.add(sc.getChangeId());
          }
          starredQuery = null;
        } else {
          for (StarredChange sc : dbProvider.get().starredChanges()
              .byAccount(getAccountId())) {
            h.add(sc.getChangeId());
          }
        }
      } catch (OrmException e) {
        log.warn("Cannot query starred by user changes", e);
      }
      starredChanges = Collections.unmodifiableSet(h);
    }
    return starredChanges;
  }

  public void asyncStarredChanges() {
    if (starredChanges == null && dbProvider != null) {
      try {
        starredQuery =
            dbProvider.get().starredChanges().byAccount(getAccountId());
      } catch (OrmException e) {
        log.warn("Cannot query starred by user changes", e);
        starredQuery = null;
        starredChanges = Collections.emptySet();
      }
    }
  }

  public void abortStarredChanges() {
    if (starredQuery != null) {
      try {
        starredQuery.close();
      } finally {
        starredQuery = null;
      }
    }
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
      name = anonymousCowardName;
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

        host = in != null ? getHost(in) : sa.getHostName();
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
        name = anonymousCowardName;
      }
    }

    return new PersonIdent(name, email, when, tz);
  }

  @Override
  public String toString() {
    return "IdentifiedUser[account " + getAccountId() + "]";
  }

  /** Check if user is the IdentifiedUser */
  @Override
  public boolean isIdentifiedUser() {
    return true;
  }

  private String getHost(final InetAddress in) {
    if (Boolean.FALSE.equals(disableReverseDnsLookup)) {
      return in.getCanonicalHostName();
    }
    return in.getHostAddress();
  }
}
