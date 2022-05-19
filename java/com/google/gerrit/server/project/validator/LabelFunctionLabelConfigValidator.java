package com.google.gerrit.server.project.validator;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.server.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

// Reject label functions if they are deleted or set to something other than NO_OP or NO_BLOCK
public class LabelFunctionLabelConfigValidator implements LabelConfigValidatorChecker {
  @Override
  public List<String> validate(Config newConfig, @Nullable Config oldConfig) {
    List<String> validationMessages = new ArrayList<>();
    for (String labelName : newConfig.getSubsections(ProjectConfig.LABEL)) {
      if (flagChangedOrNewlySet(newConfig, oldConfig, labelName, ProjectConfig.KEY_FUNCTION)) {
        String fnName =
            newConfig.getString(ProjectConfig.LABEL, labelName, ProjectConfig.KEY_FUNCTION);
        Optional<LabelFunction> labelFn = LabelFunction.parse(fnName);
        if (labelFn.isPresent() && !isAllowed(labelFn.get())) {
          validationMessages.add(
              String.format(
                  "Value '%s' of '%s.%s.%s' is not allowed and cannot be set."
                      + " Label functions can only be set to {%s, %s}."
                      + " Use submit requirements instead of label functions.",
                  fnName,
                  ProjectConfig.LABEL,
                  labelName,
                  ProjectConfig.KEY_FUNCTION,
                  LabelFunction.NO_BLOCK,
                  LabelFunction.NO_OP));
        }
      }
      if (flagDeleted(newConfig, oldConfig, labelName, ProjectConfig.KEY_FUNCTION)) {
        validationMessages.add(
            String.format(
                "Cannot delete '%s.%s.%s'."
                    + " Label functions can only be set to {%s, %s}."
                    + " Use submit requirements instead of label functions.",
                ProjectConfig.LABEL,
                labelName,
                ProjectConfig.KEY_FUNCTION,
                LabelFunction.NO_BLOCK,
                LabelFunction.NO_OP));
      }
    }
    return validationMessages;
  }

  private static boolean isAllowed(LabelFunction labelFunction) {
    return labelFunction.equals(LabelFunction.NO_BLOCK)
        || labelFunction.equals(LabelFunction.NO_OP);
  }
}
