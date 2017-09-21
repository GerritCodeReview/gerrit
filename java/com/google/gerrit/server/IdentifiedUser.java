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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.DisableReverseDnsLookup;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;

/** An authenticated user. */
public class IdentifiedUser extends CurrentUser {
  /** Create an IdentifiedUser, ignoring any per-request state. */
  @Singleton
  public static class GenericFactory {
    private final AuthConfig authConfig;
    private final Realm realm;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean disableReverseDnsLookup;

    @Inject
    public GenericFactory(
        AuthConfig authConfig,
        Realm realm,
        @AnonymousCowardName String anonymousCowardName,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        @DisableReverseDnsLookup Boolean disableReverseDnsLookup,
        AccountCache accountCache,
        GroupBackend groupBackend) {
      this.authConfig = authConfig;
      this.realm = realm;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.disableReverseDnsLookup = disableReverseDnsLookup;
    }

    public IdentifiedUser create(AccountState state) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          canonicalUrl,
          accountCache,
          groupBackend,
          disableReverseDnsLookup,
          Providers.of((SocketAddress) null),
          state,
          null);
    }

    public IdentifiedUser create(Account.Id id) {
      return create((SocketAddress) null, id);
    }

    public IdentifiedUser create(SocketAddress remotePeer, Account.Id id) {
      return runAs(remotePeer, id, null);
    }

    public IdentifiedUser runAs(
        SocketAddress remotePeer, Account.Id id, @Nullable CurrentUser caller) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          canonicalUrl,
          accountCache,
          groupBackend,
          disableReverseDnsLookup,
          Providers.of(remotePeer),
          id,
          caller);
    }
  }

  /**
   * Create an IdentifiedUser, relying on current request state.
   *
   * <p>Can only be used from within a module that has defined request scoped {@code @RemotePeer
   * SocketAddress} and {@code ReviewDb} providers.
   */
  @Singleton
  public static class RequestFactory {
    private final AuthConfig authConfig;
    private final Realm realm;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean disableReverseDnsLookup;
    private final Provider<SocketAddress> remotePeerProvider;

    @Inject
    RequestFactory(
        AuthConfig authConfig,
        Realm realm,
        @AnonymousCowardName String anonymousCowardName,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        AccountCache accountCache,
        GroupBackend groupBackend,
        @DisableReverseDnsLookup Boolean disableReverseDnsLookup,
        @RemotePeer Provider<SocketAddress> remotePeerProvider) {
      this.authConfig = authConfig;
      this.realm = realm;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.disableReverseDnsLookup = disableReverseDnsLookup;
      this.remotePeerProvider = remotePeerProvider;
    }

    public IdentifiedUser create(Account.Id id) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          canonicalUrl,
          accountCache,
          groupBackend,
          disableReverseDnsLookup,
          remotePeerProvider,
          id,
          null);
    }

    public IdentifiedUser runAs(Account.Id id, CurrentUser caller) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          canonicalUrl,
          accountCache,
          groupBackend,
          disableReverseDnsLookup,
          remotePeerProvider,
          id,
          caller);
    }
  }

  private static final GroupMembership registeredGroups =
      new ListGroupMembership(
          ImmutableSet.of(SystemGroupBackend.ANONYMOUS_USERS, SystemGroupBackend.REGISTERED_USERS));

  private final Provider<String> canonicalUrl;
  private final AccountCache accountCache;
  private final AuthConfig authConfig;
  private final Realm realm;
  private final GroupBackend groupBackend;
  private final String anonymousCowardName;
  private final Boolean disableReverseDnsLookup;
  private final Set<String> validEmails = Sets.newTreeSet(String.CASE_INSENSITIVE_ORDER);

  private final Provider<SocketAddress> remotePeerProvider;
  private final Account.Id accountId;

  private AccountState state;
  private boolean loadedAllEmails;
  private Set<String> invalidEmails;
  private GroupMembership effectiveGroups;
  private CurrentUser realUser;
  private Map<PropertyKey<Object>, Object> properties;

  private IdentifiedUser(
      AuthConfig authConfig,
      Realm realm,
      String anonymousCowardName,
      Provider<String> canonicalUrl,
      AccountCache accountCache,
      GroupBackend groupBackend,
      Boolean disableReverseDnsLookup,
      @Nullable Provider<SocketAddress> remotePeerProvider,
      AccountState state,
      @Nullable CurrentUser realUser) {
    this(
        authConfig,
        realm,
        anonymousCowardName,
        canonicalUrl,
        accountCache,
        groupBackend,
        disableReverseDnsLookup,
        remotePeerProvider,
        state.getAccount().getId(),
        realUser);
    this.state = state;
  }

  private IdentifiedUser(
      AuthConfig authConfig,
      Realm realm,
      String anonymousCowardName,
      Provider<String> canonicalUrl,
      AccountCache accountCache,
      GroupBackend groupBackend,
      Boolean disableReverseDnsLookup,
      @Nullable Provider<SocketAddress> remotePeerProvider,
      Account.Id id,
      @Nullable CurrentUser realUser) {
    this.canonicalUrl = canonicalUrl;
    this.accountCache = accountCache;
    this.groupBackend = groupBackend;
    this.authConfig = authConfig;
    this.realm = realm;
    this.anonymousCowardName = anonymousCowardName;
    this.disableReverseDnsLookup = disableReverseDnsLookup;
    this.remotePeerProvider = remotePeerProvider;
    this.accountId = id;
    this.realUser = realUser != null ? realUser : this;
  }

  @Override
  public CurrentUser getRealUser() {
    return realUser;
  }

  public AccountState state() {
    if (state == null) {
      state = accountCache.get(getAccountId());
    }
    return state;
  }

  @Override
  public IdentifiedUser asIdentifiedUser() {
    return this;
  }

  @Override
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

  public boolean hasEmailAddress(String email) {
    if (validEmails.contains(email)) {
      return true;
    } else if (invalidEmails != null && invalidEmails.contains(email)) {
      return false;
    } else if (realm.hasEmailAddress(this, email)) {
      validEmails.add(email);
      return true;
    } else if (invalidEmails == null) {
      invalidEmails = Sets.newTreeSet(String.CASE_INSENSITIVE_ORDER);
    }
    invalidEmails.add(email);
    return false;
  }

  public Set<String> getEmailAddresses() {
    if (!loadedAllEmails) {
      validEmails.addAll(realm.getEmailAddresses(this));
      loadedAllEmails = true;
    }
    return validEmails;
  }

  public String getName() {
    return getAccount().getName(anonymousCowardName);
  }

  public String getNameEmail() {
    return getAccount().getNameEmail(anonymousCowardName);
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

  public PersonIdent newRefLogIdent() {
    return newRefLogIdent(new Date(), TimeZone.getDefault());
  }

  public PersonIdent newRefLogIdent(Date when, TimeZone tz) {
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
    user = user + "|account-" + ua.getId().toString();

    return new PersonIdent(name, user + "@" + guessHost(), when, tz);
  }

  public PersonIdent newCommitterIdent(Date when, TimeZone tz) {
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

  @Override
  @Nullable
  public synchronized <T> T get(PropertyKey<T> key) {
    if (properties != null) {
      @SuppressWarnings("unchecked")
      T value = (T) properties.get(key);
      return value;
    }
    return null;
  }

  /**
   * Store a property for later retrieval.
   *
   * @param key unique property key.
   * @param value value to store; or {@code null} to clear the value.
   */
  @Override
  public synchronized <T> void put(PropertyKey<T> key, @Nullable T value) {
    if (properties == null) {
      if (value == null) {
        return;
      }
      properties = new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    PropertyKey<Object> k = (PropertyKey<Object>) key;
    if (value != null) {
      properties.put(k, value);
    } else {
      properties.remove(k);
    }
  }

  /**
   * Returns a materialized copy of the user with all dependencies.
   *
   * <p>Invoke all providers and factories of dependent objects and store the references to a copy
   * of the current identified user.
   *
   * @return copy of the identified user
   */
  public IdentifiedUser materializedCopy() {
    Provider<SocketAddress> remotePeer;
    try {
      remotePeer = Providers.of(remotePeerProvider.get());
    } catch (OutOfScopeException | ProvisionException e) {
      remotePeer =
          new Provider<SocketAddress>() {
            @Override
            public SocketAddress get() {
              throw e;
            }
          };
    }
    return new IdentifiedUser(
        authConfig,
        realm,
        anonymousCowardName,
        Providers.of(canonicalUrl.get()),
        accountCache,
        groupBackend,
        disableReverseDnsLookup,
        remotePeer,
        state,
        realUser);
  }

  private String guessHost() {
    String host = null;
    SocketAddress remotePeer = null;
    try {
      remotePeer = remotePeerProvider.get();
    } catch (OutOfScopeException | ProvisionException e) {
      // Leave null.
    }
    if (remotePeer instanceof InetSocketAddress) {
      InetSocketAddress sa = (InetSocketAddress) remotePeer;
      InetAddress in = sa.getAddress();
      host = in != null ? getHost(in) : sa.getHostName();
    }
    if (Strings.isNullOrEmpty(host)) {
      return "unknown";
    }
    return host;
  }

  private String getHost(InetAddress in) {
    if (Boolean.FALSE.equals(disableReverseDnsLookup)) {
      return in.getCanonicalHostName();
    }
    return in.getHostAddress();
  }
}
