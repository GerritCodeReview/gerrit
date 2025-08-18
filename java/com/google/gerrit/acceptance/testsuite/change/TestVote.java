// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Account;

/** Representation of a vote used for testing purposes. */
@AutoValue
public abstract class TestVote {
  /** The user that applied the vote. */
  public abstract Account.Id userId();

  /** The label of the vote. */
  public abstract String label();

  /** The value of the vote. */
  public abstract int value();

  static Builder builder() {
    return new AutoValue_TestVote.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder userId(Account.Id userId);

    abstract Builder label(String label);

    abstract Builder value(int value);

    abstract TestVote build();
  }
}
