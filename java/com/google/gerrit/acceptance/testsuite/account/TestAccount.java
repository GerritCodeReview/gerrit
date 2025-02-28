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

package com.google.gerrit.acceptance.testsuite.account;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import java.util.Optional;

public record TestAccount(
    Account.Id accountId,
    Optional<String> fullname,
    Optional<String> preferredEmail,
    Optional<String> username,
    boolean active,
    ImmutableSet<String> emails) {
  public TestAccount {
    requireNonNull(accountId, "accountId");
    requireNonNull(fullname, "fullname");
    requireNonNull(preferredEmail, "preferredEmail");
    requireNonNull(username, "username");
    requireNonNull(emails, "emails");
  }

  public ImmutableSet<String> secondaryEmails() {
    if (!preferredEmail().isPresent()) {
      return emails();
    }

    return ImmutableSet.copyOf(Sets.difference(emails(), ImmutableSet.of(preferredEmail().get())));
  }

  static Builder builder() {
    return new AutoBuilder_TestAccount_Builder();
  }

  @AutoBuilder
  abstract static class Builder {
    abstract Builder accountId(Account.Id accountId);

    abstract Builder fullname(Optional<String> fullname);

    abstract Builder preferredEmail(Optional<String> fullname);

    abstract Builder username(Optional<String> username);

    abstract Builder active(boolean active);

    abstract Builder emails(ImmutableSet<String> emails);

    abstract TestAccount build();
  }
}
