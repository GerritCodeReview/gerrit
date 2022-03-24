// Copyright (C) 2022 The Android Open Source Project
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
package com.google.gerrit.entities;

public class ChangeSizeBucket {
  /**
   * A human-readable change size bucket.
   *
   * <p>Should be kept in sync with
   * polygerrit-ui/app/elements/change-list/gr-change-list-item/gr-change-list-item.ts
   */
  private enum BucketThresholds {
    // Same as gr-change-list-item.ts::ChangeSize
    XS(10),
    SMALL(50),
    MEDIUM(250),
    LARGE(1000);

    public final long delta;

    BucketThresholds(long delta) {
      this.delta = delta;
    }
  }

  public static String getChangeSizeBucket(long delta) {
    // Same as gr-change-list-item.ts::computeChangeSize()
    if (delta == 0) {
      return "NoOp";
    } else if (delta < BucketThresholds.XS.delta) {
      return "XS";
    } else if (delta < BucketThresholds.SMALL.delta) {
      return "S";
    } else if (delta < BucketThresholds.MEDIUM.delta) {
      return "M";
    } else if (delta < BucketThresholds.LARGE.delta) {
      return "L";
    }
    return "XL";
  }
}
