// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.server.project.CreateProjectArgs;

/** Listener to provide validation on project creation. */
@ExtensionPoint
public interface ProjectCreationValidationListener {
  /**
   * Project creation validation.
   *
   * <p>Invoked by Gerrit just before a new project is going to be created.
   *
   * @param args arguments for the project creation
   * @throws ValidationException if validation fails
   */
  void validateNewProject(CreateProjectArgs args) throws ValidationException;
}
