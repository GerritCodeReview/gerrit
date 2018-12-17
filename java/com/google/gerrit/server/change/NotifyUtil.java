// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static java.util.stream.Collectors.joining;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gerrit.server.account.AccountResolver;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class NotifyUtil {
  private final AccountResolver accountResolver;

  @Inject
  NotifyUtil(AccountResolver accountResolver) {
    this.accountResolver = accountResolver;
  }

  public static boolean shouldNotify(
      NotifyHandling notify, @Nullable Map<RecipientType, NotifyInfo> notifyDetails) {
    if (!isNullOrEmpty(notifyDetails)) {
      return true;
    }

    return notify.compareTo(NotifyHandling.NONE) > 0;
  }

  private static boolean isNullOrEmpty(@Nullable Map<RecipientType, NotifyInfo> notifyDetails) {
    if (notifyDetails == null || notifyDetails.isEmpty()) {
      return true;
    }

    for (NotifyInfo notifyInfo : notifyDetails.values()) {
      if (!isEmpty(notifyInfo)) {
        return false;
      }
    }

    return true;
  }

  private static boolean isEmpty(NotifyInfo notifyInfo) {
    return notifyInfo.accounts == null || notifyInfo.accounts.isEmpty();
  }

  public ListMultimap<RecipientType, Account.Id> resolveAccounts(
      @Nullable Map<RecipientType, NotifyInfo> notifyDetails)
      throws OrmException, BadRequestException, IOException, ConfigInvalidException {
    if (isNullOrEmpty(notifyDetails)) {
      return ImmutableListMultimap.of();
    }

    ListMultimap<RecipientType, Account.Id> m = null;
    for (Entry<RecipientType, NotifyInfo> e : notifyDetails.entrySet()) {
      List<String> accounts = e.getValue().accounts;
      if (accounts != null) {
        if (m == null) {
          m = MultimapBuilder.hashKeys().arrayListValues().build();
        }
        m.putAll(e.getKey(), find(accounts));
      }
    }

    return m != null ? m : ImmutableListMultimap.of();
  }

  private List<Account.Id> find(List<String> nameOrEmails)
      throws OrmException, BadRequestException, IOException, ConfigInvalidException {
    List<String> missing = new ArrayList<>(nameOrEmails.size());
    List<Account.Id> r = new ArrayList<>(nameOrEmails.size());
    for (String nameOrEmail : nameOrEmails) {
      Account a = accountResolver.find(nameOrEmail);
      if (a != null) {
        r.add(a.getId());
      } else {
        missing.add(nameOrEmail);
      }
    }

    if (!missing.isEmpty()) {
      throw new BadRequestException(
          "The following accounts that should be notified could not be resolved: "
              + missing.stream().distinct().sorted().collect(joining(", ")));
    }

    return r;
  }
}
