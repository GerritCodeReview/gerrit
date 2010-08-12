// Copyright (C) 2010 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.util.FutureUtil;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.apache.commons.codec.binary.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/** Operation to generate a password for an account. */
public class GeneratePassword implements Callable<AccountExternalId> {
  private static final int LEN = 12;
  private static final SecureRandom rng;

  static {
    try {
      rng = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot create RNG for password generator", e);
    }
  }

  public interface Factory {
    GeneratePassword create(AccountExternalId.Key forUser);
  }

  private final AccountCache accountCache;
  private final ReviewDb db;
  private final IdentifiedUser user;

  private final AccountExternalId.Key idKey;

  @Inject
  GeneratePassword(final AccountCache accountCache, final ReviewDb db,
      final IdentifiedUser user,

      @Assisted AccountExternalId.Key idKey) {
    this.accountCache = accountCache;
    this.db = db;
    this.user = user;

    this.idKey = idKey;
  }

  public AccountExternalId call() throws OrmException, NoSuchEntityException {
    AccountExternalId id = db.accountExternalIds().get(idKey);
    if (id == null || !user.getAccountId().equals(id.getAccountId())) {
      throw new NoSuchEntityException();
    }

    List<ListenableFuture<Void>> evictions = Lists.newArrayList();
    id.setPassword(generate());
    db.accountExternalIds().update(Collections.singleton(id));
    evictions.add(accountCache.evictAsync(user.getAccountId()));
    evictions.add(accountCache.evictAsync(idKey));
    FutureUtil.waitFor(evictions);
    return id;
  }

  private String generate() {
    byte[] rand = new byte[LEN];
    rng.nextBytes(rand);

    byte[] enc = Base64.encodeBase64(rand, false);
    StringBuilder r = new StringBuilder(LEN);
    for (int i = 0; i < LEN; i++) {
      if (enc[i] == '=') {
        break;
      }
      r.append((char) enc[i]);
    }
    return r.toString();
  }
}
