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

import static com.google.gerrit.server.account.ExternalId.SCHEME_USERNAME;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;

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
  private final ExternalIdsUpdate.Server externalIdsUpdateFactory;

  private final ReviewDb db;
  private final IdentifiedUser user;
  private final String newUsername;

  @Inject
  ChangeUserName(
      AccountCache accountCache,
      SshKeyCache sshKeyCache,
      ExternalIdsUpdate.Server externalIdsUpdateFactory,
      @Assisted ReviewDb db,
      @Assisted IdentifiedUser user,
      @Nullable @Assisted String newUsername) {
    this.accountCache = accountCache;
    this.sshKeyCache = sshKeyCache;
    this.externalIdsUpdateFactory = externalIdsUpdateFactory;
    this.db = db;
    this.user = user;
    this.newUsername = newUsername;
  }

  @Override
  public VoidResult call()
      throws OrmException, NameAlreadyUsedException, InvalidUserNameException, IOException,
          ConfigInvalidException {
    Collection<ExternalId> old =
        ExternalId.from(db.accountExternalIds().byAccount(user.getAccountId()).toList())
            .stream()
            .filter(e -> e.isScheme(SCHEME_USERNAME))
            .collect(toSet());
    if (!old.isEmpty()) {
      throw new IllegalStateException(USERNAME_CANNOT_BE_CHANGED);
    }

    ExternalIdsUpdate externalIdsUpdate = externalIdsUpdateFactory.create();
    if (newUsername != null && !newUsername.isEmpty()) {
      if (!USER_NAME_PATTERN.matcher(newUsername).matches()) {
        throw new InvalidUserNameException();
      }

      ExternalId.Key key = ExternalId.Key.create(SCHEME_USERNAME, newUsername);
      try {
        String password = null;
        for (ExternalId i : old) {
          if (i.password() != null) {
            password = i.password();
          }
        }
        externalIdsUpdate.insert(db, ExternalId.create(key, user.getAccountId(), null, password));
      } catch (OrmDuplicateKeyException dupeErr) {
        // If we are using this identity, don't report the exception.
        //
        ExternalId other =
            ExternalId.from(db.accountExternalIds().get(key.asAccountExternalIdKey()));
        if (other != null && other.accountId().equals(user.getAccountId())) {
          return VoidResult.INSTANCE;
        }

        // Otherwise, someone else has this identity.
        //
        throw new NameAlreadyUsedException(newUsername);
      }
    }

    // If we have any older user names, remove them.
    //
    externalIdsUpdate.delete(db, old);
    for (ExternalId extId : old) {
      sshKeyCache.evict(extId.key().id());
      accountCache.evictByUsername(extId.key().id());
    }

    accountCache.evictByUsername(newUsername);
    sshKeyCache.evict(newUsername);
    return VoidResult.INSTANCE;
  }
}
