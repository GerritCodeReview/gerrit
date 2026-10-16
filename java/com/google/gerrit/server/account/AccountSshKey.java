// Copyright (C) 2008 The Android Open Source Project
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

import static java.util.stream.Collectors.joining;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import java.util.Map;
import java.util.Optional;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;

/**
 * An SSH key approved for use by an {@link Account}.
 *
 * @param rawKey the key as it was read, kept so that it is not lost when written back if {@code
 *     key} could not be parsed
 */
public record AccountSshKey(
    Account.Id accountId, int seq, boolean valid, Optional<AuthorizedKeyEntry> key, String rawKey) {
  public AccountSshKey(Account.Id accountId, int seq, String sshPublicKey) {
    this(accountId, seq, sshPublicKey, true);
  }

  public AccountSshKey(Account.Id accountId, int seq, String sshPublicKey, boolean valid) {
    this(accountId, seq, valid && seq > 0, parse(sshPublicKey), stripOffNewLines(sshPublicKey));
  }

  public AccountSshKey {
    if (key.isPresent()) {
      // we were able to parse and no longer need to hold duplicated rawKey
      rawKey = "";
    }
  }

  /** Returns a copy of this key that is flagged as invalid. */
  public AccountSshKey asInvalid() {
    return new AccountSshKey(accountId, seq, false, key, rawKey);
  }

  /** Returns the key in the format of a line of {@code authorized_keys}, including options. */
  public String sshPublicKey() {
    return key.map(AccountSshKey::format).orElse(rawKey);
  }

  public String algorithm() {
    return key.map(AuthorizedKeyEntry::getKeyType).orElse("none");
  }

  public @Nullable String encodedKey() {
    return key.map(k -> BaseEncoding.base64().encode(k.getKeyData())).orElse(null);
  }

  public String comment() {
    return key.map(AuthorizedKeyEntry::getComment).orElse("");
  }

  private static String format(AuthorizedKeyEntry key) {
    StringBuilder b = new StringBuilder();
    String options =
        key.getLoginOptions().entrySet().stream()
            .map(AccountSshKey::formatOption)
            .collect(joining(","));
    if (!options.isEmpty()) {
      b.append(options).append(' ');
    }
    b.append(key.getKeyType()).append(' ').append(BaseEncoding.base64().encode(key.getKeyData()));
    if (!Strings.isNullOrEmpty(key.getComment())) {
      b.append(' ').append(key.getComment());
    }
    return b.toString();
  }

  /** Boolean options are parsed to {@code name=true}, and negated ones to {@code !name=false}. */
  private static String formatOption(Map.Entry<String, String> option) {
    String name = option.getKey();
    String value = option.getValue();
    if (value.equals("true") || (name.startsWith("!") && value.equals("false"))) {
      return name;
    }
    return name + "=\"" + value + "\"";
  }

  private static String stripOffNewLines(String s) {
    return s.replace("\n", "").replace("\r", "");
  }

  private static Optional<AuthorizedKeyEntry> parse(String sshPublicKey) {
    String s = stripOffNewLines(sshPublicKey);
    if (s.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(AuthorizedKeyEntry.parseAuthorizedKeyEntry(s));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
