// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Account.Id;

/**
 * Cache value containing all external IDs.
 *
 * <p>All returned fields are unmodifiable.
 */
@AutoValue
public abstract class AllExternalIds {
  static AllExternalIds create(Multimap<Id, ExternalId> byAccount) {
    SetMultimap<String, ExternalId> byEmailCopy =
        MultimapBuilder.hashKeys(byAccount.size()).hashSetValues(1).build();
    byAccount
        .values()
        .stream()
        .filter(e -> !Strings.isNullOrEmpty(e.email()))
        .forEach(e -> byEmailCopy.put(e.email(), e));

    return new AutoValue_AllExternalIds(
        Multimaps.unmodifiableSetMultimap(
            MultimapBuilder.hashKeys(byAccount.size()).hashSetValues(5).build(byAccount)),
        byEmailCopy);
  }

  public abstract SetMultimap<Id, ExternalId> byAccount();

  public abstract SetMultimap<String, ExternalId> byEmail();
}
