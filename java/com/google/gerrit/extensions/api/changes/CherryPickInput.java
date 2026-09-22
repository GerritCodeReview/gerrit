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

import java.util.Map;

public class CherryPickInput {
  public String message;
  // Cherry-pick destination branch, which will be the destination of the newly created change.
  public String destination;
  // 40-hex digit SHA-1 of the commit which will be the parent commit of the newly created change.
  public String base;
  public Integer parent;

  /**
   * Who to send email notifications to after the cherry-pick. If not specified, defaults to {@link
   * NotifyHandling#OWNER} if the resulting change is work-in-progress (see {@link
   * #workInProgress}), or {@link NotifyHandling#ALL} otherwise.
   */
  public NotifyHandling notify;

  public Map<RecipientType, NotifyInfo> notifyDetails;

  public boolean keepReviewers;
  public boolean allowConflicts;
  public String topic;
  public boolean allowEmpty;
  public Map<String, String> validationOptions;
  public String committerEmail;

  /**
   * Whether the resulting change should be marked work-in-progress.
   *
   * <p>If unset, the change is marked work-in-progress if the source change is work-in-progress or
   * if the cherry-pick produced git conflicts (see {@link #allowConflicts}). An explicit value
   * always wins over both.
   *
   * <p>If the resulting change is work-in-progress, {@link #notify} defaults to {@link
   * NotifyHandling#OWNER} instead of {@link NotifyHandling#ALL}.
   */
  public Boolean workInProgress;
}
