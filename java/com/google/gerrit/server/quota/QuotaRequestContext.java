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

package com.google.gerrit.server.quota;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import java.util.Optional;

public record QuotaRequestContext(
    CurrentUser user,
    Optional<Project.NameKey> project,
    Optional<Change.Id> change,
    Optional<Account.Id> account) {
  public QuotaRequestContext {
    requireNonNull(user, "user");
    requireNonNull(project, "project");
    requireNonNull(change, "change");
    requireNonNull(account, "account");
  }

  public static Builder builder() {
    return new AutoBuilder_QuotaRequestContext_Builder().user(new AnonymousUser());
  }

  public Builder toBuilder() {
    return new AutoBuilder_QuotaRequestContext_Builder(this);
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract QuotaRequestContext.Builder user(CurrentUser user);

    public abstract QuotaRequestContext.Builder account(Account.Id account);

    public abstract QuotaRequestContext.Builder project(Project.NameKey project);

    public abstract QuotaRequestContext.Builder change(Change.Id change);

    public abstract QuotaRequestContext build();
  }
}
