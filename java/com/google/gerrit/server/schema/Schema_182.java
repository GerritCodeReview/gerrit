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

package com.google.gerrit.server.schema;

public class Schema_182 implements NoteDbSchemaVersion {
  private static final String MESSAGE = "Remove unused \"use flash clipboard\" preference";

  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    ui.message(MESSAGE);
    AccountPreferencesMigration.migrate(
        MESSAGE, args, c -> c.unset("general", null, "useFlashClipboard"));
  }
}
