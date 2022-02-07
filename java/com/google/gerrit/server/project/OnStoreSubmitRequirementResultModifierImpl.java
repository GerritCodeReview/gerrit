// Copyright (C) 2022 The Android Open Source Project
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
package com.google.gerrit.server.project;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.ChangeContext;

/**
 * Default implementation of {@link OnStoreSubmitRequirementResultModifier} that does not
 * re-evaluate {@link SubmitRequirementResult}.
 */
public class OnStoreSubmitRequirementResultModifierImpl
    implements OnStoreSubmitRequirementResultModifier {
  @Override
  public SubmitRequirementResult modifyResultOnStore(
      SubmitRequirement submitRequirement,
      SubmitRequirementResult result,
      ChangeData cd,
      ChangeContext ctx) {
    return result;
  }
}
