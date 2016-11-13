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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

/**
 * This exception is thrown if a project cannot be created because a project with the same name in a
 * different case already exists. This can only happen if the OS has a case insensitive file system
 * (e.g. Windows), because in this case the name for the git repository in the file system is
 * already occupied by the existing project.
 */
public class RepositoryCaseMismatchException extends RepositoryNotFoundException {

  private static final long serialVersionUID = 1L;

  /** @param projectName name of the project that cannot be created */
  public RepositoryCaseMismatchException(final Project.NameKey projectName) {
    super("Name occupied in other case. Project " + projectName.get() + " cannot be created.");
  }
}
