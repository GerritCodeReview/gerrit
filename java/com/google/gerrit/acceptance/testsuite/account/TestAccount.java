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
import com.google.gerrit.reviewdb.client.Account;
import java.util.Optional;

@AutoValue
public abstract class TestAccount {
  public abstract Account.Id accountId();

  public abstract Optional<String> fullname();

  public abstract Optional<String> preferredEmail();

  public abstract Optional<String> username();

  static Builder builder() {
    return new AutoValue_TestAccount.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder accountId(Account.Id accountId);

    abstract Builder fullname(Optional<String> fullname);

    abstract Builder preferredEmail(Optional<String> fullname);

    abstract Builder username(Optional<String> username);

    abstract TestAccount build();
  }
}
