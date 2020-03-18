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

package com.google.gerrit.server.group.db;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

/**
 * A representation of a group in NoteDb
 *
 * <p>This class has the exact same properties of the parent class {@link GroupConfig} with an extra
 * ObjectId field pointing to a specific commit in the group ref, giving this class the ability to
 * point to a previous state of the group and not necessarily the latest
 */
public class VersionedGroupConfig extends GroupConfig {
  private final ObjectId objectId;

  protected VersionedGroupConfig(AccountGroup.UUID groupUuid, ObjectId objectId) {
    super(groupUuid);
    this.objectId = objectId;
  }

  @Override
  public void load(Project.NameKey projectName, Repository db)
      throws IOException, ConfigInvalidException {
    load(projectName, db, objectId);
  }
}
