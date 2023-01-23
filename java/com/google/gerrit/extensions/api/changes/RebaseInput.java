// Copyright (C) 2015 The Android Open Source Project
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

public class RebaseInput {
  public String base;

  /**
   * Whether the rebase should succeed if there are conflicts.
   *
   * <p>If there are conflicts the file contents of the rebased change contain git conflict markers
   * to indicate the conflicts.
   */
  public boolean allowConflicts;

  /**
   * Whether the rebase should be done on behalf of the uploader.
   *
   * <p>This means the uploader of the current patch set will also be the uploader of the rebased
   * patch set. The calling user will be recorded as the real user.
   *
   * <p>Rebasing on behalf of the uploader is only supported for trivial rebases. This means this
   * option cannot be combined with the {@link #allowConflicts} option.
   *
   * <p>In addition, rebasing on behalf of the uploader is only supported for the current patch set
   * of a change and not when rebasing a chain.
   *
   * <p>Using this option is not supported when rebasing a chain via the Rebase Chain REST endpoint.
   */
  public boolean onBehalfOfUploader;

  public Map<String, String> validationOptions;
}
