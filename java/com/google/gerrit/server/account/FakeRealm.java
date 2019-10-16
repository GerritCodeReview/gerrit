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

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.client.AccountFieldName;

/** Fake implementation of {@link Realm} that does not communicate. */
public class FakeRealm extends AbstractRealm {
  @Override
  public boolean allowsEdit(AccountFieldName field) {
    return false;
  }

  @Override
  public AuthRequest authenticate(AuthRequest who) {
    return who;
  }

  @Override
  public void onCreateAccount(AuthRequest who, Account account) {
    // Do nothing.
  }

  @Override
  public Account.Id lookup(String accountName) {
    return null;
  }
}
