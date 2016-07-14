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
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
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
import java.util.Collections;
import java.util.Map;

/**
 * ‘external-ids.config’ file in the user branch in the All-Users repository
 * that contains the external IDs of the user.
 * <p>
 * The 'external-ids.config' file is a git config file that has one 'scheme'
 * section for each scheme for which the user has any external ID. The scheme
 * name is used as subsection name and the external IDs are represented as 'id'
 * values in the subsection. An 'id' value is formatted as
 * "ID <EMAIL> : PASSWORD", where 'EMAIL' and 'PASSWORD' are optional. E.g.:
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
  public abstract static class Accessor {
    @Singleton
    public static class User extends Accessor {
      private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
      private final IdentifiedUser.GenericFactory userFactory;

      @Inject
      User(GitRepositoryManager repoManager,
          AllUsersName allUsersName,
          Provider<MetaDataUpdate.User> metaDataUpdateFactory,
          IdentifiedUser.GenericFactory userFactory) {
        super(repoManager, allUsersName);

        this.metaDataUpdateFactory = metaDataUpdateFactory;
        this.userFactory = userFactory;
      }

      @Override
      protected void commit(ExternalIdsConfig externalIdsConfig)
          throws IOException {
        try (MetaDataUpdate md = metaDataUpdateFactory.get().create(allUsersName,
            userFactory.create(externalIdsConfig.accountId))) {
          externalIdsConfig.commit(md);
        }
      }
    }

    @Singleton
    public static class Server extends Accessor {
      private final Provider<MetaDataUpdate.Server> metaDataUpdateFactory;

      @Inject
      Server(GitRepositoryManager repoManager,
          AllUsersName allUsersName,
          Provider<MetaDataUpdate.Server> metaDataUpdateFactory) {
        super(repoManager, allUsersName);

        this.metaDataUpdateFactory = metaDataUpdateFactory;
      }

      @Override
      protected void commit(ExternalIdsConfig externalIdsConfig)
          throws IOException {
        try (MetaDataUpdate md =
            metaDataUpdateFactory.get().create(allUsersName)) {
          externalIdsConfig.commit(md);
        }
      }
    }

    protected final GitRepositoryManager repoManager;
    protected final AllUsersName allUsersName;

    private Accessor(GitRepositoryManager repoManager,
        AllUsersName allUsersName) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
    }

    public Multimap<String, ExternalId> get(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      try (Repository git = repoManager.openRepository(allUsersName);
          ExternalIdsConfig externalIdsConfig =
              new ExternalIdsConfig(accountId)) {
        externalIdsConfig.load(git);
        return externalIdsConfig.getExternalIds();
      }
    }

    public void upsert(Account.Id accountId, ExternalId newExternalIds)
        throws IOException, ConfigInvalidException {
      upsert(accountId, Collections.singleton(newExternalIds));
    }

    public void upsert(Account.Id accountId,
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

    public void delete(Account.Id accountId, ExternalId externalIdToDelete)
        throws IOException, ConfigInvalidException {
      delete(accountId, Collections.singleton(externalIdToDelete));
    }

    public void delete(Account.Id accountId,
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

    public void deleteAll(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      try (ExternalIdsConfig externalIdsConfig = open(accountId)) {
        Multimap<String, ExternalId> externalIds =
            externalIdsConfig.getExternalIds();
        if (!externalIds.isEmpty()) {
          externalIds.clear();
          commit(externalIdsConfig);
        }
      }
    }

    public void replace(Account.Id accountId, ExternalId oldExternalId,
        ExternalId newExternalId) throws IOException, ConfigInvalidException {
      replace(accountId, Collections.singleton(oldExternalId),
          Collections.singleton(newExternalId));
    }

    public void replace(Account.Id accountId,
        Collection<ExternalId> oldExternalIds,
        Collection<ExternalId> newExternalIds)
            throws IOException, ConfigInvalidException {
      try (ExternalIdsConfig externalIdsConfig = open(accountId)) {
        Multimap<String, ExternalId> externalIds =
            externalIdsConfig.getExternalIds();
        for (ExternalId externalId : oldExternalIds) {
          externalIds.remove(externalId.scheme(), externalId);
        }
        for (ExternalId externalId : newExternalIds) {
          externalIds.put(externalId.scheme(), externalId);
        }
        commit(externalIdsConfig);
      }
    }

    private ExternalIdsConfig open(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      Repository git = repoManager.openRepository(allUsersName);
      ExternalIdsConfig externalIdsConfig = new ExternalIdsConfig(accountId);
      externalIdsConfig.load(git);
      return externalIdsConfig;
    }

    protected abstract void commit(ExternalIdsConfig externalIdsConfig)
        throws IOException;
  }

  @AutoValue
  public abstract static class ExternalId {
    public static ExternalId create(String scheme, String id) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme, id, null, null);
    }

    public static ExternalId create(String scheme, String id,
        @Nullable String email, @Nullable String password) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme, id, email,
          password);
    }

    public static ExternalId createWithPassword(String scheme, String id,
        String password) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme, id, null,
          Strings.emptyToNull(password));
    }

    public static ExternalId createUsername(String id, String password) {
      return createWithPassword(SCHEME_USERNAME, id, password);
    }

    public static ExternalId createWithEmail(String scheme, String id,
        String email) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme, id, email,
          null);
    }

    public static ExternalId createEmail(String email) {
      return createWithEmail(SCHEME_MAILTO, email, email);
    }

    static ExternalId create(String scheme, ExternalIdValue externalIdValue) {
      return new AutoValue_ExternalIdsConfig_ExternalId(scheme,
          externalIdValue.id(), externalIdValue.email(),
          externalIdValue.password());
    }

    public static ExternalId from(AccountExternalId externalId) {
      return create(externalId.getKey().getScheme(), externalId.getExternalId(),
          externalId.getEmailAddress(), externalId.getPassword());
    }

    public static Collection<ExternalId> from(
        Iterable<AccountExternalId> externalIds) {
      return FluentIterable.from(externalIds)
          .transform(new Function<AccountExternalId, ExternalId>() {
            @Override
            public ExternalId apply(AccountExternalId externalId) {
              return from(externalId);
            }
          }).toList();
    }

    public static Collection<ExternalId> fromKeys(
        Iterable<AccountExternalId.Key> externalIdsKeys) {
      return FluentIterable.from(externalIdsKeys)
          .transform(new Function<AccountExternalId.Key, ExternalId>() {
            @Override
            public ExternalId apply(AccountExternalId.Key externalIdKey) {
              return create(externalIdKey.getScheme(), externalIdKey.get());
            }
          }).toList();
    }

    ExternalIdValue getValue() {
      return ExternalIdValue.create(id(), email(), password());
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

  public static final String EXTERNAL_IDS_CONFIG = "external-ids.config";

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
    externalIds = parse(cfg);
  }

  public static Multimap<String, ExternalId> parse(Config cfg) {
    Multimap<String, ExternalId> externalIds = ArrayListMultimap.create();
    for (String schemeValue : cfg.getSubsections(SCHEME)) {
      String scheme = schemeValue.toLowerCase();
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

    if (externalIds.isEmpty()) {
      if (Strings.isNullOrEmpty(commit.getMessage())) {
        commit.setMessage("Deleted external IDs configuration\n");
      }
      saveFile(EXTERNAL_IDS_CONFIG, null);
      return true;
    }

    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated external IDs configuration\n");
    }

    Config cfg = readConfig(EXTERNAL_IDS_CONFIG);
    writeToConfig(cfg, externalIds);
    saveConfig(EXTERNAL_IDS_CONFIG, cfg);
    return true;
  }

  public static void writeToConfig(Config cfg,
      Multimap<String, ExternalId> externalIds) {
    for (String projectName : cfg.getSubsections(SCHEME)) {
      cfg.unset(SCHEME, projectName, KEY_ID);
    }

    for (Map.Entry<String, Collection<ExternalId>> e : externalIds.asMap()
        .entrySet()) {
      cfg.setStringList(SCHEME, e.getKey(), KEY_ID,
          FluentIterable.from(e.getValue())
              .transform(new Function<ExternalId, String>() {
            @Override
            public String apply(ExternalId externalId) {
              return externalId.getValue().toString();
            }
          }).toList());
    }
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
