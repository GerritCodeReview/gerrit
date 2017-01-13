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

import com.google.gerrit.extensions.client.EventInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import java.util.List;

public class ReviewerUpdateInfo extends EventInfo {
  public AccountInfo author;
  public List<ReviewerUpdateItemInfo> updates;

  public static class ReviewerUpdateItemInfo {
    public AccountInfo reviewer;
    public ReviewerState state;

    public ReviewerUpdateItemInfo(AccountInfo reviewer, ReviewerState state) {
      this.reviewer = reviewer;
      this.state = state;
    }
  }

  public ReviewerUpdateInfo() {
    this.type = EventInfoType.REVIEWER_UPDATE;
  }
}
