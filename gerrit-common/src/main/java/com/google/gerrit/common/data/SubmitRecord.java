// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Account;

import java.util.List;

/**
 * Describes the state required to submit a change.
 */
public class SubmitRecord {
  public static enum Status {
    /** The change is ready for submission. */
    OK,

    /** The change is missing a required label. */
    NOT_READY,

    /** The change has been closed. */
    CLOSED,

    /**
     * An internal server error occurred preventing computation.
     * <p>
     * Additional detail may be available in {@link SubmitRecord#errorMessage}.
     */
    RULE_ERROR;
  }

  public Status status;
  public List<Label> labels;
  public String errorMessage;

  public static class Label {
    public static enum Status {
      /**
       * This label provides what is necessary for submission.
       * <p>
       * If provided, {@link Label#appliedBy} describes the user account
       * that applied this label to the change.
       */
      OK,

      /**
       * This label prevents the change from being submitted.
       * <p>
       * If provided, {@link Label#appliedBy} describes the user account
       * that applied this label to the change.
       */
      REJECT,

      /**
       * The label is required for submission, but has not been satisfied.
       */
      NEED,

      /**
       * The label is required for submission, but is impossible to complete.
       * The likely cause is access has not been granted correctly by the
       * project owner or site administrator.
       */
      IMPOSSIBLE;
    }

    public String label;
    public Status status;
    public Account.Id appliedBy;
  }
}
