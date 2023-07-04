// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.proto.ProtoField;
import java.util.Objects;

public class VotingRangeInfo {

  @ProtoField(protoTag = 1)
  public int min;

  @ProtoField(protoTag = 2)
  public int max;

  public VotingRangeInfo(int min, int max) {
    this.min = min;
    this.max = max;
  }

  public VotingRangeInfo() {}

  @Override
  public boolean equals(Object o) {
    if (o instanceof VotingRangeInfo) {
      VotingRangeInfo votingRangeInfo = (VotingRangeInfo) o;
      return min == votingRangeInfo.min && max == votingRangeInfo.max;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max);
  }
}
