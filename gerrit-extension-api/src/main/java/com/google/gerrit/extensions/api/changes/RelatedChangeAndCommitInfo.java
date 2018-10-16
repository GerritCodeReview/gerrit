// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.common.CommitInfo;

public class RelatedChangeAndCommitInfo {
  public String project;
  public String changeId;
  public CommitInfo commit;
  public Integer _changeNumber;
  public Integer _revisionNumber;
  public Integer _currentRevisionNumber;
  public String status;

  public RelatedChangeAndCommitInfo() {}

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("project", project)
        .add("changeId", changeId)
        .add("commit", commit)
        .add("_changeNumber", _changeNumber)
        .add("_revisionNumber", _revisionNumber)
        .add("_currentRevisionNumber", _currentRevisionNumber)
        .add("status", status)
        .toString();
  }
}
