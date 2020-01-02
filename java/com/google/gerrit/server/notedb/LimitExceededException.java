// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.gerrit.exceptions.StorageException;

/**
 * A write operation was rejected because a limit would be exceeded. Limits are currently imposed
 * on:
 *
 * <ul>
 *   <li>The number of NoteDb updates per change.
 *   <li>The number of patch sets per change.
 *   <li>The number of files per change.
 * </ul>
 */
public class LimitExceededException extends StorageException {
  private static final long serialVersionUID = 1L;

  public LimitExceededException(String message) {
    super(message);
  }
}
