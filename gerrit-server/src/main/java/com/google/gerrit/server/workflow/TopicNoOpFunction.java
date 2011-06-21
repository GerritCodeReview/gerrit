// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.workflow;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.server.CurrentUser;

/** A function that does nothing. */
public class TopicNoOpFunction extends TopicCategoryFunction {
  public static String NAME = "NoOp";

  @Override
  public void run(final ApprovalType at, final TopicFunctionState state) {
  }

  @Override
  public boolean isValid(final CurrentUser user, final ApprovalType at,
      final TopicFunctionState state) {
    return false;
  }
}
