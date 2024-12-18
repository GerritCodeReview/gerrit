// Copyright (C) 2014 The Android Open Source Project
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

import java.util.Map;

public class EditInfo {
  public CommitInfo commit;
  public int basePatchSetNumber;
  public String baseRevision;
  public String ref;
  public Map<String, FetchInfo> fetch;
  public Map<String, FileInfo> files;

  /**
   * Whether the change edit contains conflicts.
   *
   * <p>If {@code true}, some of the file contents of the change contain git conflict markers to
   * indicate the conflicts.
   *
   * <p>Only set if this edit info is returned in response to a request that rebases the change edit
   * (see {@link com.google.gerrit.server.restapi.change.RebaseChangeEdit}) and conflicts are
   * allowed.
   */
  public Boolean containsGitConflicts;
}
