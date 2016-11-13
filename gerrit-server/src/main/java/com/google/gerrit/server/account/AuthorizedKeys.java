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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountSshKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class AuthorizedKeys {
  public static final String FILE_NAME = "authorized_keys";

  @VisibleForTesting public static final String INVALID_KEY_COMMENT_PREFIX = "# INVALID ";

  @VisibleForTesting public static final String DELETED_KEY_COMMENT = "# DELETED";

  public static List<Optional<AccountSshKey>> parse(Account.Id accountId, String s) {
    List<Optional<AccountSshKey>> keys = new ArrayList<>();
    int seq = 1;
    for (String line : s.split("\\r?\\n")) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      } else if (line.startsWith(INVALID_KEY_COMMENT_PREFIX)) {
        String pub = line.substring(INVALID_KEY_COMMENT_PREFIX.length());
        AccountSshKey key = new AccountSshKey(new AccountSshKey.Id(accountId, seq++), pub);
        key.setInvalid();
        keys.add(Optional.of(key));
      } else if (line.startsWith(DELETED_KEY_COMMENT)) {
        keys.add(Optional.empty());
        seq++;
      } else if (line.startsWith("#")) {
        continue;
      } else {
        AccountSshKey key = new AccountSshKey(new AccountSshKey.Id(accountId, seq++), line);
        keys.add(Optional.of(key));
      }
    }
    return keys;
  }

  public static String serialize(Collection<Optional<AccountSshKey>> keys) {
    StringBuilder b = new StringBuilder();
    for (Optional<AccountSshKey> key : keys) {
      if (key.isPresent()) {
        if (!key.get().isValid()) {
          b.append(INVALID_KEY_COMMENT_PREFIX);
        }
        b.append(key.get().getSshPublicKey().trim());
      } else {
        b.append(DELETED_KEY_COMMENT);
      }
      b.append("\n");
    }
    return b.toString();
  }
}
