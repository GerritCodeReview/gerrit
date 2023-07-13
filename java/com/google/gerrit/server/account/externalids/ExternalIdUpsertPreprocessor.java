// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;

/**
 * This optional preprocessor is called in {@link ExternalIdNotes} before an update is committed.
 */
@ExtensionPoint
public interface ExternalIdUpsertPreprocessor {
  /**
   * Called when inserting or updating an external ID. {@link ExternalId#blobId()} is set. The
   * upsert can be blocked by throwing {@link com.google.gerrit.exceptions.StorageException}, e.g.
   * when a precondition or preparatory work fails.
   */
  void upsert(ExternalId extId);
}
