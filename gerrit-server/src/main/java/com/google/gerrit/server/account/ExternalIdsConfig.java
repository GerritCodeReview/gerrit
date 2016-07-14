// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * ‘external-ids.config’ file in the user branch in the All-Users repository
 * that contains the external IDs of the user.
 * <p>
 * The 'external-ids.config' file is a git config file that has one 'scheme'
 * section for each scheme for which the user has any external ID. The scheme
 * name is used as subsection name and the external IDs are represented as 'id'
 * values in the subsection. An 'id' value is formatted as
 * "ID <EMAIL> : PASSWORD", where 'EMAIL' and "PASSWORD are optional. E.g.:
 * *
 * <pre>
 *   [scheme "gerrit"]
 *     id = jdoe
 *   [scheme "mailto"]
 *     id = jdoe@example.com <jdoe@example.com>
 *     id = john.doe@example.com <john.doe@example.com>
 *   [scheme "username"]
 *     id = jdoe : my-secret-password
 * </pre>
 */
public class ExternalIdsConfig extends VersionedMetaData
    implements AutoCloseable {
  @Singleton
  public static class Accessor {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;
    private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Accessor(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        Provider<MetaDataUpdate.User> metaDataUpdateFactory,
        IdentifiedUser.GenericFactory userFactory) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.metaDataUpdateFactory = metaDataUpdateFactory;
      this.userFactory = userFactory;
    }

    public Multimap<String, ExternalId> getExternalIds(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      try (Repository git = repoManager.openRepository(allUsersName);
          ExternalIdsConfig externalIdsConfig =
              new ExternalIdsConfig(accountId)) {
        externalIdsConfig.load(git);
        return externalIdsConfig.getExternalIds();
      }
    }

    public void upsertExternalIds(Account.Id accountId,
        Collection<ExternalId> newExternalIds)
            throws IOException, ConfigInvalidException {
      try (ExternalIdsConfig externalIdsConfig = open(accountId)) {
        Multimap<String, ExternalId> externalIds =
            externalIdsConfig.getExternalIds();
        for (ExternalId externalId : newExternalIds) {
          externalIds.put(externalId.scheme(), externalId);
        }
        commit(externalIdsConfig);
      }
    }

    public void deleteExternalIds(Account.Id accountId,
        Collection<ExternalId> externalIdsToDelete)
            throws IOException, ConfigInvalidException {
      try (ExternalIdsConfig externalIdsConfig = open(accountId)) {
        Multimap<String, ExternalId> externalIds =
            externalIdsConfig.getExternalIds();
        boolean commit = false;
        for (ExternalId externalId : externalIdsToDelete) {
          if (externalIds.remove(externalId.scheme(), externalId)) {
            commit = true;
          }
        }
        if (commit) {
          commit(externalIdsConfig);
        }
      }
    }

    private ExternalIdsConfig open(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      Repository git = repoManager.openRepository(allUsersName);
      ExternalIdsConfig externalIdsConfig = new ExternalIdsConfig(accountId);
      externalIdsConfig.load(git);
      return externalIdsConfig;
    }

    private void commit(ExternalIdsConfig externalIdsConfig)
        throws IOException {
      try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName,
          userFactory.create(externalIdsConfig.accountId))) {
        externalIdsConfig.commit(md);
      }
    }
  }

  @AutoValue
  public abstract static class ExternalId {
    public static ExternalId create(String scheme, String id,
        @Nullable String email, @Nullable String password) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme, id, email,
          password);
    }

    static ExternalId create(String scheme, ExternalIdValue externalIdValue) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme,
          externalIdValue.id(), externalIdValue.email(),
          externalIdValue.password());
    }

    public abstract String scheme();
    public abstract String id();
    public abstract @Nullable String email();
    public abstract @Nullable String password();
  }

  /**
   * Scheme used for {@link AuthType#LDAP}, {@link AuthType#CLIENT_SSL_CERT_LDAP},
   * {@link AuthType#HTTP_LDAP}, and {@link AuthType#LDAP_BIND} usernames.
   * <p>
   * The name {@code gerrit:} was a very poor choice.
   */
  public static final String SCHEME_GERRIT = "gerrit";

  /** Scheme used for randomly created identities constructed by a UUID. */
  public static final String SCHEME_UUID = "uuid";

  /** Scheme used to represent only an email address. */
  public static final String SCHEME_MAILTO = "mailto";

  /** Scheme for the username used to authenticate an account, e.g. over SSH. */
  public static final String SCHEME_USERNAME = "username";

  /** Scheme used for GPG public keys. */
  public static final String SCHEME_GPGKEY = "gpgkey";

  /** Scheme for external auth used during authentication, e.g. OAuth Token */
  public static final String SCHEME_EXTERNAL = "external";

  public static final ImmutableSet<String> SCHEMES =
      ImmutableSet.of(SCHEME_EXTERNAL, SCHEME_GERRIT, SCHEME_GPGKEY,
          SCHEME_MAILTO, SCHEME_USERNAME, SCHEME_UUID);

  private static final String EXTERNAL_IDS_CONFIG = "external-ids.config";
  private static final String SCHEME = "scheme";
  private static final String KEY_ID = "id";

  private final Account.Id accountId;
  private final String ref;

  private Repository git;
  private Multimap<String, ExternalId> externalIds;

  public ExternalIdsConfig(Account.Id accountId) {
    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  public void load(Repository git) throws IOException, ConfigInvalidException {
    checkState(this.git == null);
    this.git = git;
    super.load(git);
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    Config cfg = readConfig(EXTERNAL_IDS_CONFIG);
    externalIds = parse(accountId, cfg);
  }

  @VisibleForTesting
  public static Multimap<String, ExternalId> parse(Account.Id accountId,
      Config cfg) throws ConfigInvalidException {
    Multimap<String, ExternalId> externalIds = ArrayListMultimap.create();
    for (String schemeValue : cfg.getSubsections(SCHEME)) {
      String scheme = schemeValue.toLowerCase();
      if (!SCHEMES.contains(scheme)) {
        throw new ConfigInvalidException(
            String.format("Invalid scheme %s in external ids of account %d",
                scheme, accountId.get()));
      }
      String[] idValues = cfg.getStringList(SCHEME, schemeValue, KEY_ID);
      for (String idValue : idValues) {
        if (!Strings.isNullOrEmpty(idValue)) {
          externalIds.put(scheme,
              ExternalId.create(scheme, ExternalIdValue.parse(idValue)));
        }
      }
    }
    return externalIds;
  }

  Multimap<String, ExternalId> getExternalIds() {
    checkLoaded();
    return externalIds;
  }

  @Override
  protected boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException {
    checkLoaded();

    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated watch configuration\n");
    }

    Config cfg = readConfig(EXTERNAL_IDS_CONFIG);

    for (String projectName : cfg.getSubsections(SCHEME)) {
      cfg.unset(SCHEME, projectName, KEY_ID);
    }

    for (Map.Entry<String, Collection<ExternalId>> e : externalIds.asMap()
        .entrySet()) {
      cfg.setStringList(SCHEME, e.getKey(), KEY_ID,
          FluentIterable.from(e.getValue())
              .transform(Functions.toStringFunction())
              .toList());
    }

    saveConfig(EXTERNAL_IDS_CONFIG, cfg);
    return true;
  }

  @Override
  public void close() {
    if (git != null) {
      git.close();
    }
  }

  private void checkLoaded() {
    checkState(externalIds != null, "external IDs not loaded yet");
  }

  @AutoValue
  public abstract static class ExternalIdValue {
    public static ExternalIdValue create(String id, @Nullable String email,
        @Nullable String password) {
      return new AutoValue_ExternalIdsConfig_ExternalIdValue(id, email, password);
    }

    public static ExternalIdValue parse(String externalIdValue) {
      externalIdValue = externalIdValue.trim();

      String id = null;
      String email = null;
      String password = null;

      int i = externalIdValue.indexOf(" <");
      int j = externalIdValue.indexOf('>');
      if (i > 0 && i < j) {
        id = externalIdValue.substring(0, i);
        email = Strings.emptyToNull(externalIdValue.substring(i + 2, j));
      }

      int k = externalIdValue.indexOf(" : ");
      if (k > Math.max(0, j)) {
        if (id == null) {
          id = externalIdValue.substring(0, k);
        }
        password = Strings.emptyToNull(externalIdValue.substring(k + 3));
      }

      if (id == null) {
        id = externalIdValue;
      }

      return create(id, email, password);
    }

    public abstract String id();
    public abstract @Nullable String email();
    public abstract @Nullable String password();

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(id());
      if (email() != null) {
        b.append(" <").append(email()).append(">");
      }
      if (password() != null) {
        b.append(" : ").append(password());
      }
      return b.toString();
    }
  }
}
