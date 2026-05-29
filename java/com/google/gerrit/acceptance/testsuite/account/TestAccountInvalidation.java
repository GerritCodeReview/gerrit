// Copyright (C) 2020 The Android Open Source Project
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
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import java.util.Optional;

/**
 * API to invalidate accounts in tests.
 *
 * <p>This allows to test Gerrit behavior when there is invalid account data in NoteDb (e.g.
 * accounts with duplicate emails).
 */
@AutoValue
public abstract class TestAccountInvalidation {
  /**
   * Sets a preferred email for the account for which the account doesn't have an external ID.
   *
   * <p>This allows to set the same preferred email for multiple accounts so that the email becomes
   * ambiguous.
   */
  public abstract Optional<String> preferredEmailWithoutExternalId();

  abstract ThrowingConsumer<TestAccountInvalidation> accountInvalidator();

  public static Builder builder(ThrowingConsumer<TestAccountInvalidation> accountInvalidator) {
    return new AutoValue_TestAccountInvalidation.Builder().accountInvalidator(accountInvalidator);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder preferredEmailWithoutExternalId(String preferredEmail);

    abstract Builder accountInvalidator(
        ThrowingConsumer<TestAccountInvalidation> accountInvalidator);

    abstract TestAccountInvalidation autoBuild();

    /** Executes the account invalidation as specified. */
    public void invalidate() {
      TestAccountInvalidation accountInvalidation = autoBuild();
      accountInvalidation.accountInvalidator().acceptAndThrowSilently(accountInvalidation);
    }
  }
}
