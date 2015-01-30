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

public class ProblemInfo {
  public static enum Status {
    FIXED, FIX_FAILED;
  }

  public String message;
  public Status status;
  public String outcome;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName())
        .append('[').append(message);
    if (status != null || outcome != null) {
      sb.append(" (").append(status).append(": ").append(outcome)
          .append(')');
    }
    return sb.append(']').toString();
  }
}
