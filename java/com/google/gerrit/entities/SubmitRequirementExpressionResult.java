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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Result of evaluating a submit requirement expression on a given Change. */
@AutoValue
public abstract class SubmitRequirementExpressionResult {

  /** Submit requirement expression for which this result is evaluated. */
  public abstract SubmitRequirementExpression expression();

  /** Status of evaluation. */
  public abstract Status status();

  /**
   * Optional error message. Populated if the evaluator fails to evaluate the expression for a
   * certain change.
   */
  public abstract Optional<String> errorMessage();

  /**
   * List leaf predicates that are fulfilled, for example the expression
   *
   * <p><i>label:Code-Review=+2 and branch:refs/heads/master</i>
   *
   * <p>has two leaf predicates:
   *
   * <ul>
   *   <li>label:Code-Review=+2
   *   <li>branch:refs/heads/master
   * </ul>
   *
   * This method will return the leaf predicates that were fulfilled, for example if only the first
   * predicate was fulfilled, the returned list will be equal to ["label:Code-Review=+2"].
   */
  public abstract ImmutableList<String> passingAtoms();

  /**
   * List of leaf predicates that are not fulfilled. See {@link #passingAtoms()} for more details.
   */
  public abstract ImmutableList<String> failingAtoms();

  public static SubmitRequirementExpressionResult create(
      SubmitRequirementExpression expression, PredicateResult predicateResult) {
    return create(
        expression,
        predicateResult.status() ? Status.PASS : Status.FAIL,
        predicateResult.getPassingAtoms(),
        predicateResult.getFailingAtoms());
  }

  public static SubmitRequirementExpressionResult create(
      SubmitRequirementExpression expression,
      Status status,
      ImmutableList<String> passingAtoms,
      ImmutableList<String> failingAtoms) {
    return new AutoValue_SubmitRequirementExpressionResult(
        expression, status, Optional.empty(), passingAtoms, failingAtoms);
  }

  public static SubmitRequirementExpressionResult create(
      SubmitRequirementExpression expression,
      Status status,
      Optional<String> errorMessage,
      ImmutableList<String> passingAtoms,
      ImmutableList<String> failingAtoms) {
    return new AutoValue_SubmitRequirementExpressionResult(
        expression, status, errorMessage, passingAtoms, failingAtoms);
  }

  public static SubmitRequirementExpressionResult error(
      SubmitRequirementExpression expression, String errorMessage) {
    return new AutoValue_SubmitRequirementExpressionResult(
        expression,
        Status.ERROR,
        Optional.of(errorMessage),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static TypeAdapter<SubmitRequirementExpressionResult> typeAdapter() {
    return new GsonTypeAdapter();
  }

  /** Json serializer for {@link SubmitRequirementExpressionResult}. */
  static class GsonTypeAdapter extends TypeAdapter<SubmitRequirementExpressionResult> {
    private static final String KEY_EXPRESSION = "expression";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PASSING_ATOMS = "passingAtoms";
    private static final String KEY_FAILING_ATOMS = "failingAtoms";
    private static final String KEY_ERROR_MESSAGE = "errorMessage";

    @Override
    public void write(JsonWriter out, SubmitRequirementExpressionResult expResult)
        throws IOException {
      out.beginObject();
      out.name(KEY_EXPRESSION)
          .jsonValue(SubmitRequirementExpression.typeAdapter().toJson(expResult.expression()));
      out.name(KEY_STATUS).value(expResult.status().name());
      out.name(KEY_PASSING_ATOMS);
      writeArray(out, expResult.passingAtoms());
      out.name(KEY_FAILING_ATOMS);
      writeArray(out, expResult.failingAtoms());
      if (expResult.errorMessage().isPresent()) {
        out.name(KEY_ERROR_MESSAGE).value(expResult.errorMessage().get());
      }
      out.endObject();
    }

    @Override
    public SubmitRequirementExpressionResult read(JsonReader in) throws IOException {
      JsonObject parsed = unpack(new JsonParser().parse(in)).getAsJsonObject();
      SubmitRequirementExpression expression =
          SubmitRequirementExpression.typeAdapter()
              .fromJsonTree(unpack(parsed.get(KEY_EXPRESSION)));
      Status status = Status.valueOf(parsed.get(KEY_STATUS).getAsString());
      List<String> passingAtoms = new ArrayList<>();
      List<String> failingAtoms = new ArrayList<>();
      for (JsonElement elem : parsed.getAsJsonArray(KEY_PASSING_ATOMS)) {
        passingAtoms.add(elem.getAsString());
      }
      for (JsonElement elem : parsed.getAsJsonArray(KEY_FAILING_ATOMS)) {
        failingAtoms.add(elem.getAsString());
      }
      Optional<String> errorMessage = Optional.empty();
      if (parsed.has(KEY_ERROR_MESSAGE)) {
        errorMessage = Optional.of(unpack(parsed.get(KEY_ERROR_MESSAGE)).getAsString());
      }
      return SubmitRequirementExpressionResult.create(
          expression,
          status,
          errorMessage,
          ImmutableList.copyOf(passingAtoms),
          ImmutableList.copyOf(failingAtoms));
    }

    /**
     * Unpack the {@code in} {@link JsonElement}, i.e. if the element has a single "value" child
     * return it. We've previously used the default Gson serializer for serializing submit
     * requirements entities. This unpacking is needed to preserve backward compatibility while
     * deserializing entities that were previously serialized by the default serializer.
     */
    private static JsonElement unpack(JsonElement in) {
      if (!in.isJsonObject()) {
        return in;
      }
      JsonObject asJsonObject = in.getAsJsonObject();
      return asJsonObject.has("value") && asJsonObject.size() == 1 ? asJsonObject.get("value") : in;
    }

    private static void writeArray(JsonWriter out, ImmutableList<String> source)
        throws IOException {
      out.beginArray();
      for (String value : source) {
        out.value(value);
      }
      out.endArray();
    }
  }

  public enum Status {
    /** Submit requirement expression is fulfilled for a given change. */
    PASS,

    /** Submit requirement expression is failing for a given change. */
    FAIL,

    /** Submit requirement expression contains invalid syntax and is not parsable. */
    ERROR
  }

  /**
   * Entity detailing the result of evaluating a predicate.
   *
   * <p>Example - branch:refs/heads/foo and has:unresolved
   *
   * <p>The above predicate is an "And" predicate having two child predicates:
   *
   * <ul>
   *   <li>branch:refs/heads/foo
   *   <li>has:unresolved
   * </ul>
   *
   * <p>Each child predicate as well as the parent contains the result of its evaluation.
   */
  @AutoValue
  public abstract static class PredicateResult {
    abstract ImmutableList<PredicateResult> childPredicateResults();

    /** We only set this field for leaf predicates. */
    public abstract String predicateString();

    /** true if the predicate is passing for a given change. */
    abstract boolean status();

    /** Returns a list of leaf predicate results whose {@link PredicateResult#status()} is true. */
    ImmutableList<String> getPassingAtoms() {
      return getAtoms(/* status= */ true).stream()
          .map(PredicateResult::predicateString)
          .collect(ImmutableList.toImmutableList());
    }

    /** Returns a list of leaf predicate results whose {@link PredicateResult#status()} is false. */
    ImmutableList<String> getFailingAtoms() {
      return getAtoms(/* status= */ false).stream()
          .map(PredicateResult::predicateString)
          .collect(ImmutableList.toImmutableList());
    }

    /**
     * Returns the list of leaf {@link PredicateResult} whose {@link #status()} is equal to the
     * {@code status} parameter.
     */
    private ImmutableList<PredicateResult> getAtoms(boolean status) {
      ImmutableList.Builder<PredicateResult> atomsList = ImmutableList.builder();
      getAtomsRecursively(atomsList, status);
      return atomsList.build();
    }

    private void getAtomsRecursively(ImmutableList.Builder<PredicateResult> list, boolean status) {
      if (!predicateString().isEmpty() && status() == status) {
        list.add(this);
        return;
      }
      childPredicateResults().forEach(c -> c.getAtomsRecursively(list, status));
    }

    public static Builder builder() {
      return new AutoValue_SubmitRequirementExpressionResult_PredicateResult.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder childPredicateResults(ImmutableList<PredicateResult> value);

      protected abstract ImmutableList.Builder<PredicateResult> childPredicateResultsBuilder();

      public abstract Builder predicateString(String value);

      public abstract Builder status(boolean value);

      public Builder addChildPredicateResult(PredicateResult result) {
        childPredicateResultsBuilder().add(result);
        return this;
      }

      public abstract PredicateResult build();
    }
  }
}
