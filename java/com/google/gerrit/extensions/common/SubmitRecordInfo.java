// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import java.util.List;
import java.util.Objects;

/** API response containing a {@link com.google.gerrit.entities.SubmitRecord} entity. */
public class SubmitRecordInfo {
  public enum Status {
    OK,
    NOT_READY,
    CLOSED,
    FORCED,
    RULE_ERROR
  }

  public static class Label {
    public enum Status {
      OK,
      REJECT,
      NEED,
      MAY,
      IMPOSSIBLE
    }

    public String label;
    public Label.Status status;
    public AccountInfo appliedBy;

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Label)) {
        return false;
      }
      Label that = (Label) o;
      return Objects.equals(label, that.label)
          && status == that.status
          && Objects.equals(appliedBy, that.appliedBy);
    }

    @Override
    public int hashCode() {
      return Objects.hash(label, status, appliedBy);
    }
  }

  public String ruleName;
  public Status status;
  public List<Label> labels;
  public List<LegacySubmitRequirementInfo> requirements;
  public String errorMessage;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubmitRecordInfo)) {
      return false;
    }
    SubmitRecordInfo that = (SubmitRecordInfo) o;
    return Objects.equals(ruleName, that.ruleName)
        && status == that.status
        && Objects.equals(labels, that.labels)
        && Objects.equals(requirements, that.requirements)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ruleName, status, labels, requirements, errorMessage);
  }
}
