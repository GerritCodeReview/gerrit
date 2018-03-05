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

package com.google.gerrit.server.account;

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/** Operation to change the username of an account. */
public class ChangeUserName implements Callable<VoidResult> {
  public static final String USERNAME_CANNOT_BE_CHANGED = "Username cannot be changed.";

  private static final Pattern USER_NAME_PATTERN = Pattern.compile(Account.USER_NAME_PATTERN);

  /** Generic factory to change any user's username. */
  public interface Factory {
    ChangeUserName create(ReviewDb db, IdentifiedUser user, String newUsername);
  }

  private final AccountCache accountCache;
  private final SshKeyCache sshKeyCache;

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final String newUsername;

  @Inject
  ChangeUserName(
      final AccountCache accountCache,
      final SshKeyCache sshKeyCache,
      @Assisted final ReviewDb db,
      @Assisted final IdentifiedUser user,
      @Nullable @Assisted final String newUsername) {
    this.accountCache = accountCache;
    this.sshKeyCache = sshKeyCache;

    this.db = db;
    this.user = user;
    this.newUsername = newUsername;
  }

  @Override
  public VoidResult call()
      throws OrmException, NameAlreadyUsedException, InvalidUserNameException, IOException {
    final Collection<AccountExternalId> old = old();
    if (!old.isEmpty()) {
      throw new IllegalStateException(USERNAME_CANNOT_BE_CHANGED);
    }

    if (newUsername != null && !newUsername.isEmpty()) {
      if (!USER_NAME_PATTERN.matcher(newUsername).matches()) {
        throw new InvalidUserNameException();
      }

      final AccountExternalId.Key key = new AccountExternalId.Key(SCHEME_USERNAME, newUsername);
      try {
        final AccountExternalId id = new AccountExternalId(user.getAccountId(), key);

        for (AccountExternalId i : old) {
          if (i.getPassword() != null) {
            id.setPassword(i.getPassword());
          }
        }

        db.accountExternalIds().insert(Collections.singleton(id));
      } catch (OrmDuplicateKeyException dupeErr) {
        // If we are using this identity, don't report the exception.
        //
        AccountExternalId other = db.accountExternalIds().get(key);
        if (other != null && other.getAccountId().equals(user.getAccountId())) {
          return VoidResult.INSTANCE;
        }

        // Otherwise, someone else has this identity.
        //
        throw new NameAlreadyUsedException(newUsername);
      }
    }

    // If we have any older user names, remove them.
    //
    db.accountExternalIds().delete(old);
    for (AccountExternalId i : old) {
      sshKeyCache.evict(i.getSchemeRest());
      accountCache.evictByUsername(i.getSchemeRest());
    }

    accountCache.evict(user.getAccountId());
    accountCache.evictByUsername(newUsername);
    sshKeyCache.evict(newUsername);
    return VoidResult.INSTANCE;
  }

  private Collection<AccountExternalId> old() throws OrmException {
    final Collection<AccountExternalId> r = new ArrayList<>(1);
    for (AccountExternalId i : db.accountExternalIds().byAccount(user.getAccountId())) {
      if (i.isScheme(SCHEME_USERNAME)) {
        r.add(i);
      }
    }
    return r;
  }
}
