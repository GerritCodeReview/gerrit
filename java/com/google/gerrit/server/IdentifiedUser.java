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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.flogger.LazyArgs.lazy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupMembership;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.EnablePeerIPInReflogRecord;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
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
    private final RefLogIdentityProvider refLogIdentityProvider;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean enablePeerIPInReflogRecord;

    @Inject
    public GenericFactory(
        AuthConfig authConfig,
        Realm realm,
        @AnonymousCowardName String anonymousCowardName,
        RefLogIdentityProvider refLogIdentityProvider,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        @EnablePeerIPInReflogRecord Boolean enablePeerIPInReflogRecord,
        AccountCache accountCache,
        GroupBackend groupBackend) {
      this.authConfig = authConfig;
      this.realm = realm;
      this.anonymousCowardName = anonymousCowardName;
      this.refLogIdentityProvider = refLogIdentityProvider;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.enablePeerIPInReflogRecord = enablePeerIPInReflogRecord;
    }

    public IdentifiedUser create(AccountState state) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          refLogIdentityProvider,
          canonicalUrl,
          accountCache,
          groupBackend,
          enablePeerIPInReflogRecord,
          Providers.of(null),
          state,
          /* realUser= */ null);
    }

    public IdentifiedUser create(Account.Id id) {
      return create(/* remotePeer= */ null, id);
    }

    @VisibleForTesting
    @UsedAt(UsedAt.Project.GOOGLE)
    public IdentifiedUser forTest(Account.Id id, PropertyMap properties) {
      return runAs(/* remotePeer= */ null, id, /* caller= */ null, properties);
    }

    public IdentifiedUser create(@Nullable SocketAddress remotePeer, Account.Id id) {
      return runAs(remotePeer, id, /* caller= */ null);
    }

    public IdentifiedUser runAs(
        @Nullable SocketAddress remotePeer, Account.Id id, @Nullable CurrentUser caller) {
      return runAs(remotePeer, id, caller, PropertyMap.EMPTY);
    }

    private IdentifiedUser runAs(
        @Nullable SocketAddress remotePeer,
        Account.Id id,
        @Nullable CurrentUser caller,
        PropertyMap properties) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          refLogIdentityProvider,
          canonicalUrl,
          accountCache,
          groupBackend,
          enablePeerIPInReflogRecord,
          Providers.of(remotePeer),
          id,
          caller,
          properties);
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
    private final RefLogIdentityProvider refLogIdentityProvider;
    private final Provider<String> canonicalUrl;
    private final AccountCache accountCache;
    private final GroupBackend groupBackend;
    private final Boolean enablePeerIPInReflogRecord;
    private final Provider<SocketAddress> remotePeerProvider;

    @Inject
    RequestFactory(
        AuthConfig authConfig,
        Realm realm,
        @AnonymousCowardName String anonymousCowardName,
        RefLogIdentityProvider refLogIdentityProvider,
        @CanonicalWebUrl Provider<String> canonicalUrl,
        AccountCache accountCache,
        GroupBackend groupBackend,
        @EnablePeerIPInReflogRecord Boolean enablePeerIPInReflogRecord,
        @RemotePeer Provider<SocketAddress> remotePeerProvider) {
      this.authConfig = authConfig;
      this.realm = realm;
      this.anonymousCowardName = anonymousCowardName;
      this.refLogIdentityProvider = refLogIdentityProvider;
      this.canonicalUrl = canonicalUrl;
      this.accountCache = accountCache;
      this.groupBackend = groupBackend;
      this.enablePeerIPInReflogRecord = enablePeerIPInReflogRecord;
      this.remotePeerProvider = remotePeerProvider;
    }

    public IdentifiedUser create(Account.Id id) {
      return create(id, PropertyMap.EMPTY);
    }

    public <T> IdentifiedUser create(Account.Id id, PropertyMap properties) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          refLogIdentityProvider,
          canonicalUrl,
          accountCache,
          groupBackend,
          enablePeerIPInReflogRecord,
          remotePeerProvider,
          id,
          null,
          properties);
    }

    public IdentifiedUser runAs(Account.Id id, CurrentUser caller, PropertyMap properties) {
      return new IdentifiedUser(
          authConfig,
          realm,
          anonymousCowardName,
          refLogIdentityProvider,
          canonicalUrl,
          accountCache,
          groupBackend,
          enablePeerIPInReflogRecord,
          remotePeerProvider,
          id,
          caller,
          properties);
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
  private final RefLogIdentityProvider refLogIdentityProvider;
  private final Boolean enablePeerIPInReflogRecord;
  private final Set<String> validEmails = Sets.newTreeSet(String.CASE_INSENSITIVE_ORDER);
  private final CurrentUser realUser; // Must be final since cached properties depend on it.

  private final Provider<SocketAddress> remotePeerProvider;
  private final Account.Id accountId;

  private AccountState state;
  private boolean loadedAllEmails;
  private Set<String> invalidEmails;
  private GroupMembership effectiveGroups;
  private PersonIdent refLogIdent;

  private IdentifiedUser(
      AuthConfig authConfig,
      Realm realm,
      String anonymousCowardName,
      RefLogIdentityProvider refLogIdentityProvider,
      Provider<String> canonicalUrl,
      AccountCache accountCache,
      GroupBackend groupBackend,
      Boolean enablePeerIPInReflogRecord,
      Provider<SocketAddress> remotePeerProvider,
      AccountState state,
      @Nullable CurrentUser realUser) {
    this(
        authConfig,
        realm,
        anonymousCowardName,
        refLogIdentityProvider,
        canonicalUrl,
        accountCache,
        groupBackend,
        enablePeerIPInReflogRecord,
        remotePeerProvider,
        state.account().id(),
        realUser,
        PropertyMap.EMPTY);
    this.state = state;
  }

  private IdentifiedUser(
      AuthConfig authConfig,
      Realm realm,
      String anonymousCowardName,
      RefLogIdentityProvider refLogIdentityProvider,
      Provider<String> canonicalUrl,
      AccountCache accountCache,
      GroupBackend groupBackend,
      Boolean enablePeerIPInReflogRecord,
      Provider<SocketAddress> remotePeerProvider,
      Account.Id id,
      @Nullable CurrentUser realUser,
      PropertyMap properties) {
    super(properties);
    this.canonicalUrl = canonicalUrl;
    this.accountCache = accountCache;
    this.groupBackend = groupBackend;
    this.authConfig = authConfig;
    this.realm = realm;
    this.anonymousCowardName = anonymousCowardName;
    this.refLogIdentityProvider = refLogIdentityProvider;
    this.enablePeerIPInReflogRecord = enablePeerIPInReflogRecord;
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
   * Returns the user's user name; null if one has not been selected/assigned or if the user name is
   * empty.
   */
  @Override
  public Optional<String> getUserName() {
    return state().userName();
  }

  /** Returns unique name of the user for logging, never {@code null} */
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

  @Override
  public ImmutableSet<String> getEmailAddresses() {
    if (!loadedAllEmails) {
      validEmails.addAll(realm.getEmailAddresses(this));
      loadedAllEmails = true;
    }
    return ImmutableSet.copyOf(validEmails);
  }

  @Override
  public ImmutableSet<ExternalId.Key> getExternalIdKeys() {
    return state().externalIds().stream().map(ExternalId::key).collect(toImmutableSet());
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

  @Nullable
  public SocketAddress getRemotePeer() {
    try {
      return remotePeerProvider.get();
    } catch (OutOfScopeException | ProvisionException e) {
      return null;
    }
  }

  public PersonIdent newRefLogIdent() {
    return refLogIdentityProvider.newRefLogIdent(this);
  }

  public PersonIdent newRefLogIdent(Instant when, ZoneId zoneId) {
    if (refLogIdent != null) {
      refLogIdent =
          new PersonIdent(refLogIdent.getName(), refLogIdent.getEmailAddress(), when, zoneId);
      return refLogIdent;
    }
    refLogIdent = refLogIdentityProvider.newRefLogIdent(this, when, zoneId);
    return refLogIdent;
  }

  public PersonIdent newCommitterIdent(PersonIdent ident) {
    return newCommitterIdent(ident.getWhenAsInstant(), ident.getZoneId());
  }

  protected String getGenericEmail(Account ua) {
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

    return user + "@" + host;
  }

  protected String getCommitterName(Account ua, String email) {
    String name = ua.fullName();
    if (name == null || name.isEmpty()) {
      final int at = email.indexOf('@');
      if (0 < at) {
        name = email.substring(0, at);
      } else {
        name = anonymousCowardName;
      }
    }
    return name;
  }

  public PersonIdent newCommitterIdent(Instant when, ZoneId zoneId) {
    final Account ua = getAccount();
    String email = ua.preferredEmail();
    if (email == null || email.isEmpty()) {
      email = getGenericEmail(ua);
    }
    String name = getCommitterName(ua, email);
    return new PersonIdent(name, email, when, zoneId);
  }

  public Optional<PersonIdent> getOptionalCommitterIdent(
      String email, Instant when, ZoneId zoneId) {
    if (!hasEmailAddress(email)) {
      return Optional.empty();
    }
    String name = getCommitterName(getAccount(), email);
    return Optional.of(new PersonIdent(name, email, when, zoneId));
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
        refLogIdentityProvider,
        Providers.of(canonicalUrl.get()),
        accountCache,
        groupBackend,
        enablePeerIPInReflogRecord,
        remotePeer,
        state,
        realUser);
  }

  @Override
  public boolean hasSameAccountId(CurrentUser other) {
    return getAccountId().get() == other.getAccountId().get();
  }
}
