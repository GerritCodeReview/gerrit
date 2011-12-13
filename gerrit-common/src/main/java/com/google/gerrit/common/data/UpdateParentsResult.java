// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Project;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from updating the parent project of a project.
 */
public class UpdateParentsResult {
  protected List<Error> errors;

  public UpdateParentsResult() {
    errors = new ArrayList<Error>();
  }

  public void addError(final Error e) {
    errors.add(e);
  }

  public List<Error> getErrors() {
    return errors;
  }

  public static class Error {
    public static enum Type {
      /** Not permitted to update the parent project of the project. */
      UPDATE_NOT_PERMITTED,

      /** Updating the child project failed. */
      PROJECT_UPDATE_FAILED,

      /** Child project not found. */
      PROJECT_NOT_FOUND,

      /** Parent project not found. */
      PARENT_PROJECT_NOT_FOUND,

      /**
       * It's not possible to set a parent project for this project (e.g. it is
       * the wild project that cannot have a parent).
       */
      PARENT_CANNOT_BE_SET,

      /** Cycle exists in the parent pointers. */
      CYCLE_EXISTS
    }

    protected Type type;
    protected Project.NameKey projectName;
    protected Project.NameKey parentProjectName;

    protected Error() {
    }

    public Error(final Type type, final Project.NameKey projectName,
        final Project.NameKey parentProjectName) {
      this.type = type;
      this.projectName = projectName;
    }

    public Type getType() {
      return type;
    }

    public Project.NameKey getProjectName() {
      return projectName;
    }

    public Project.NameKey getParentProjectName() {
      return parentProjectName;
    }

    @Override
    public String toString() {
      return type + " " + projectName + " " + parentProjectName;
    }
  }
}
