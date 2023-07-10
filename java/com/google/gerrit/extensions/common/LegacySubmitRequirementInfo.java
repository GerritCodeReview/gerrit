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

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class LegacySubmitRequirementInfo {
  public String status;
  public String fallbackText;
  public String type;

  public LegacySubmitRequirementInfo(String status, String fallbackText, String type) {
    this.status = status;
    this.fallbackText = fallbackText;
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LegacySubmitRequirementInfo)) {
      return false;
    }
    LegacySubmitRequirementInfo that = (LegacySubmitRequirementInfo) o;
    return Objects.equals(status, that.status)
        && Objects.equals(fallbackText, that.fallbackText)
        && Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, fallbackText, type);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("fallbackText", fallbackText)
        .add("type", type)
        .toString();
  }

  public LegacySubmitRequirementInfo() {}
}
