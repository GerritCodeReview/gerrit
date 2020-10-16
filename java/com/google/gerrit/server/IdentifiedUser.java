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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.flogger.LazyArgs.lazy;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.EnableReverseDnsLookup;
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
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.SystemReader;

/** An authenticated user. */
public class IdentifiedUser extends CurrentUser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Create an IdentifiedUser, ignoring any per-request state. */
  @Singleton
  public static class GenericFactory {
    private final AuthConfig authConfig;
    private final Realm realm;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean enableReverseDnsLookup;

    @Inject
    public GenericFactory(
        AuthConfig authConfig,
        Realm realm,
        @AnonymousCowardName String anonymousCowardName,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        @EnableReverseDnsLookup Boolean enableReverseDnsLookup,
        AccountCache accountCache,
        GroupBackend groupBackend) {
      this.authConfig = authConfig;
      this.realm = realm;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.enableReverseDnsLookup = enableReverseDnsLookup;
    }

    public IdentifiedUser create(AccountState state) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          canonicalUrl,
          accountCache,
          groupBackend,
          enableReverseDnsLookup,
          Providers.of(null),
          state,
          null);
    }

    public IdentifiedUser create(Account.Id id) {
      return create(null, id);
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
          enableReverseDnsLookup,
          Providers.of(remotePeer),
          id,
          caller);
    }
  }

  /**
   * Create an IdentifiedUser, relying on current request state.
   *
   * <p>Can only be used from within a module that has defined a request scoped {@code @RemotePeer
   * SocketAddress} provider.
   */
  @Singleton
  public static class RequestFactory {
    private final AuthConfig authConfig;
    private final Realm realm;
    private final String anonymousCowardName;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean enableReverseDnsLookup;
    private final Provider<SocketAddress> remotePeerProvider;

    @Inject
    RequestFactory(
        AuthConfig authConfig,
        Realm realm,
        @AnonymousCowardName String anonymousCowardName,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        AccountCache accountCache,
        GroupBackend groupBackend,
        @EnableReverseDnsLookup Boolean enableReverseDnsLookup,
        @RemotePeer Provider<SocketAddress> remotePeerProvider) {
      this.authConfig = authConfig;
      this.realm = realm;
      this.anonymousCowardName = anonymousCowardName;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.enableReverseDnsLookup = enableReverseDnsLookup;
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
          enableReverseDnsLookup,
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
          enableReverseDnsLookup,
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
  private final Boolean enableReverseDnsLookup;
  private final Set<String> validEmails = Sets.newTreeSet(String.CASE_INSENSITIVE_ORDER);
  private final CurrentUser realUser; // Must be final since cached properties depend on it.

  private final Provider<SocketAddress> remotePeerProvider;
  private final Account.Id accountId;

  private AccountState state;
  private boolean loadedAllEmails;
  private Set<String> invalidEmails;
  private GroupMembership effectiveGroups;
  private Map<PropertyKey<Object>, Object> properties;

  private IdentifiedUser(
      AuthConfig authConfig,
      Realm realm,
      String anonymousCowardName,
      Provider<String> canonicalUrl,
      AccountCache accountCache,
      GroupBackend groupBackend,
      Boolean enableReverseDnsLookup,
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
        enableReverseDnsLookup,
        remotePeerProvider,
        state.account().id(),
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
      Boolean enableReverseDnsLookup,
      @Nullable Provider<SocketAddress> remotePeerProvider,
      Account.Id id,
      @Nullable CurrentUser realUser) {
    this.canonicalUrl = canonicalUrl;
    this.accountCache = accountCache;
    this.groupBackend = groupBackend;
    this.authConfig = authConfig;
    this.realm = realm;
    this.anonymousCowardName = anonymousCowardName;
    this.enableReverseDnsLookup = enableReverseDnsLookup;
    this.remotePeerProvider = remotePeerProvider;
    this.accountId = id;
    this.realUser = realUser != null ? realUser : this;
  }

  @Override
  public CurrentUser getRealUser() {
    return realUser;
  }

  @Override
  public boolean isImpersonating() {
    if (realUser == this) {
      return false;
    }
    if (realUser.isIdentifiedUser()) {
      if (realUser.getAccountId().equals(getAccountId())) {
        // Impersonating another copy of this user is allowed.
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the account state of the identified user.
   *
   * @return the account state of the identified user, an empty account state if the account is
   *     missing
   */
  public AccountState state() {
    if (state == null) {
      // TODO(ekempin):
      // Ideally we would only create IdentifiedUser instances for existing accounts. To ensure
      // this we could load the account state eagerly on the creation of IdentifiedUser and fail is
      // the account is missing. In most cases, e.g. when creating an IdentifiedUser for a request
      // context, we really want to fail early if the account is missing. However there are some
      // usages where an IdentifiedUser may be instantiated for a missing account. We may go
      // through all of them and ensure that they never try to create an IdentifiedUser for a
      // missing account or make this explicit by adding a createEvenIfMissing method to
      // IdentifiedUser.GenericFactory. However since this is a lot of effort we stick with calling
      // AccountCache#getEvenIfMissing(Account.Id) for now.
      // Alternatively we could be could also return an Optional<AccountState> from the state()
      // method and let callers handle the missing account case explicitly. But this would be a lot
      // of work too.
      state = accountCache.getEvenIfMissing(getAccountId());
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

  /**
   * @return the user's user name; null if one has not been selected/assigned or if the user name is
   *     empty.
   */
  @Override
  public Optional<String> getUserName() {
    return state().userName();
  }

  /** @return unique name of the user for logging, never {@code null} */
  @Override
  public String getLoggableName() {
    return getUserName()
        .orElseGet(() -> firstNonNull(getAccount().preferredEmail(), "a/" + getAccountId().get()));
  }

  /**
   * Returns the account of the identified user.
   *
   * @return the account of the identified user, an empty account if the account is missing
   */
  public Account getAccount() {
    return state().account();
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

  public ImmutableSet<String> getEmailAddresses() {
    if (!loadedAllEmails) {
      validEmails.addAll(realm.getEmailAddresses(this));
      loadedAllEmails = true;
    }
    return ImmutableSet.copyOf(validEmails);
  }

  public String getName() {
    return getAccount().getName();
  }

  public String getNameEmail() {
    return getAccount().getNameEmail(anonymousCowardName);
  }

  @Override
  public GroupMembership getEffectiveGroups() {
    if (effectiveGroups == null) {
      if (authConfig.isIdentityTrustable(state().externalIds())) {
        effectiveGroups = groupBackend.membershipsOf(this);
        logger.atFinest().log(
            "Known groups of %s: %s", getLoggableName(), lazy(effectiveGroups::getKnownGroups));
      } else {
        effectiveGroups = registeredGroups;
        logger.atFinest().log(
            "%s has a non-trusted identity, falling back to %s as known groups",
            getLoggableName(), lazy(registeredGroups::getKnownGroups));
      }
    }
    return effectiveGroups;
  }

  @Override
  public Object getCacheKey() {
    return getAccountId();
  }

  public PersonIdent newRefLogIdent() {
    return newRefLogIdent(new Date(), TimeZone.getDefault());
  }

  public PersonIdent newRefLogIdent(Date when, TimeZone tz) {
    final Account ua = getAccount();

    String name = ua.fullName();
    if (name == null || name.isEmpty()) {
      name = ua.preferredEmail();
    }
    if (name == null || name.isEmpty()) {
      name = anonymousCowardName;
    }

    String user = getUserName().orElse("") + "|account-" + ua.id().toString();
    return new PersonIdent(name, user + "@" + guessHost(), when, tz);
  }

  public PersonIdent newCommitterIdent(Date when, TimeZone tz) {
    final Account ua = getAccount();
    String name = ua.fullName();
    String email = ua.preferredEmail();

    if (email == null || email.isEmpty()) {
      // No preferred email is configured. Use a generic identity so we
      // don't leak an address the user may have given us, but doesn't
      // necessarily want to publish through Git records.
      //
      String user = getUserName().orElseGet(() -> "account-" + ua.id().toString());

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
  public synchronized <T> Optional<T> get(PropertyKey<T> key) {
    if (properties != null) {
      @SuppressWarnings("unchecked")
      T value = (T) properties.get(key);
      return Optional.ofNullable(value);
    }
    return Optional.empty();
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
          () -> {
            throw e;
          };
    }
    return new IdentifiedUser(
        authConfig,
        realm,
        anonymousCowardName,
        Providers.of(canonicalUrl.get()),
        accountCache,
        groupBackend,
        enableReverseDnsLookup,
        remotePeer,
        state,
        realUser);
  }

  @Override
  public boolean hasSameAccountId(CurrentUser other) {
    return getAccountId().get() == other.getAccountId().get();
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
    if (Boolean.TRUE.equals(enableReverseDnsLookup)) {
      return in.getCanonicalHostName();
    }
    return in.getHostAddress();
  }
}
