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

package com.google.gerrit.server.git;

import com.google.gerrit.entities.Project;
import java.io.IOException;

/** Thrown when trying to create a repository that exist. */
public class RepositoryExistsException extends IOException {
  private static final long serialVersionUID = 1L;

  /**
   * @param projectName name of the project that cannot be created
   * @param reason reason why the project cannot be created
   */
  public RepositoryExistsException(Project.NameKey projectName, String reason) {
    super(
        String.format("Repository %s exists and cannot be created. %s", projectName.get(), reason));
  }

  /**
   * @param projectName name of the project that cannot be created
   */
  public RepositoryExistsException(Project.NameKey projectName) {
    super(String.format("Repository %s exists and cannot be created.", projectName.get()));
  }
}
