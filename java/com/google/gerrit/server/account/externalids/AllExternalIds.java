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

import static com.google.common.collect.ImmutableSetMultimap.toImmutableSetMultimap;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gerrit.reviewdb.client.Account;
import java.util.Collection;

/** Cache value containing all external IDs. */
@AutoValue
public abstract class AllExternalIds {
  static AllExternalIds create(SetMultimap<Account.Id, ExternalId> byAccount) {
    return new AutoValue_AllExternalIds(
        ImmutableSetMultimap.copyOf(byAccount), byEmailCopy(byAccount.values()));
  }

  static AllExternalIds create(Collection<ExternalId> externalIds) {
    return new AutoValue_AllExternalIds(
        externalIds.stream().collect(toImmutableSetMultimap(e -> e.accountId(), e -> e)),
        byEmailCopy(externalIds));
  }

  private static ImmutableSetMultimap<String, ExternalId> byEmailCopy(
      Collection<ExternalId> externalIds) {
    return externalIds
        .stream()
        .filter(e -> !Strings.isNullOrEmpty(e.email()))
        .collect(toImmutableSetMultimap(e -> e.email(), e -> e));
  }

  public abstract ImmutableSetMultimap<Account.Id, ExternalId> byAccount();

  public abstract ImmutableSetMultimap<String, ExternalId> byEmail();
}
