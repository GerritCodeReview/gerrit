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

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.IdentifiedUser;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/** Basic implementation of {@link Realm}.  */
public abstract class AbstractRealm implements Realm {
  @Override
  public boolean hasEmailAddress(IdentifiedUser user, String email) {
    for (AccountExternalId ext : user.state().getExternalIds()) {
      if (Objects.equals(ext.getEmailAddress(), email)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Set<String> getEmailAddresses(IdentifiedUser user) {
    Collection<AccountExternalId> ids = user.state().getExternalIds();
    Set<String> emails = Sets.newHashSetWithExpectedSize(ids.size());
    for (AccountExternalId ext : ids) {
      if (!Strings.isNullOrEmpty(ext.getEmailAddress())) {
        emails.add(ext.getEmailAddress());
      }
    }
    return emails;
  }
}
