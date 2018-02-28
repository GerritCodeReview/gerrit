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

import com.google.gerrit.common.Nullable;

/** Describes a requirement to submit a change. */
public class SubmitRequirement {
  private String shortReason;
  private String fullReason;
  @Nullable private String label;

  public static Builder builder() {
    return new Builder();
  }

  public String shortReason() {
    return shortReason;
  }

  public String fullReason() {
    return fullReason;
  }

  public @Nullable String label() {
    return label;
  }

  public static final class Builder {
    private String shortReason;
    private String fullReason;
    private String label;

    private Builder() {}

    public Builder setShortReason(String shortReason) {
      this.shortReason = shortReason;
      return this;
    }

    public Builder setFullReason(String fullReason) {
      this.fullReason = fullReason;
      return this;
    }

    public Builder setLabel(String label) {
      this.label = label;
      return this;
    }

    public SubmitRequirement build() {
      SubmitRequirement submitRequirement = new SubmitRequirement();
      submitRequirement.label = this.label;
      submitRequirement.fullReason = this.fullReason;
      submitRequirement.shortReason = this.shortReason;
      return submitRequirement;
    }
  }
}
