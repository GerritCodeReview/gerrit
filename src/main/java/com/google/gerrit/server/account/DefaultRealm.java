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

import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.inject.Inject;

import java.util.Set;

public final class DefaultRealm implements Realm {
  private final EmailExpander emailExpander;

  @Inject
  DefaultRealm(final EmailExpander emailExpander) {
    this.emailExpander = emailExpander;
  }

  @Override
  public AuthRequest authenticate(final AuthRequest who) {
    if (who.getEmailAddress() == null && who.getLocalUser() != null
        && emailExpander.canExpand(who.getLocalUser())) {
      who.setEmailAddress(emailExpander.expand(who.getLocalUser()));
    }
    return who;
  }

  @Override
  public Set<AccountGroup.Id> groups(final AccountState who) {
    return who.getInternalGroups();
  }
}
