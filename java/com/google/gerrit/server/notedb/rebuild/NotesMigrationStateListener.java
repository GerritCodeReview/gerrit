// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb.rebuild;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.notedb.NotesMigrationState;
import java.io.IOException;

/** Listener for state changes performed by {@link OnlineNoteDbMigrator}. */
@ExtensionPoint
public interface NotesMigrationStateListener {
  /**
   * Invoked just before saving the new migration state.
   *
   * @param oldState state prior to this state change.
   * @param newState state after this state change.
   * @throws IOException if an error occurred, which will cause the migration to abort. Exceptions
   *     that should be considered non-fatal must be caught (and ideally logged) by the
   *     implementation rather than thrown.
   */
  void preStateChange(NotesMigrationState oldState, NotesMigrationState newState)
      throws IOException;
}
