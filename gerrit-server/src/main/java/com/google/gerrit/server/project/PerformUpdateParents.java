// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gwtorm.client.OrmException;

import java.util.Collection;

public interface PerformUpdateParents {

  /**
   * Sets the given parent project as new parent for all given child projects.
   *
   * @param children the child projects for which the given parent project
   *        should be set as new parent
   * @param newParent the project which should be set as new parent, if
   *        <code>null</code> the wild project will be set as new parent
   * @throws OrmException
   * @throws UpdateParentsFailedException thrown in case setting the new parent
   *         has failed for any child project
   */
  public void updateParents(Collection<ProjectControl> children,
      ProjectControl newParent) throws OrmException,
      UpdateParentsFailedException;
}
