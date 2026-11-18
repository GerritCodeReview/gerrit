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

import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;
import com.google.gerrit.entities.Account;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;

public class AuthorizedKeys {
  public static final String FILE_NAME = "authorized_keys";

  @VisibleForTesting public static final String INVALID_KEY_COMMENT_PREFIX = "# INVALID ";

  @VisibleForTesting public static final String DELETED_KEY_COMMENT = "# DELETED";

  private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("\\r?\\n");

  public static List<Optional<AccountSshKey>> parse(Account.Id accountId, String s) {
    List<Optional<AccountSshKey>> keys = new ArrayList<>();
    int seq = 1;
    for (String line : Splitter.on(LINE_SPLIT_PATTERN).split(s)) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      } else if (line.startsWith(INVALID_KEY_COMMENT_PREFIX)) {
        String pub = line.substring(INVALID_KEY_COMMENT_PREFIX.length());
        AccountSshKey key = parseKey(accountId, seq++, pub).asInvalid();
        keys.add(Optional.of(key));
      } else if (line.startsWith(DELETED_KEY_COMMENT)) {
        keys.add(Optional.empty());
        seq++;
      } else if (line.startsWith("#")) {
        continue;
      } else {
        AccountSshKey key = parseKey(accountId, seq++, line);
        keys.add(Optional.of(key));
      }
    }
    return keys;
  }

  public static String serialize(Collection<Optional<AccountSshKey>> keys) {
    StringBuilder b = new StringBuilder();
    for (Optional<AccountSshKey> key : keys) {
      if (key.isPresent()) {
        if (!key.get().valid()) {
          b.append(INVALID_KEY_COMMENT_PREFIX);
        }
        b.append(format(key.get()));
      } else {
        b.append(DELETED_KEY_COMMENT);
      }
      b.append("\n");
    }
    return b.toString();
  }

  /** Parses a line of {@code authorized_keys}; unparsable lines are kept as the public key. */
  public static AccountSshKey parseKey(Account.Id accountId, int seq, String line) {
    AuthorizedKeyEntry entry;
    try {
      entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(line.replace("\n", "").replace("\r", ""));
    } catch (IllegalArgumentException e) {
      return new AccountSshKey(accountId, seq, line);
    }
    if (entry == null || entry.getLoginOptions().isEmpty()) {
      return new AccountSshKey(accountId, seq, line);
    }
    StringBuilder pub =
        new StringBuilder(entry.getKeyType())
            .append(' ')
            .append(BaseEncoding.base64().encode(entry.getKeyData()));
    if (!Strings.isNullOrEmpty(entry.getComment())) {
      pub.append(' ').append(entry.getComment());
    }
    return new AccountSshKey(
        accountId, seq, pub.toString(), true, ImmutableMap.copyOf(entry.getLoginOptions()));
  }

  public static String format(AccountSshKey key) {
    String optionsLine =
        key.options().entrySet().stream().map(AuthorizedKeys::formatOption).collect(joining(","));
    String pub = key.sshPublicKey().trim();
    return optionsLine.isEmpty() ? pub : optionsLine + " " + pub;
  }

  private static String formatOption(Map.Entry<String, String> option) {
    String name = option.getKey();
    String value = option.getValue();
    if (value.equals("true") || (name.startsWith("!") && value.equals("false"))) {
      return name;
    }
    return name + "=\"" + value + "\"";
  }
}
