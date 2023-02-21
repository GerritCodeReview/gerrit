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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import java.util.List;

/** Information for creating a new patch set from a given patch. */
public class ApplyPatchPatchSetInput {

  /** The patch to be applied. */
  public ApplyPatchInput patch;

  /**
   * The commit message for the new patch set. If not specified, a predefined message will be used.
   */
  @Nullable public String commitMessage;

  /**
   * 40-hex digit SHA-1 of the commit which will be the parent commit of the newly created patch
   * set. If set, it must be a merged commit or a change revision on the destination branch.
   * Otherwise, the target change's branch tip will be used.
   */
  @Nullable public String base;

  /**
   * The author of the new patch set. Must include both {@link AccountInput#name} and {@link
   * AccountInput#email} fields.
   */
  @Nullable public AccountInput author;

  @Nullable public List<ListChangesOption> response_format_options;
}
