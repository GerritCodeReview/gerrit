// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.extensions.api.changes;

import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;

import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.DefaultInput;

public class AddReviewerInput {
  @DefaultInput public String reviewer;
  public Boolean confirmed;
  public ReviewerState state;
  public NotifyHandling notify;

  public boolean confirmed() {
    return (confirmed != null) ? confirmed : false;
  }

  public ReviewerState state() {
    return (state != null) ? state : REVIEWER;
  }
}
