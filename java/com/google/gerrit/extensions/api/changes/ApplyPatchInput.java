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

package com.google.gerrit.extensions.api.changes;

import java.util.Map;

public class ApplyPatchInput {
  public String message;  // required
  public String destinationBranch;  // required
  public String patch;  // required

  // 40-hex digit SHA-1 of the commit which will be the parent commit of the newly created change.
  public String base;

  public NotifyHandling notify = NotifyHandling.ALL;
  public Map<RecipientType, NotifyInfo> notifyDetails;

  public boolean allowConflicts;
  public String topic;
  public boolean allowEmpty;
  public Map<String, String> validationOptions;
}
