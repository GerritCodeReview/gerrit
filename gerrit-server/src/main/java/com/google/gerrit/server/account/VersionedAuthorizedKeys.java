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

import com.google.common.base.Optional;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.VersionedMetaData;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 'authorized_keys' file in the refs/users/CD/ABCD branches of the All-Users
 * repository.
 *
 * The `authorized_keys' files stores the public SSH keys of the user. The file
 * format matches the standard SSH file format, which means that each key is
 * stored in a separate line.
 *
 * The order of the keys in the file determines the sequence numbers of the
 * keys.
 *
 * Invalid keys are marked with the prefix <code># INVALID</code>.
 *
 * To keep the sequence numbers intact when a key is deleted, a
 * <code># DELETED</code> line is inserted at the position where the key is
 * deleted.
 *
 * Other comment lines are ignored on read, but are not written back when the
 * file is modified.
 */
public class VersionedAuthorizedKeys extends VersionedMetaData {
  private static final String FILE_NAME = "authorized_keys";
  private static final String INVALID_KEY_COMMENT = "# INVALID ";
  private static final String DELETED_KEY_COMMENT = "# DELETED";

  private final Account.Id accountId;
  private final String ref;
  private SortedMap<Integer, Optional<AccountSshKey>> keys;

  public VersionedAuthorizedKeys(Account.Id accountId) {
    this.accountId = accountId;
    this.ref = RefNames.refsUsers(accountId);
  }

  @Override
  protected String getRefName() {
    return ref;
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    keys = new TreeMap<>();
    int seq = 1;
    for (String line : readUTF8(FILE_NAME).split("\\r?\\n")) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      } else if (line.startsWith(INVALID_KEY_COMMENT)) {
        String pub = line.substring(INVALID_KEY_COMMENT.length());
        AccountSshKey key =
            new AccountSshKey(new AccountSshKey.Id(accountId, seq++), pub);
        key.setInvalid();
        keys.put(key.getKey().get(), Optional.of(key));
      } else if (line.startsWith(DELETED_KEY_COMMENT)) {
        keys.put(seq++, Optional.<AccountSshKey> absent());
      } else if (line.startsWith("#")) {
        continue;
      } else {
        AccountSshKey key =
            new AccountSshKey(new AccountSshKey.Id(accountId, seq++), line);
        keys.put(key.getKey().get(), Optional.of(key));
      }
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit)
      throws IOException, ConfigInvalidException {
    if (commit.getMessage() == null || "".equals(commit.getMessage())) {
      commit.setMessage("Updated SSH keys\n");
    }

    StringBuilder b = new StringBuilder();
    for (Optional<AccountSshKey> key : keys.values()) {
      if (key.isPresent()) {
        if (!key.get().isValid()) {
          b.append(INVALID_KEY_COMMENT);
        }
        b.append(key.get().getSshPublicKey().trim());
      } else {
        b.append(DELETED_KEY_COMMENT);
      }
      b.append("\n");
    }
    saveUTF8(FILE_NAME, b.toString());
    return true;
  }

  /** Returns all SSH keys. */
  public List<AccountSshKey> getKeys() {
    checkState(keys != null, "SSH keys not loaded yet");
    List<AccountSshKey> result = new ArrayList<>();
    for (Optional<AccountSshKey> key : keys.values()) {
      if (key.isPresent()) {
        result.add(key.get());
      }
    }
    return result;
  }

  /**
   * Returns the SSH key with the given sequence number.
   *
   * @param seq sequence number
   * @return the SSH key, <code>null</code> if there is no SSH key with this
   *         sequence number, or if the SSH key with this sequence number has
   *         been deleted
   */
  public AccountSshKey getKey(int seq) {
    checkState(keys != null, "SSH keys not loaded yet");
    Optional<AccountSshKey> key = keys.get(seq);
    return key.isPresent() ? key.get() : null;
  }

  /**
   * Adds a new public SSH key.
   *
   * @param pub the public SSH key to be added
   * @return the new SSH key
   */
  public AccountSshKey addKey(String pub) {
    checkState(keys != null, "SSH keys not loaded yet");
    int seq = keys.isEmpty() ? 1 : keys.lastKey() + 1;
    AccountSshKey key =
        new AccountSshKey(new AccountSshKey.Id(accountId, seq), pub);
    keys.put(seq, Optional.of(key));
    return key;
  }

  /**
   * Deletes the SSH key with the given sequence number.
   *
   * @param seq the sequence number
   * @return <code>true</code> if a key with this sequence number was found and
   *         deleted, <code>false</code> if no key with the given sequence
   *         number exists
   */
  public boolean deleteKey(int seq) {
    checkState(keys != null, "SSH keys not loaded yet");
    if (keys.containsKey(seq) && keys.get(seq).isPresent()) {
      keys.put(seq, Optional.<AccountSshKey> absent());
      return true;
    }
    return false;
  }

  /**
   * Marks the SSH key with the given sequence number as invalid.
   *
   * @param seq the sequence number
   */
  public void markKeyInvalid(int seq) {
    checkState(keys != null, "SSH keys not loaded yet");
    AccountSshKey key = getKey(seq);
    if (key != null) {
      key.setInvalid();
    }
  }

  /**
   * Sets new SSH keys.
   *
   * The existing SSH keys are overwritten.
   *
   * @param newKeys the new public SSH keys
   */
  public void setKeys(List<AccountSshKey> newKeys) {
    keys = new TreeMap<>();
    for (AccountSshKey key : newKeys) {
      keys.put(key.getKey().get(), Optional.of(key));
    }
    for (int seq = 1; seq < keys.lastKey(); seq++) {
      if (!keys.containsKey(seq)) {
        keys.put(seq, Optional.<AccountSshKey> absent());
      }
    }
  }
}
