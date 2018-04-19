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
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import java.util.Optional;

@AutoValue
public abstract class TestAccountUpdate {
  public abstract Optional<String> fullname();

  public abstract Optional<String> httpPassword();

  public abstract Optional<String> preferredEmail();

  public abstract Optional<String> username();

  public abstract Optional<String> status();

  abstract ThrowingFunction<TestAccountUpdate, TestAccount> accountUpdater();

  public static Builder builder(ThrowingFunction<TestAccountUpdate, TestAccount> accountUpdater) {
    return new AutoValue_TestAccountUpdate.Builder()
        .accountUpdater(accountUpdater)
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

    abstract Builder accountUpdater(
        ThrowingFunction<TestAccountUpdate, TestAccount> accountUpdater);

    abstract TestAccountUpdate autoBuild();

    public TestAccount update() throws Exception {
      TestAccountUpdate accountUpdate = autoBuild();
      return accountUpdate.accountUpdater().apply(accountUpdate);
    }
  }
}
