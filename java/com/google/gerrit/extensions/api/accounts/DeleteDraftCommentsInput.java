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

package com.google.gerrit.extensions.api.accounts;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.DefaultInput;

public class DeleteDraftCommentsInput {
  /**
   * Delete comments only on changes that match this query.
   *
   * <p>If null or empty, delete comments on all changes.
   */
  @DefaultInput public String query;

  public DeleteDraftCommentsInput() {
    this(null);
  }

  public DeleteDraftCommentsInput(@Nullable String query) {
    this.query = query;
  }
}
