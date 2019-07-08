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

import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.client.ChangeStatus;
import java.util.Map;

public class ChangeInput {
  public String project;
  public String branch;
  public String subject;

  public String topic;
  public ChangeStatus status;
  public String baseChange;
  public Boolean newBranch;
  public MergeInput merge;

  /** Who to send email notifications to after change is created. */
  public NotifyHandling notify = NotifyHandling.ALL;

  public Map<RecipientType, NotifyInfo> notifyDetails;
}
