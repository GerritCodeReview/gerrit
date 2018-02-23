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

import java.util.List;
import java.util.Objects;

/** Describes the state required to submit a change. */
public class SubmitRecord {
  public static boolean isSubmittable(List<SubmitRecord> in) {
    if (in == null || in.isEmpty()) {
      // If the list is null or empty, it means that this Gerrit installation does not
      // have any form of validation rules.
      // Hence, the permission system should be used to determine if the change can be merged
      // or not.
      return true;
    }

    if (in.stream().noneMatch(r -> r.status == Status.OK)) {
      // One (or more) plugins are enabled, but none said the change can be merged.
      return false;
    }

    // We can submit, unless at least one plugin prevents it.
    return in.stream().noneMatch(r -> r.status == Status.NOT_READY);
  }

  public enum Status {
    // NOTE: These values are persisted in the index, so deleting or changing
    // the name of any values requires a schema upgrade.

    /** The change is ready for submission. */
    OK,

    /** Something is preventing this change from being submitted. */
    NOT_READY,

    /** The change has been closed. */
    CLOSED,

    /** The change was submitted bypassing submit rules. */
    FORCED,

    /**
     * An internal server error occurred preventing computation.
     *
     * <p>Additional detail may be available in {@link SubmitRecord#errorMessage}.
     */
    RULE_ERROR
  }

  public Status status;
  public List<Label> labels;
  public List<SubmitRequirement> requirements;
  public String errorMessage;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(status);
    if (status == Status.RULE_ERROR && errorMessage != null) {
      sb.append('(').append(errorMessage).append(')');
    }
    sb.append('[');
    if (labels != null) {
      String delimiter = "";
      for (Label label : labels) {
        sb.append(delimiter).append(label);
        delimiter = ", ";
      }
    }
    sb.append(']');
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof SubmitRecord) {
      SubmitRecord r = (SubmitRecord) o;
      return Objects.equals(status, r.status)
          && Objects.equals(labels, r.labels)
          && Objects.equals(errorMessage, r.errorMessage);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, labels, errorMessage);
  }
}
