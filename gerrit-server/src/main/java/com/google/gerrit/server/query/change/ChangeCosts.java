// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

public class ChangeCosts {
  public static final int IDS_MEMORY = 1;
  public static final int CHANGES_SCAN = 2;
  public static final int TR_SCAN = 20;
  public static final int APPROVALS_SCAN = 30;
  public static final int PATCH_SETS_SCAN = 30;

  /** Estimated matches for a Change-Id string. */
  public static final int CARD_KEY = 5;

  /** Estimated matches for a commit SHA-1 string. */
  public static final int CARD_COMMIT = 5;

  /** Estimated matches for a tracking/bug id string. */
  public static final int CARD_TRACKING_IDS = 5;

  public static int cost(int cost, int cardinality) {
    return Math.max(1, cost) * Math.max(0, cardinality);
  }

  private ChangeCosts() {
  }
}
