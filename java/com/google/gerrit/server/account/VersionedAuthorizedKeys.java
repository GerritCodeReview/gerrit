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
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.InvalidSshKeyException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.ssh.SshKeyCreator;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Repository;

/**
 * 'authorized_keys' file in the refs/users/CD/ABCD branches of the All-Users repository.
 *
 * <p>The `authorized_keys' files stores the public SSH keys of the user. The file format matches
 * the standard SSH file format, which means that each key is stored on a separate line (see
 * https://en.wikibooks.org/wiki/OpenSSH/Client_Configuration_Files#.7E.2F.ssh.2Fauthorized_keys).
 *
 * <p>The order of the keys in the file determines the sequence numbers of the keys. The first line
 * corresponds to sequence number 1.
 *
 * <p>Each line has one of these forms:
 *
 * <pre>{@code
 * [# EXPIRES <instant> ]<key>             a valid key
 * # INVALID [# EXPIRES <instant> ]<key>   an invalid key
 * # DELETED [# EXPIRES <instant> ]<key>   a deleted key that had an expiration
 * # DELETED                               a deleted key without expiration
 * }</pre>
 *
 * <p>where {@code <instant>} is an ISO-8601 instant, e.g. {@code 2030-01-02T03:04:05Z}, and {@code
 * <key>} is {@code <algorithm> <encoded key> [<comment>]}.
 *
 * <p>To keep the sequence numbers intact when a key is deleted, a {@code # DELETED} line is
 * inserted at the position where the key was deleted. A deleted key that had an expiration is kept
 * on that line so that it cannot be added again to renew it.
 *
 * <p>Other comment lines are ignored on read, and are not written back when the file is modified.
 */
public class VersionedAuthorizedKeys extends VersionedMetaData {
  public static final String KEY_ALREADY_EXISTS = "SSH key already exists";
  public static final String KEY_USED_PREVIOUSLY = "SSH key has been used previously";

  /** Read/write SSH keys by user ID. */
  @Singleton
  public static class Accessor {
    private final GitRepositoryManager repoManager;
    private final AllUsersName allUsersName;
    private final VersionedAuthorizedKeys.Factory authorizedKeysFactory;
    private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Accessor(
        GitRepositoryManager repoManager,
        AllUsersName allUsersName,
        VersionedAuthorizedKeys.Factory authorizedKeysFactory,
        Provider<MetaDataUpdate.User> metaDataUpdateFactory,
        IdentifiedUser.GenericFactory userFactory) {
      this.repoManager = repoManager;
      this.allUsersName = allUsersName;
      this.authorizedKeysFactory = authorizedKeysFactory;
      this.metaDataUpdateFactory = metaDataUpdateFactory;
      this.userFactory = userFactory;
    }

    public List<AccountSshKey> getKeys(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      return read(accountId).getKeys();
    }

    public AccountSshKey getKey(Account.Id accountId, int seq)
        throws IOException, ConfigInvalidException {
      return read(accountId).getKey(seq);
    }

    @CanIgnoreReturnValue
    public synchronized AccountSshKey addKey(
        Account.Id accountId, String pub, Optional<Instant> expirationDate)
        throws IOException, ConfigInvalidException, InvalidSshKeyException {
      VersionedAuthorizedKeys authorizedKeys = read(accountId);
      AccountSshKey key = authorizedKeys.addKey(pub, expirationDate);
      commit(authorizedKeys);
      return key;
    }

    public synchronized void deleteKey(Account.Id accountId, int seq)
        throws IOException, ConfigInvalidException {
      VersionedAuthorizedKeys authorizedKeys = read(accountId);
      if (authorizedKeys.deleteKey(seq)) {
        commit(authorizedKeys);
      }
    }

    public synchronized void markKeyInvalid(Account.Id accountId, int seq)
        throws IOException, ConfigInvalidException {
      VersionedAuthorizedKeys authorizedKeys = read(accountId);
      if (authorizedKeys.markKeyInvalid(seq)) {
        commit(authorizedKeys);
      }
    }

    private VersionedAuthorizedKeys read(Account.Id accountId)
        throws IOException, ConfigInvalidException {
      try (Repository git = repoManager.openRepository(allUsersName)) {
        VersionedAuthorizedKeys authorizedKeys = authorizedKeysFactory.create(accountId);
        authorizedKeys.load(allUsersName, git);
        return authorizedKeys;
      }
    }

    private void commit(VersionedAuthorizedKeys authorizedKeys) throws IOException {
      try (MetaDataUpdate md =
          metaDataUpdateFactory
              .get()
              .create(allUsersName, userFactory.create(authorizedKeys.accountId))) {
        authorizedKeys.commit(md);
      }
    }
  }

  public static class SimpleSshKeyCreator implements SshKeyCreator {
    @Override
    public AccountSshKey create(
        Account.Id accountId, int seq, String encoded, Optional<Instant> expirationDate) {
      return AccountSshKey.create(accountId, seq, encoded, true, false, expirationDate);
    }
  }

  public interface Factory {
    VersionedAuthorizedKeys create(Account.Id accountId);
  }

  private final SshKeyCreator sshKeyCreator;
  private final Account.Id accountId;
  private final String ref;
  private List<Optional<AccountSshKey>> keys;

  @Inject
  public VersionedAuthorizedKeys(SshKeyCreator sshKeyCreator, @Assisted Account.Id accountId) {
    this.sshKeyCreator = sshKeyCreator;
    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  protected void onLoad() throws IOException {
    keys = AuthorizedKeys.parse(accountId, readUTF8(AuthorizedKeys.FILE_NAME));
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException {
    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Updated SSH keys\n");
    }

    saveUTF8(AuthorizedKeys.FILE_NAME, AuthorizedKeys.serialize(keys));
    return true;
  }

  /** Returns all SSH keys. */
  private List<AccountSshKey> getKeys() {
    checkLoaded();
    return keys.stream().flatMap(Optional::stream).filter(key -> !key.deleted()).collect(toList());
  }

  /**
   * Returns the SSH key with the given sequence number.
   *
   * @param seq sequence number
   * @return the SSH key, <code>null</code> if there is no SSH key with this sequence number, or if
   *     the SSH key with this sequence number has been deleted
   */
  @Nullable
  private AccountSshKey getKey(int seq) {
    checkLoaded();
    return keys.get(seq - 1).filter(key -> !key.deleted()).orElse(null);
  }

  /**
   * Adds a new public SSH key.
   *
   * <p>A key cannot be added if a key with the same algorithm and key material (the comment is
   * ignored) exists already, has expired, or has been deleted before.
   *
   * @param pub the public SSH key to be added
   * @param expirationDate when the new key expires, if at all
   * @return the new SSH key
   * @throws InvalidSshKeyException if the key is invalid, exists already or was used previously
   */
  private AccountSshKey addKey(String pub, Optional<Instant> expirationDate)
      throws InvalidSshKeyException {
    checkLoaded();

    int seq = keys.size() + 1;
    AccountSshKey key = sshKeyCreator.create(accountId, seq, pub, expirationDate);

    for (Optional<AccountSshKey> existing : keys) {
      if (existing.isPresent()
          && existing.get().algorithm().equals(key.algorithm())
          && Objects.equals(existing.get().encodedKey(), key.encodedKey())) {
        throw new InvalidSshKeyException(
            existing.get().deleted() || existing.get().isExpired()
                ? KEY_USED_PREVIOUSLY
                : KEY_ALREADY_EXISTS);
      }
    }

    keys.add(Optional.of(key));
    return key;
  }

  /**
   * Deletes the SSH key with the given sequence number.
   *
   * @param seq the sequence number
   * @return <code>true</code> if a key with this sequence number was found and deleted, <code>false
   *     </code> if no key with the given sequence number exists
   */
  private boolean deleteKey(int seq) {
    checkLoaded();
    if (seq <= keys.size() && keys.get(seq - 1).isPresent()) {
      AccountSshKey key = keys.get(seq - 1).get();
      keys.set(
          seq - 1,
          key.expirationDate().isPresent()
              ? Optional.of(
                  AccountSshKey.create(
                      key.accountId(),
                      key.seq(),
                      key.sshPublicKey(),
                      false,
                      true,
                      key.expirationDate()))
              : Optional.empty());
      return true;
    }
    return false;
  }

  /**
   * Marks the SSH key with the given sequence number as invalid.
   *
   * @param seq the sequence number
   * @return <code>true</code> if a key with this sequence number was found and marked as invalid,
   *     <code>false</code> if no key with the given sequence number exists or if the key was
   *     already marked as invalid
   */
  private boolean markKeyInvalid(int seq) {
    checkLoaded();

    Optional<AccountSshKey> key = keys.get(seq - 1);
    if (key.isPresent() && key.get().valid()) {
      keys.set(seq - 1, Optional.of(AccountSshKey.createInvalid(key.get())));
      return true;
    }
    return false;
  }

  /**
   * Sets new SSH keys.
   *
   * <p>The existing SSH keys are overwritten.
   *
   * @param newKeys the new public SSH keys
   */
  public void setKeys(Collection<AccountSshKey> newKeys) {
    Ordering<AccountSshKey> o = Ordering.from(comparing(AccountSshKey::seq));
    keys = new ArrayList<>(Collections.nCopies(o.max(newKeys).seq(), Optional.empty()));
    for (AccountSshKey key : newKeys) {
      keys.set(key.seq() - 1, Optional.of(key));
    }
  }

  private void checkLoaded() {
    checkState(keys != null, "SSH keys not loaded yet");
  }
}
