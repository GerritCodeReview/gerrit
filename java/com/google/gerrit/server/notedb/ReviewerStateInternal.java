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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.client.ReviewerState;
import java.util.Arrays;
import org.eclipse.jgit.revwalk.FooterKey;

/** State of a reviewer on a change. */
public enum ReviewerStateInternal {
  /** The user has contributed at least one nonzero vote on the change. */
  REVIEWER(ReviewerState.REVIEWER),

  /** The reviewer was added to the change, but has not voted. */
  CC(ReviewerState.CC),

  /** The user was previously a reviewer on the change, but was removed. */
  REMOVED(ReviewerState.REMOVED);

  public static final ImmutableList<ReviewerStateInternal> ALL_STATES =
      ImmutableList.copyOf(values());

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

  private final ReviewerState state;

  ReviewerStateInternal(ReviewerState state) {
    this.state = state;
  }

  FooterKey getFooterKey() {
    switch (this) {
      case REVIEWER:
        return ChangeNoteFooters.FOOTER_REVIEWER;
      case CC:
        return ChangeNoteFooters.FOOTER_CC;
      case REMOVED:
        return ChangeNoteFooters.FOOTER_REMOVED;
    }
    throw new IllegalStateException("unhandled state: " + this);
  }

  FooterKey getByEmailFooterKey() {
    switch (this) {
      case REVIEWER:
        return ChangeNoteFooters.FOOTER_REVIEWER_EMAIL;
      case CC:
        return ChangeNoteFooters.FOOTER_CC_EMAIL;
      case REMOVED:
        return ChangeNoteFooters.FOOTER_REMOVED_EMAIL;
    }
    throw new IllegalStateException("unhandled state: " + this);
  }

  public ReviewerState asReviewerState() {
    return state;
  }
}
