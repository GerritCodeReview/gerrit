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

package com.google.gerrit.common.data;

import com.google.gerrit.extensions.client.Side;

public enum DiffType {
  AUTO_MERGE(1, ""), FIRST_PARENT(2, "FP");

  public final short side;
  public final String encoded;

  private DiffType(int side, String encoded) {
    this.side = (short) side;
    this.encoded = encoded;
  }

  public static DiffType fromSide(Side side) {
    if (side == Side.FIRST_PARENT) {
     return DiffType.FIRST_PARENT;
    }
    if (side == Side.REVISION) {
      return DiffType.AUTO_MERGE;
    }
    return null;
  }
}