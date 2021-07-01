package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Optional;

public class SubmitRequirementResultAdapter extends TypeAdapter<SubmitRequirementResult> {
  private static final String NAME = "name";
  private static final String DESCRIPTION = "description";

  private static final String APPLICABILITY_EXPRESSION = "applicability_expression";
  private static final String SUBMITTABILITY_EXPRESSION = "submittability_expression";
  private static final String OVERRIDE_EXPRESSION = "override_expression";

  private static final String APPLICABILITY_RESULT = "applicability_expression_result";
  private static final String SUBMITTABILITY_RESULT = "submittability_expression_result";
  private static final String OVERRIDE_RESULT = "override_expression_result";
  private static final String STATUS = "status";
  private static final String PASSING_ATOMS = "passing_atoms";
  private static final String FAILING_ATOMS = "failing_atoms";

  private static final String SUBMIT_REQUIREMENT = "submit_requirement";
  private static final String ALLOW_OVERRIDE_IN_CHILD_PROJECTS = "allow_override_in_child_projects";

  @Override
  public void write(JsonWriter out, SubmitRequirementResult value) throws IOException {
    storeSubmitRequirement(out, value.submitRequirement());

    Optional<SubmitRequirementExpressionResult> appResult = value.applicabilityExpressionResult();
    Optional<SubmitRequirementExpressionResult> overrideResult = value.overrideExpressionResult();
    SubmitRequirementExpressionResult submittabilityResult = value.submittabilityExpressionResult();

    storeSubmitExpressionResult(out, SUBMITTABILITY_RESULT, submittabilityResult);

    if (appResult.isPresent()) {
      storeSubmitExpressionResult(out, APPLICABILITY_RESULT, appResult.get());
    }

    if (overrideResult.isPresent()) {
      storeSubmitExpressionResult(out, OVERRIDE_RESULT, overrideResult.get());
    }
  }

  @Override
  public SubmitRequirementResult read(JsonReader in) throws IOException {
    // TODO: implement
    return null;
  }

  private void storeSubmitExpressionResult(
      JsonWriter out, String name, SubmitRequirementExpressionResult result) throws IOException {
    out.name(name).beginObject().name(STATUS).value(result.status().toString());

    if (!result.passingAtoms().isEmpty()) {
      out.name(PASSING_ATOMS).beginArray();
      for (String passAtom : result.passingAtoms()) {
        out.value(passAtom);
      }
      out.endArray();
    }

    if (!result.failingAtoms().isEmpty()) {
      out.name(FAILING_ATOMS).beginArray();
      for (String failAtom : result.failingAtoms()) {
        out.value(failAtom);
      }
      out.endArray();
    }
  }

  private void storeSubmitRequirement(JsonWriter out, SubmitRequirement submitRequirement)
      throws IOException {
    out.name(SUBMIT_REQUIREMENT)
        .beginObject()
        .name(NAME)
        .value(submitRequirement.name())
        .name(SUBMITTABILITY_EXPRESSION)
        .value(submitRequirement.submittabilityExpression().expressionString())
        .name(ALLOW_OVERRIDE_IN_CHILD_PROJECTS)
        .value(submitRequirement.allowOverrideInChildProjects());
    if (submitRequirement.description().isPresent()) {
      out.name(DESCRIPTION).value(submitRequirement.description().get());
    }
    if (submitRequirement.overrideExpression().isPresent()) {
      out.name(OVERRIDE_EXPRESSION)
          .value(submitRequirement.overrideExpression().get().expressionString());
    }
    if (submitRequirement.applicabilityExpression().isPresent()) {
      out.name(APPLICABILITY_EXPRESSION)
          .value(submitRequirement.applicabilityExpression().get().expressionString());
    }
    out.endObject();
  }
}
