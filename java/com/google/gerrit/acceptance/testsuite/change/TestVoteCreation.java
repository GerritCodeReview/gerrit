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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelId;
import java.util.Optional;

/** Initial attributes of the vote. If not provided, arbitrary values will be used. */
@AutoValue
public abstract class TestVoteCreation {
  public abstract Optional<Account.Id> user();

  public abstract Optional<String> label();

  public abstract Optional<Integer> value();

  abstract ThrowingFunction<TestVoteCreation, TestVote> voteCreator();

  public static TestVoteCreation.Builder builder(
      ThrowingFunction<TestVoteCreation, TestVote> voteCreator) {
    return new AutoValue_TestVoteCreation.Builder().voteCreator(voteCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    /**
     * The user for which the vote should be created.
     *
     * <p>Must be an existing user account.
     *
     * <p>If not set the new vote is applied by an arbitrary user that is not the change owner.
     */
    public abstract Builder user(Account.Id user);

    /**
     * The label on which the vote should be created.
     *
     * <p>Must be an existing label.
     *
     * <p>If not set the new vote is applied on an arbitrary label.
     */
    public abstract Builder label(String label);

    /**
     * The value with which the vote should be created.
     *
     * <p>Must be a value that is allowed for the label.
     *
     * <p>If not set the new vote is applied with an arbitrary non-zero value.
     */
    public abstract Builder value(int value);

    public Builder codeReviewApproval() {
      return label(LabelId.CODE_REVIEW).value(2);
    }

    public Builder codeReviewVeto() {
      return label(LabelId.CODE_REVIEW).value(-2);
    }

    abstract TestVoteCreation.Builder voteCreator(
        ThrowingFunction<TestVoteCreation, TestVote> voteCreator);

    abstract TestVoteCreation build();

    /**
     * Creates the vote.
     *
     * @return the {@link TestVote} representing the created vote
     */
    @CanIgnoreReturnValue
    public TestVote create() {
      TestVoteCreation voteCreation = build();
      return voteCreation.voteCreator().applyAndThrowSilently(voteCreation);
    }
  }
}
