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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** Result of evaluating a {@link SubmitRequirement} on a given Change. */
@AutoValue
public abstract class SubmitRequirementResult {
  /** Submit requirement for which this result is evaluated. */
  public abstract SubmitRequirement submitRequirement();

  /** Result of evaluating a {@link SubmitRequirement#applicabilityExpression()} on a change. */
  public abstract Optional<SubmitRequirementExpressionResult> applicabilityExpressionResult();

  /**
   * Result of evaluating a {@link SubmitRequirement#submittabilityExpression()} ()} on a change.
   */
  public abstract SubmitRequirementExpressionResult submittabilityExpressionResult();

  /** Result of evaluating a {@link SubmitRequirement#overrideExpression()} ()} on a change. */
  public abstract Optional<SubmitRequirementExpressionResult> overrideExpressionResult();

  /** SHA-1 of the patchset commit ID for which the submit requirement was evaluated. */
  public abstract ObjectId patchSetCommitId();

  /**
   * Whether this result was created from a legacy {@link SubmitRecord}, or by evaluating a {@link
   * SubmitRequirement}.
   *
   * <p>If equals {@link Optional#empty()}, we treat the result as non-legacy (false).
   */
  public abstract Optional<Boolean> legacy();

  public boolean isLegacy() {
    return legacy().orElse(false);
  }

  @Memoized
  public Status status() {
    if (assertError(submittabilityExpressionResult())
        || assertError(applicabilityExpressionResult())
        || assertError(overrideExpressionResult())) {
      return Status.ERROR;
    } else if (assertFail(applicabilityExpressionResult())) {
      return Status.NOT_APPLICABLE;
    } else if (assertPass(overrideExpressionResult())) {
      return Status.OVERRIDDEN;
    } else if (assertPass(submittabilityExpressionResult())) {
      return Status.SATISFIED;
    } else {
      return Status.UNSATISFIED;
    }
  }

  /** Returns true if the submit requirement is fulfilled and can allow change submission. */
  @Memoized
  public boolean fulfilled() {
    Status s = status();
    return s == Status.SATISFIED || s == Status.OVERRIDDEN || s == Status.NOT_APPLICABLE;
  }

  public static Builder builder() {
    return new AutoValue_SubmitRequirementResult.Builder();
  }

  @VisibleForTesting
  public static TypeAdapter<SubmitRequirementResult> defaultTypeAdapter(Gson gson) {
    return new AutoValue_SubmitRequirementResult.GsonTypeAdapter(gson);
  }

  public static TypeAdapter<SubmitRequirementResult> typeAdapter() {
    return new GsonTypeAdapter();
  }

  /** Json serializer for {@link SubmitRequirementResult}. */
  static class GsonTypeAdapter extends TypeAdapter<SubmitRequirementResult> {
    private static final String KEY_SUBMIT_REQUIREMENT = "submitRequirement";
    private static final String KEY_APPLICABILITY_EXPRESSION_RESULT =
        "applicabilityExpressionResult";
    private static final String KEY_OVERRIDE_EXPRESSION_RESULT = "overrideExpressionResult";
    private static final String KEY_SUBMITTABILITY_EXPRESSION_RESULT =
        "submittabilityExpressionResult";
    private static final String KEY_PATCHSET_COMMIT_ID = "patchSetCommitId";
    private static final String KEY_LEGACY = "legacy";

    @Override
    public void write(JsonWriter out, SubmitRequirementResult srResult) throws IOException {
      out.beginObject();
      out.name(KEY_SUBMIT_REQUIREMENT)
          .jsonValue(SubmitRequirement.typeAdapter().toJson(srResult.submitRequirement()));
      if (srResult.applicabilityExpressionResult().isPresent()) {
        serializeExpressionResult(
            out,
            KEY_APPLICABILITY_EXPRESSION_RESULT,
            srResult.applicabilityExpressionResult().get());
      }
      serializeExpressionResult(
          out, KEY_SUBMITTABILITY_EXPRESSION_RESULT, srResult.submittabilityExpressionResult());
      if (srResult.overrideExpressionResult().isPresent()) {
        serializeExpressionResult(
            out, KEY_OVERRIDE_EXPRESSION_RESULT, srResult.overrideExpressionResult().get());
      }
      out.name(KEY_PATCHSET_COMMIT_ID).value(srResult.patchSetCommitId().name());
      if (srResult.legacy().isPresent()) {
        out.name(KEY_LEGACY).value(srResult.legacy().get());
      }
      out.endObject();
    }

    @Override
    public SubmitRequirementResult read(JsonReader in) throws IOException {
      JsonObject parsed = new JsonParser().parse(in).getAsJsonObject();
      SubmitRequirementResult.Builder builder = SubmitRequirementResult.builder();
      TypeAdapter<SubmitRequirement> srAdapter = SubmitRequirement.typeAdapter();
      builder.submitRequirement(srAdapter.fromJsonTree(unpack(parsed.get(KEY_SUBMIT_REQUIREMENT))));
      if (parsed.has(KEY_APPLICABILITY_EXPRESSION_RESULT)) {
        builder.applicabilityExpressionResult(
            Optional.of(deserializeExpressionResult(parsed, KEY_APPLICABILITY_EXPRESSION_RESULT)));
      }
      builder.submittabilityExpressionResult(
          deserializeExpressionResult(parsed, KEY_SUBMITTABILITY_EXPRESSION_RESULT));
      if (parsed.has(KEY_OVERRIDE_EXPRESSION_RESULT)) {
        builder.overrideExpressionResult(
            Optional.of(deserializeExpressionResult(parsed, KEY_OVERRIDE_EXPRESSION_RESULT)));
      }
      JsonElement psCommitIdElement = parsed.get(KEY_PATCHSET_COMMIT_ID);
      builder.patchSetCommitId(deserializePatchsetCommitId(psCommitIdElement));
      if (parsed.has(KEY_LEGACY)) {
        JsonElement legacyJsonObject = unpack(parsed.get(KEY_LEGACY));
        builder.legacy(Optional.of(deserializeLegacy(legacyJsonObject)));
      }
      return builder.build();
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

    private static void serializeExpressionResult(
        JsonWriter out, String key, SubmitRequirementExpressionResult expResult)
        throws IOException {
      String overrideSerial = SubmitRequirementExpressionResult.typeAdapter().toJson(expResult);
      out.name(key).jsonValue(overrideSerial);
    }

    private static SubmitRequirementExpressionResult deserializeExpressionResult(
        JsonObject obj, String key) {
      return SubmitRequirementExpressionResult.typeAdapter().fromJsonTree(unpack(obj.get(key)));
    }

    private static boolean deserializeLegacy(JsonElement obj) {
      if (obj.isJsonObject()) {
        obj = unpack(obj.getAsJsonObject());
      }
      return obj.getAsBoolean();
    }

    private static ObjectId deserializePatchsetCommitId(JsonElement element) {
      return unpack(element).isJsonObject()
          ? deserializePatchsetCommitIdLegacyFormat(element)
          : ObjectId.fromString(element.getAsString());
    }

    /**
     * Some existing persisted entities in NoteDb are serialized using this format. Ensure proper
     * deserialization for these.
     */
    private static ObjectId deserializePatchsetCommitIdLegacyFormat(JsonElement in) {
      JsonObject asJsonObject = in.getAsJsonObject();
      int w1 = asJsonObject.get("w1").getAsInt();
      int w2 = asJsonObject.get("w2").getAsInt();
      int w3 = asJsonObject.get("w3").getAsInt();
      int w4 = asJsonObject.get("w4").getAsInt();
      int w5 = asJsonObject.get("w5").getAsInt();
      int[] raw = {w1, w2, w3, w4, w5};
      return ObjectId.fromRaw(raw);
    }
  }

  public enum Status {
    /** Submit requirement is fulfilled. */
    SATISFIED,

    /**
     * Submit requirement is not satisfied. Happens when {@link
     * SubmitRequirement#submittabilityExpression()} evaluates to false.
     */
    UNSATISFIED,

    /**
     * Submit requirement is overridden. Happens when {@link SubmitRequirement#overrideExpression()}
     * evaluates to true.
     */
    OVERRIDDEN,

    /**
     * Submit requirement is not applicable for a given change. Happens when {@link
     * SubmitRequirement#applicabilityExpression()} evaluates to false.
     */
    NOT_APPLICABLE,

    /**
     * Any of the applicability, blocking or override expressions contain invalid syntax and are not
     * parsable.
     */
    ERROR
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder submitRequirement(SubmitRequirement submitRequirement);

    public abstract Builder applicabilityExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder submittabilityExpressionResult(SubmitRequirementExpressionResult value);

    public abstract Builder overrideExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder patchSetCommitId(ObjectId value);

    public abstract Builder legacy(Optional<Boolean> value);

    public abstract SubmitRequirementResult build();
  }

  private boolean assertPass(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.PASS);
  }

  private boolean assertPass(SubmitRequirementExpressionResult expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.PASS);
  }

  private boolean assertFail(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.FAIL);
  }

  private boolean assertError(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.ERROR);
  }

  private boolean assertError(SubmitRequirementExpressionResult expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.ERROR);
  }

  private boolean assertStatus(
      SubmitRequirementExpressionResult expressionResult,
      SubmitRequirementExpressionResult.Status status) {
    return expressionResult.status() == status;
  }

  private boolean assertStatus(
      Optional<SubmitRequirementExpressionResult> expressionResult,
      SubmitRequirementExpressionResult.Status status) {
    return expressionResult.isPresent() && assertStatus(expressionResult.get(), status);
  }
}
