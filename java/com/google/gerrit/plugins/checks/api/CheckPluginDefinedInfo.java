// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.common.PluginDefinedInfo;
import java.util.Objects;

public class CheckPluginDefinedInfo extends PluginDefinedInfo {
  public CombinedCheckState combinedCheckState;

  public CheckPluginDefinedInfo(CombinedCheckState combinedCheckState) {
    this.combinedCheckState = requireNonNull(combinedCheckState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(combinedCheckState);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CheckPluginDefinedInfo)) {
      return false;
    }
    CheckPluginDefinedInfo i = (CheckPluginDefinedInfo) o;
    return Objects.equals(combinedCheckState, i.combinedCheckState);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("combinedCheckState", combinedCheckState)
        .toString();
  }
}
