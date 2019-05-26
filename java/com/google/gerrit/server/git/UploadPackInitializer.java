// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import org.eclipse.jgit.transport.UploadPack;

@ExtensionPoint
public interface UploadPackInitializer {

  /**
   * UploadPack initialization.
   *
   * <p>Invoked by Gerrit when a new UploadPack instance is created and just before it is used.
   * Implementors will usually call setXXX methods on the uploadPack parameter in order to set
   * additional properties on it.
   *
   * @param project project for which the UploadPack is created
   * @param uploadPack the UploadPack instance which is being initialized
   */
  void init(Project.NameKey project, UploadPack uploadPack);
}
