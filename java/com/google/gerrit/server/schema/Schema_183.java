// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.server.config.AllProjectsName;
import com.google.inject.Inject;

/**
 * Schema 183 for Gerrit metadata.
 *
 * <p>Upgrading to this schema version adds a new permission to the Gerrit permission system. The
 * new permission "Revert" will be default to all Registered Users.
 */
public class Schema_183 implements NoteDbSchemaVersion {

  private final GrantRevertPermission grantRevertPermission;
  private final AllProjectsName allProjectsName;

  @Inject
  public Schema_183(GrantRevertPermission grantRevertPermission, AllProjectsName allProjectsName) {
    this.grantRevertPermission = grantRevertPermission;
    this.allProjectsName = allProjectsName;
  }

  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    grantRevertPermission.execute(allProjectsName);
  }
}
