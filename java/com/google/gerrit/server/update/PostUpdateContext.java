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

package com.google.gerrit.server.update;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;

/** Context for performing the {@link BatchUpdateOp#postUpdate} phase. */
public interface PostUpdateContext extends Context {
  /**
   * Get the change data for the specified change.
   *
   * <p>If the change data has been computed previously, because the change has been indexed after
   * an update or because this method has been invoked before, the cached change data instance is
   * returned.
   *
   * @param changeId the ID of the change for which the change data should be returned
   */
  ChangeData getChangeData(Project.NameKey projectName, Change.Id changeId);

  default ChangeData getChangeData(Change change) {
    return getChangeData(change.getProject(), change.getId());
  }

  default ChangeData getChangeData(ChangeNotes changeNotes) {
    return getChangeData(changeNotes.getChange());
  }
}
