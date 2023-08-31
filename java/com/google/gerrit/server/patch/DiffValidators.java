// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;

/** Validates {@link FileDiffOutput}(s) after they are computed by the {@link DiffOperations}. */
public class DiffValidators {
  DynamicSet<DiffValidator> diffValidators;

  @Inject
  public DiffValidators(DynamicSet<DiffValidator> diffValidators) {
    this.diffValidators = diffValidators;
  }

  public void validate(FileDiffOutput fileDiffOutput)
      throws LargeObjectException, DiffNotAvailableException {
    for (DiffValidator validator : diffValidators) {
      validator.validate(fileDiffOutput);
    }
  }
}
