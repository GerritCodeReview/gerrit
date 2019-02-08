// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.ReviewerState;
import java.util.Arrays;
import org.eclipse.jgit.revwalk.FooterKey;

/**
 * State of a reviewer on a change.
 *
 * <p>The natural order is significant: when requesting to add a user as multiple states in a single
 * operation, the higher value wins.
 */
public enum ReviewerStateInternal {
  /** The user was previously a reviewer on the change, but was removed. */
  REMOVED(new FooterKey("Removed"), ReviewerState.REMOVED),

  /** The reviewer was added to the change, but has not voted. */
  CC(new FooterKey("CC"), ReviewerState.CC),

  /** The user has contributed at least one nonzero vote on the change. */
  REVIEWER(new FooterKey("Reviewer"), ReviewerState.REVIEWER);

  public static ReviewerStateInternal fromReviewerState(ReviewerState state) {
    return ReviewerStateInternal.values()[state.ordinal()];
  }

  static {
    // Ensure enum names match, in order.
    ImmutableList<String> internalNames =
        Arrays.stream(ReviewerStateInternal.values()).map(Enum::name).collect(toImmutableList());
    ImmutableList<String> externalNames =
        Arrays.stream(ReviewerState.values()).map(Enum::name).collect(toImmutableList());
    checkState(
        internalNames.equals(externalNames),
        "Mismatched reviewer state mapping:\ninternal: %s\nexternal: %s",
        internalNames,
        externalNames);
  }

  private final FooterKey footerKey;
  private final ReviewerState state;

  ReviewerStateInternal(FooterKey footerKey, ReviewerState state) {
    this.footerKey = footerKey;
    this.state = state;
  }

  FooterKey getFooterKey() {
    return footerKey;
  }

  FooterKey getByEmailFooterKey() {
    return new FooterKey(footerKey.getName() + "-email");
  }

  public ReviewerState asReviewerState() {
    return state;
  }
}
