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
import java.util.Map;
import java.util.Objects;

public class TestSubmitRuleInfo {
  /** @see com.google.gerrit.entities.SubmitRecord.Status */
  public String status;

  public String errorMessage;
  public Map<String, AccountInfo> ok;
  public Map<String, AccountInfo> reject;
  public Map<String, None> need;
  public Map<String, AccountInfo> may;
  public Map<String, None> impossible;

  public static class None {
    private None() {}

    public static final None INSTANCE = new None();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof TestSubmitRuleInfo) {
      TestSubmitRuleInfo other = (TestSubmitRuleInfo) o;
      return Objects.equals(status, other.status)
          && Objects.equals(errorMessage, other.errorMessage)
          && Objects.equals(ok, other.ok)
          && Objects.equals(reject, other.reject)
          && Objects.equals(need, other.need)
          && Objects.equals(may, other.may)
          && Objects.equals(impossible, other.impossible);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, errorMessage, ok, reject, need, may, impossible);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("status", status)
        .add("errorMessage", errorMessage)
        .add("ok", ok)
        .add("reject", reject)
        .add("need", need)
        .add("may", may)
        .add("impossible", impossible)
        .toString();
  }
}
