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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountResolver;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class NotifyResolver {
  @AutoValue
  public abstract static class Result {
    public static Result none() {
      return create(NotifyHandling.NONE);
    }

    public static Result all() {
      return create(NotifyHandling.ALL);
    }

    public static Result create(NotifyHandling notifyHandling) {
      return create(notifyHandling, ImmutableSetMultimap.of());
    }

    public static Result create(
        NotifyHandling handling, ImmutableSetMultimap<RecipientType, Account.Id> recipients) {
      return new AutoValue_NotifyResolver_Result(handling, recipients);
    }

    public abstract NotifyHandling handling();

    // TODO(dborowitz): Should be ImmutableSetMultimap.
    public abstract ImmutableSetMultimap<RecipientType, Account.Id> accounts();

    public Result withHandling(NotifyHandling notifyHandling) {
      return create(notifyHandling, accounts());
    }

    public boolean shouldNotify() {
      return !accounts().isEmpty() || handling().compareTo(NotifyHandling.NONE) > 0;
    }
  }

  private final AccountResolver accountResolver;

  @Inject
  NotifyResolver(AccountResolver accountResolver) {
    this.accountResolver = accountResolver;
  }

  public Result resolve(
      NotifyHandling handling, @Nullable Map<RecipientType, NotifyInfo> notifyDetails)
      throws BadRequestException, IOException, ConfigInvalidException {
    requireNonNull(handling);
    ImmutableSetMultimap.Builder<RecipientType, Account.Id> b = ImmutableSetMultimap.builder();
    if (notifyDetails != null) {
      for (Map.Entry<RecipientType, NotifyInfo> e : notifyDetails.entrySet()) {
        b.putAll(e.getKey(), find(e.getValue().accounts));
      }
    }
    return Result.create(handling, b.build());
  }

  private ImmutableList<Account.Id> find(@Nullable List<String> inputs)
      throws BadRequestException, IOException, ConfigInvalidException {
    if (inputs == null || inputs.isEmpty()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Account.Id> r = ImmutableList.builder();
    List<String> problems = new ArrayList<>(inputs.size());
    for (String nameOrEmail : inputs) {
      try {
        r.add(accountResolver.resolve(nameOrEmail).asUnique().getAccount().getId());
      } catch (UnprocessableEntityException e) {
        problems.add(e.getMessage());
      }
    }

    if (!problems.isEmpty()) {
      throw new BadRequestException(
          "Some accounts that should be notified could not be resolved: "
              + problems.stream().collect(joining("\n")));
    }

    return r.build();
  }
}
