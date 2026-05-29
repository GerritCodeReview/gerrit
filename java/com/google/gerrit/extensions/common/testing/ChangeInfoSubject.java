// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.auto.value.AutoValue;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/** A Truth subject for {@link ChangeInfo} instances. */
public class ChangeInfoSubject extends Subject {
  private final ChangeInfo changeInfo;

  public static ChangeInfoSubject assertThat(ChangeInfo changeInfo) {
    return assertAbout(changes()).that(changeInfo);
  }

  public static Factory<ChangeInfoSubject, ChangeInfo> changes() {
    return ChangeInfoSubject::new;
  }

  private ChangeInfoSubject(FailureMetadata metadata, ChangeInfo changeInfo) {
    super(metadata, changeInfo);
    this.changeInfo = changeInfo;
  }

  private ChangeInfo changeInfo() {
    isNotNull();
    return changeInfo;
  }

  /**
   * Asserts that the ChangeInfo has exactly the provided votes or fails.
   *
   * <p>The 0-value votes and non-existing votes are treated as equal votes. In other word, if
   * expectedVote has value zero, then the actual vote can be either 0 or not present at all and
   * vice-verse.
   */
  public void hasExactlyVotes(Vote... expectedVotes) {
    assertWithMessage("ChangeInfo.labels is null").that(changeInfo().labels).isNotNull();
    Set<Vote> actualVotes = getAllNonZeroVotes(changeInfo().labels);
    Arrays.stream(expectedVotes)
        .filter(v -> v.value() == 0 && !actualVotes.contains(v))
        .forEach(actualVotes::add);
    assertWithMessage("Votes are different.")
        .that(actualVotes)
        .containsExactlyElementsIn(expectedVotes);
  }

  /** Assers that the ChangeInfo has no votes or fails. */
  public void hasNoVotes() {
    hasExactlyVotes();
  }

  private static Set<Vote> getAllNonZeroVotes(Map<String, LabelInfo> labels) {
    Set<Vote> votes = new HashSet<>();
    for (Entry<String, LabelInfo> entry : labels.entrySet()) {
      List<ApprovalInfo> allApprovals = entry.getValue().all;
      if (allApprovals == null) {
        continue;
      }
      allApprovals.stream()
          .filter(approvalInfo -> !approvalInfo.value.equals(0))
          .map(
              apprvoalInfo ->
                  vote(entry.getKey(), Account.id(apprvoalInfo._accountId), apprvoalInfo.value))
          .forEach(votes::add);
    }
    return votes;
  }

  public static Vote vote(String labelId, Account.Id accountId, int value) {
    return Vote.create(labelId, accountId, value);
  }

  @AutoValue
  public abstract static class Vote {
    static Vote create(String labelId, Account.Id accountId, int value) {
      return new AutoValue_ChangeInfoSubject_Vote(labelId, accountId, value);
    }

    public abstract String labelId();

    public abstract Account.Id accountId();

    public abstract int value();
  }
}
