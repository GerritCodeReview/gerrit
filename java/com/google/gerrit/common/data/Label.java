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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Account;
import java.util.Objects;

public class Label {
  public enum Status {
    // NOTE: These values are persisted in the index, so deleting or changing
    // the name of any values requires a schema upgrade.

    /**
     * This label provides what is necessary for submission.
     *
     * <p>If provided, {@link Label#appliedBy} describes the user account that applied this label
     * to the change.
     */
    OK,

    /**
     * This label prevents the change from being submitted.
     *
     * <p>If provided, {@link Label#appliedBy} describes the user account that applied this label
     * to the change.
     */
    REJECT,

    /** The label is required for submission, but has not been satisfied. */
    NEED,

    /**
     * The label may be set, but it's neither necessary for submission nor does it block
     * submission if set.
     */
    MAY,

    /**
     * The label is required for submission, but is impossible to complete. The likely cause is
     * access has not been granted correctly by the project owner or site administrator.
     */
    IMPOSSIBLE
  }

  public String label;
  public Status status;
  public Account.Id appliedBy;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(label).append(": ").append(status);
    if (appliedBy != null) {
      sb.append(" by ").append(appliedBy);
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Label) {
      Label l = (Label) o;
      return Objects.equals(label, l.label)
          && Objects.equals(status, l.status)
          && Objects.equals(appliedBy, l.appliedBy);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, status, appliedBy);
  }
}
