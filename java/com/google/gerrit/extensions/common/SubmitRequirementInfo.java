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

package com.google.gerrit.extensions.common;

import java.util.Objects;

public class SubmitRequirementInfo {
  public final String label;
  public final String fullReason;
  public final String shortReason;
  public final String status;

  public SubmitRequirementInfo(String status, String shortReason, String fullReason, String label) {
    this.status = status;
    this.shortReason = shortReason;
    this.fullReason = fullReason;
    this.label = label;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubmitRequirementInfo)) {
      return false;
    }
    SubmitRequirementInfo that = (SubmitRequirementInfo) o;
    return Objects.equals(label, that.label)
        && Objects.equals(fullReason, that.fullReason)
        && Objects.equals(shortReason, that.shortReason)
        && Objects.equals(status, that.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, fullReason, shortReason, status);
  }
}
