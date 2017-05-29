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
   * The name of the strategy {@link org.eclipse.jgit.merge.MergeStrategy} which will be used for
   * merging. Allowed values are `recursive`, `resolve`, `simple-two-way-in-core`, `ours` and
   * `theirs`. If not set, the project setting will be used.
   */
  public String strategy;

  public NotifyHandling notify = NotifyHandling.NONE;
  public Map<RecipientType, NotifyInfo> notifyDetails;
}
