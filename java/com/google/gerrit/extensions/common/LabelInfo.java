// Copyright (C) 2014 The Android Open Source Project
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
import java.util.Map;
import java.util.Objects;

public class LabelInfo {
  public AccountInfo approved;
  public AccountInfo rejected;
  public AccountInfo recommended;
  public AccountInfo disliked;
  public List<ApprovalInfo> all;

  public Map<String, String> values;

  public Short value;
  public Short defaultValue;
  public Boolean optional;
  public Boolean blocking;

  @Override
  public boolean equals(Object o) {
    if (o instanceof LabelInfo) {
      LabelInfo labelInfo = (LabelInfo) o;
      return Objects.equals(approved, labelInfo.approved)
          && Objects.equals(rejected, labelInfo.rejected)
          && Objects.equals(recommended, labelInfo.recommended)
          && Objects.equals(disliked, labelInfo.disliked)
          && Objects.equals(all, labelInfo.all)
          && Objects.equals(values, labelInfo.values)
          && Objects.equals(value, labelInfo.value)
          && Objects.equals(defaultValue, labelInfo.defaultValue)
          && Objects.equals(optional, labelInfo.optional)
          && Objects.equals(blocking, labelInfo.blocking);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        approved,
        rejected,
        recommended,
        disliked,
        all,
        values,
        value,
        defaultValue,
        optional,
        blocking);
  }
}
