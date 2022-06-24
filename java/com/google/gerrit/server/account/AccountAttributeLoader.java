// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class AccountAttributeLoader {

  public interface Factory {
    AccountAttributeLoader create();
  }

  private final InternalAccountDirectory directory;
  private final Map<Account.Id, AccountAttribute> created = new HashMap<>();

  @Inject
  AccountAttributeLoader(InternalAccountDirectory directory) {
    this.directory = directory;
  }

  @Nullable
  public synchronized AccountAttribute get(@Nullable Account.Id id) {
    if (id == null) {
      return null;
    }
    return created.computeIfAbsent(id, k -> new AccountAttribute(k.get()));
  }

  public void fill() {
    directory.fillAccountAttributeInfo(created.values());
  }
}
