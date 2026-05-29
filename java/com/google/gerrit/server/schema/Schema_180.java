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

package com.google.gerrit.server.schema;

/**
 * Schema 180 for Gerrit metadata.
 *
 * <p>180 is the first schema version that is supported by NoteDb. All previous schema versions were
 * for ReviewDb. Since ReviewDb no longer exists those schema versions have been deleted.
 *
 * <p>Upgrading to this schema version creates the {@code refs/meta/version} ref in NoteDb that
 * stores the number of the current schema version.
 */
public class Schema_180 implements NoteDbSchemaVersion {
  @Override
  public void upgrade(Arguments args, UpdateUI ui) {
    // Do nothing; only used to populate the version ref, which is done by the caller.
  }
}
