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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountProjectWatch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.StarredChange;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.MaterializedGroupMembership;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.server.OrmException;
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
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.annotation.Nullable;

/** An authenticated user. */
public class IdentifiedUser extends CurrentUser {
  /** Create an IdentifiedUser, ignoring any per-request state. */
  @Singleton
  public static class GenericFactory {
    private final CapabilityControl.Factory capabilityControlFactory;
    private final AuthConfig authConfig;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final Realm realm;
    private final AccountCache accountCache;
    private final MaterializedGroupMembership.Factory groupMembershipFactory;

    @Inject
    GenericFactory(
        CapabilityControl.Factory capabilityControlFactory,
        final AuthConfig authConfig,
        final @AnonymousCowardName String anonymousCowardName,
        final @CanonicalWebUrl Provider<String> canonicalUrl,
        final Realm realm, final AccountCache accountCache,
        final MaterializedGroupMembership.Factory groupMembershipFactory) {
      this.capabilityControlFactory = capabilityControlFactory;
      this.authConfig = authConfig;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.realm = realm;
      this.accountCache = accountCache;
      this.groupMembershipFactory = groupMembershipFactory;
    }

    public IdentifiedUser create(final Account.Id id) {
      return create(AccessPath.UNKNOWN, null, id);
    }

    public IdentifiedUser create(Provider<ReviewDb> db, Account.Id id) {
      return new IdentifiedUser(capabilityControlFactory, AccessPath.UNKNOWN,
          authConfig, anonymousCowardName, canonicalUrl, realm, accountCache,
          groupMembershipFactory, null, db, id);
    }

    public IdentifiedUser create(AccessPath accessPath,
        Provider<SocketAddress> remotePeerProvider, Account.Id id) {
      return new IdentifiedUser(capabilityControlFactory, accessPath,
          authConfig, anonymousCowardName, canonicalUrl, realm, accountCache,
          groupMembershipFactory, remotePeerProvider, null, id);
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
    private final Realm realm;
    private final AccountCache accountCache;
    private final MaterializedGroupMembership.Factory groupMembershipFactory;

    private final Provider<SocketAddress> remotePeerProvider;
    private final Provider<ReviewDb> dbProvider;

    @Inject
    RequestFactory(
        CapabilityControl.Factory capabilityControlFactory,
        final AuthConfig authConfig,
        final @AnonymousCowardName String anonymousCowardName,
        final @CanonicalWebUrl Provider<String> canonicalUrl,
        final Realm realm, final AccountCache accountCache,
        final MaterializedGroupMembership.Factory groupMembershipFactory,

        final @RemotePeer Provider<SocketAddress> remotePeerProvider,
        final Provider<ReviewDb> dbProvider) {
      this.capabilityControlFactory = capabilityControlFactory;
      this.authConfig = authConfig;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.realm = realm;
      this.accountCache = accountCache;
      this.groupMembershipFactory = groupMembershipFactory;

      this.remotePeerProvider = remotePeerProvider;
      this.dbProvider = dbProvider;
    }

    public IdentifiedUser create(final AccessPath accessPath,
        final Account.Id id) {
      return new IdentifiedUser(capabilityControlFactory, accessPath,
          authConfig, anonymousCowardName, canonicalUrl, realm, accountCache,
          groupMembershipFactory, remotePeerProvider, dbProvider, id);
    }
  }

  private static final Logger log =
      LoggerFactory.getLogger(IdentifiedUser.class);

  private static final Set<AccountGroup.UUID> registeredGroups =
      new AbstractSet<AccountGroup.UUID>() {
        private final List<AccountGroup.UUID> groups =
            Collections.unmodifiableList(Arrays.asList(new AccountGroup.UUID[] {
                AccountGroup.ANONYMOUS_USERS, AccountGroup.REGISTERED_USERS}));

        @Override
        public boolean contains(Object o) {
          return groups.contains(o);
        }

        @Override
        public Iterator<AccountGroup.UUID> iterator() {
          return groups.iterator();
        }

        @Override
        public int size() {
          return groups.size();
        }
      };

  private final Provider<String> canonicalUrl;
  private final Realm realm;
  private final AccountCache accountCache;
  private final MaterializedGroupMembership.Factory groupMembershipFactory;
  private final AuthConfig authConfig;
  private final String anonymousCowardName;

  @Nullable
  private final Provider<SocketAddress> remotePeerProvider;

  @Nullable
  private final Provider<ReviewDb> dbProvider;

  private final Account.Id accountId;

  private AccountState state;
  private Set<String> emailAddresses;
  private GroupMembership effectiveGroups;
  private Set<Change.Id> starredChanges;
  private Collection<AccountProjectWatch> notificationFilters;

  private IdentifiedUser(
      CapabilityControl.Factory capabilityControlFactory,
      final AccessPath accessPath,
      final AuthConfig authConfig,
      final String anonymousCowardName,
      final Provider<String> canonicalUrl,
      final Realm realm, final AccountCache accountCache,
      final MaterializedGroupMembership.Factory groupMembershipFactory,
      @Nullable final Provider<SocketAddress> remotePeerProvider,
      @Nullable final Provider<ReviewDb> dbProvider, final Account.Id id) {
    super(capabilityControlFactory, accessPath);
    this.canonicalUrl = canonicalUrl;
    this.realm = realm;
    this.accountCache = accountCache;
    this.groupMembershipFactory = groupMembershipFactory;
    this.authConfig = authConfig;
    this.anonymousCowardName = anonymousCowardName;
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

  @Override
  public GroupMembership getEffectiveGroups() {
    if (effectiveGroups == null) {
      if (authConfig.isIdentityTrustable(state().getExternalIds())) {
        effectiveGroups = realm.groups(state());
      } else {
        effectiveGroups = groupMembershipFactory.create(registeredGroups);
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
        name = anonymousCowardName;
      }
    }

    return new PersonIdent(name, email, when, tz);
  }

  @Override
  public String toString() {
    return "IdentifiedUser[account " + getAccountId() + "]";
  }
}
