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
                    LabelFunction.NO_OP.getFunctionName())));
        throw new BadRequestException(msg.toString());
      }
    }
  }

  private LabelDefinitionInputValidator() {}
}
