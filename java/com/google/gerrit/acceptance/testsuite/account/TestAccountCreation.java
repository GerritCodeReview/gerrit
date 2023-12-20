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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.Account;
import java.util.Optional;
import java.util.Set;

@AutoValue
public abstract class TestAccountCreation {
  public abstract Optional<String> fullname();

  public abstract Optional<String> httpPassword();

  public abstract Optional<String> preferredEmail();

  public abstract Optional<String> username();

  public abstract Optional<String> status();

  public abstract Optional<Boolean> active();

  public abstract ImmutableSet<String> secondaryEmails();

  abstract ThrowingFunction<TestAccountCreation, Account.Id> accountCreator();

  public static Builder builder(
      ThrowingFunction<TestAccountCreation, Account.Id> accountCreator,
      boolean arePasswordsAllowed) {
    TestAccountCreation.Builder builder =
        new AutoValue_TestAccountCreation.Builder().accountCreator(accountCreator);
    if (arePasswordsAllowed) {
      builder.httpPassword("http-pass");
    }
    return builder;
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder fullname(String fullname);

    public Builder clearFullname() {
      return fullname("");
    }

    public abstract Builder httpPassword(String httpPassword);

    public Builder clearHttpPassword() {
      return httpPassword("");
    }

    public abstract Builder preferredEmail(String preferredEmail);

    public Builder clearPreferredEmail() {
      return preferredEmail("");
    }

    public abstract Builder username(String username);

    public Builder clearUsername() {
      return username("");
    }

    public abstract Builder status(String status);

    public Builder clearStatus() {
      return status("");
    }

    abstract Builder active(boolean active);

    public Builder active() {
      return active(true);
    }

    public Builder inactive() {
      return active(false);
    }

    public abstract Builder secondaryEmails(Set<String> secondaryEmails);

    abstract ImmutableSet.Builder<String> secondaryEmailsBuilder();

    public Builder addSecondaryEmail(String secondaryEmail) {
      secondaryEmailsBuilder().add(secondaryEmail);
      return this;
    }

    abstract Builder accountCreator(
        ThrowingFunction<TestAccountCreation, Account.Id> accountCreator);

    abstract TestAccountCreation autoBuild();

    public Account.Id create() {
      TestAccountCreation accountCreation = autoBuild();
      if (accountCreation.preferredEmail().isPresent()) {
        checkState(
            !accountCreation.secondaryEmails().contains(accountCreation.preferredEmail().get()),
            "preferred email %s cannot be secondary email at the same time",
            accountCreation.preferredEmail().get());
      }
      return accountCreation.accountCreator().applyAndThrowSilently(accountCreation);
    }
  }
}
