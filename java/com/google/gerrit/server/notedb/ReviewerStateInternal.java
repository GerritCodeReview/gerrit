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

import com.google.gerrit.extensions.client.ReviewerState;
import java.util.Arrays;
import org.eclipse.jgit.revwalk.FooterKey;

/** State of a reviewer on a change. */
public enum ReviewerStateInternal {
  /** The user has contributed at least one nonzero vote on the change. */
  REVIEWER("Reviewer", ReviewerState.REVIEWER),

  /** The reviewer was added to the change, but has not voted. */
  CC("CC", ReviewerState.CC),

  /** The user was previously a reviewer on the change, but was removed. */
  REMOVED("Removed", ReviewerState.REMOVED);

  public static ReviewerStateInternal fromReviewerState(ReviewerState state) {
    return ReviewerStateInternal.values()[state.ordinal()];
  }

  static {
    boolean ok = true;
    if (ReviewerStateInternal.values().length != ReviewerState.values().length) {
      ok = false;
    }
    for (int i = 0; i < ReviewerStateInternal.values().length; i++) {
      ok &= ReviewerState.values()[i].equals(ReviewerStateInternal.values()[i].state);
    }
    if (!ok) {
      throw new IllegalStateException(
          "Mismatched reviewer state mapping: "
              + Arrays.asList(ReviewerStateInternal.values())
              + " != "
              + Arrays.asList(ReviewerState.values()));
    }
  }

  private final String footer;
  private final ReviewerState state;

  ReviewerStateInternal(String footer, ReviewerState state) {
    this.footer = footer;
    this.state = state;
  }

  FooterKey getFooterKey() {
    return new FooterKey(footer);
  }

  FooterKey getByEmailFooterKey() {
    return new FooterKey(footer + "-email");
  }

  public ReviewerState asReviewerState() {
    return state;
  }
}
