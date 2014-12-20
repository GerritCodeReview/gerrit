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

package com.google.gerrit.testutil;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.FieldName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.Realm;

import java.util.Collections;
import java.util.Set;

/** Fake implementation of {@link Realm} for testing. */
public class FakeRealm implements Realm {
  @Override
  public boolean allowsEdit(FieldName field) {
    return false;
  }

  @Override
  public AuthRequest authenticate(AuthRequest who) {
    return who;
  }

  @Override
  public AuthRequest link(ReviewDb db, Account.Id to, AuthRequest who) {
    return who;
  }

  @Override
  public AuthRequest unlink(ReviewDb db, Account.Id to, AuthRequest who) {
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

  @Override
  public boolean hasEmailAddress(IdentifiedUser who, String email) {
    return false;
  }

  @Override
  public Set<String> getEmailAddresses(IdentifiedUser who) {
    return Collections.emptySet();
  }
}
