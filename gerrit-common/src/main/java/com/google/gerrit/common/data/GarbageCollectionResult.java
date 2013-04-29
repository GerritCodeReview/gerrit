// Copyright (C) 2012 The Android Open Source Project
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

public class GarbageCollectionResult {
  protected List<Error> errors;

  public GarbageCollectionResult() {
    errors = new ArrayList<Error>();
  }

  public void addError(Error e) {
    errors.add(e);
  }

  public List<Error> getErrors() {
    return errors;
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public static class Error {
    public static enum Type {
      /** Git garbage collection was already scheduled for this project */
      GC_ALREADY_SCHEDULED,

      /** The repository was not found. */
      REPOSITORY_NOT_FOUND,

      /** The Git garbage collection failed. */
      GC_FAILED
    }

    protected Type type;
    protected Project.NameKey projectName;

    protected Error() {
    }

    public Error(Type type, Project.NameKey projectName) {
      this.type = type;
      this.projectName = projectName;
    }

    public Type getType() {
      return type;
    }

    public Project.NameKey getProjectName() {
      return projectName;
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(type);
      if (projectName != null) {
        b.append(" ").append(projectName);
      }
      return b.toString();
    }
  }
}
