// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountDirectory.DirectoryException;
import com.google.gerrit.server.account.AccountDirectory.FillOptions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AccountLoader {
  public static final Set<FillOptions> DETAILED_OPTIONS =
      Collections.unmodifiableSet(
          EnumSet.of(
              FillOptions.ID,
              FillOptions.NAME,
              FillOptions.EMAIL,
              FillOptions.USERNAME,
              FillOptions.AVATARS));

  public interface Factory {
    AccountLoader create(boolean detailed);

    AccountLoader create(Set<FillOptions> options);
  }

  private final InternalAccountDirectory directory;
  private final Set<FillOptions> options;
  private final Map<Account.Id, AccountInfo> created;
  private final List<AccountInfo> provided;

  @AssistedInject
  AccountLoader(InternalAccountDirectory directory, @Assisted boolean detailed) {
    this(directory, detailed ? DETAILED_OPTIONS : InternalAccountDirectory.ID_ONLY);
  }

  @AssistedInject
  AccountLoader(InternalAccountDirectory directory, @Assisted Set<FillOptions> options) {
    this.directory = directory;
    this.options = options;
    created = new HashMap<>();
    provided = new ArrayList<>();
  }

  public AccountInfo get(Account.Id id) {
    if (id == null) {
      return null;
    }
    AccountInfo info = created.get(id);
    if (info == null) {
      info = new AccountInfo(id.get());
      created.put(id, info);
    }
    return info;
  }

  public void put(AccountInfo info) {
    checkArgument(info._accountId != null, "_accountId field required");
    provided.add(info);
  }

  public void fill() throws OrmException {
    try {
      directory.fillAccountInfo(Iterables.concat(created.values(), provided), options);
    } catch (DirectoryException e) {
      Throwables.throwIfInstanceOf(e.getCause(), OrmException.class);
      throw new OrmException(e);
    }
  }

  public void fill(Collection<? extends AccountInfo> infos) throws OrmException {
    for (AccountInfo info : infos) {
      put(info);
    }
    fill();
  }
}
