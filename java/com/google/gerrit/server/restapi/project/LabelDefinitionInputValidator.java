// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.extensions.common.LabelDefinitionInput;
import com.google.gerrit.extensions.restapi.BadRequestException;

/** Validates {@link LabelDefinitionInput}'s. */
class LabelDefinitionInputValidator {
  static void validate(LabelDefinitionInput input) throws BadRequestException {
    validate(/* labelName= */ null, input);
  }

  static void validate(@Nullable String labelName, LabelDefinitionInput input)
      throws BadRequestException {
    if (input.function != null) {
      if (LabelFunction.ANY_WITH_BLOCK.getFunctionName().equals(input.function)
          || LabelFunction.MAX_WITH_BLOCK.getFunctionName().equals(input.function)
          || LabelFunction.MAX_NO_BLOCK.getFunctionName().equals(input.function)) {
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("Function %s", input.function));
        if (labelName != null) {
          msg.append(String.format(" of label %s", labelName));
        }
        msg.append(" is deprecated.");
        msg.append(
            String.format(
                " The function can only be set to %s. Use submit requirements instead of label"
                    + " functions.",
                ImmutableList.of(
                    LabelFunction.NO_BLOCK.getFunctionName(),
                    LabelFunction.NO_OP.getFunctionName(),
                    LabelFunction.PATCH_SET_LOCK.getFunctionName())));
        throw new BadRequestException(msg.toString());
      }
    }
  }

  private LabelDefinitionInputValidator() {}
}
