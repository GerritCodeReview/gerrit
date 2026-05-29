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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@AutoValue
public abstract class TestAccountUpdate {
  public abstract Optional<String> fullname();

  public abstract Optional<String> httpPassword();

  public abstract Optional<String> preferredEmail();

  public abstract Optional<String> username();

  public abstract Optional<String> status();

  public abstract Optional<Boolean> active();

  public abstract Function<ImmutableSet<String>, Set<String>> secondaryEmailsModification();

  abstract ThrowingConsumer<TestAccountUpdate> accountUpdater();

  public static Builder builder(ThrowingConsumer<TestAccountUpdate> accountUpdater) {
    return new AutoValue_TestAccountUpdate.Builder()
        .accountUpdater(accountUpdater)
        .secondaryEmailsModification(in -> in)
        .httpPassword("http-pass");
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

    abstract Builder secondaryEmailsModification(
        Function<ImmutableSet<String>, Set<String>> secondaryEmailsModification);

    abstract Function<ImmutableSet<String>, Set<String>> secondaryEmailsModification();

    public Builder clearSecondaryEmails() {
      return secondaryEmailsModification(originalSecondaryEmail -> ImmutableSet.of());
    }

    public Builder addSecondaryEmail(String secondaryEmail) {
      Function<ImmutableSet<String>, Set<String>> secondaryEmailsModification =
          secondaryEmailsModification();
      secondaryEmailsModification(
          originalSecondaryEmails ->
              Sets.union(
                  secondaryEmailsModification.apply(originalSecondaryEmails),
                  ImmutableSet.of(secondaryEmail)));
      return this;
    }

    public Builder removeSecondaryEmail(String secondaryEmail) {
      Function<ImmutableSet<String>, Set<String>> previousModification =
          secondaryEmailsModification();
      secondaryEmailsModification(
          originalSecondaryEmails ->
              Sets.difference(
                  previousModification.apply(originalSecondaryEmails),
                  ImmutableSet.of(secondaryEmail)));
      return this;
    }

    abstract Builder accountUpdater(ThrowingConsumer<TestAccountUpdate> accountUpdater);

    abstract TestAccountUpdate autoBuild();

    public void update() {
      TestAccountUpdate accountUpdate = autoBuild();
      accountUpdate.accountUpdater().acceptAndThrowSilently(accountUpdate);
    }
  }
}
