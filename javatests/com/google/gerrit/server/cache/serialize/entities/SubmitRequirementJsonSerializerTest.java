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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.notedb.ChangeNoteJson;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class SubmitRequirementJsonSerializerTest {
  private static final SubmitRequirementExpression srReqExp =
      SubmitRequirementExpression.create("label:Code-Review=+2");

  private static final String srReqExpSerial = "{\"expressionString\":\"label:Code-Review=+2\"}";

  private static final SubmitRequirement sr =
      SubmitRequirement.builder()
          .setName("CR")
          .setDescription(Optional.of("CR description"))
          .setApplicabilityExpression(SubmitRequirementExpression.of("branch:refs/heads/master"))
          .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+2"))
          .setAllowOverrideInChildProjects(true)
          .build();

  private static final String srSerial =
      "{\"name\":\"CR\","
          + "\"description\":{\"value\":\"CR description\"},"
          + "\"applicabilityExpression\":{\"value\":"
          + "{\"expressionString\":\"branch:refs/heads/master\"}},"
          + "\"submittabilityExpression\":{"
          + "\"expressionString\":\"label:Code-Review=+2\"},"
          + "\"overrideExpression\":{\"value\":null},"
          + "\"allowOverrideInChildProjects\":true}";

  private static final SubmitRequirementExpressionResult srExpResult =
      SubmitRequirementExpressionResult.create(
          SubmitRequirementExpression.create("label:Code-Review=MAX AND -label:Code-Review=MIN"),
          Status.FAIL,
          /* passingAtoms= */ ImmutableList.of("label:Code-Review=MAX"),
          /* failingAtoms= */ ImmutableList.of("label:Code-Review=MIN"));

  private static final String srExpResultSerial =
      "{\"expression\":{\"expressionString\":"
          + "\"label:Code-Review=MAX AND -label:Code-Review=MIN\"},"
          + "\"status\":\"FAIL\","
          + "\"errorMessage\":{\"value\":null},"
          + "\"passingAtoms\":[\"label:Code-Review=MAX\"],"
          + "\"failingAtoms\":[\"label:Code-Review=MIN\"]}";

  private static final SubmitRequirementResult srReqResult =
      SubmitRequirementResult.builder()
          .submitRequirement(
              SubmitRequirement.builder()
                  .setName("CR")
                  .setDescription(Optional.of("CR Description"))
                  .setApplicabilityExpression(
                      SubmitRequirementExpression.of("branch:refs/heads/master"))
                  .setSubmittabilityExpression(
                      SubmitRequirementExpression.create("label:\"Code-Review=+2\""))
                  .setOverrideExpression(SubmitRequirementExpression.of("label:Override=+1"))
                  .setAllowOverrideInChildProjects(false)
                  .build())
          .patchSetCommitId(ObjectId.fromString("4663ab9e9eb49a214e68e60f0fe5d0b6f44f763e"))
          .applicabilityExpressionResult(
              Optional.of(
                  SubmitRequirementExpressionResult.create(
                      SubmitRequirementExpression.create("branch:refs/heads/master"),
                      Status.PASS,
                      ImmutableList.of("refs/heads/master"),
                      ImmutableList.of())))
          .submittabilityExpressionResult(
              SubmitRequirementExpressionResult.create(
                  SubmitRequirementExpression.create("label:\"Code-Review=+2\""),
                  Status.PASS,
                  /* passingAtoms= */ ImmutableList.of("label:\"Code-Review=+2\""),
                  /* failingAtoms= */ ImmutableList.of()))
          .overrideExpressionResult(
              Optional.of(
                  SubmitRequirementExpressionResult.create(
                      SubmitRequirementExpression.create("label:Override=+1"),
                      Status.PASS,
                      /* passingAtoms= */ ImmutableList.of(),
                      /* failingAtoms= */ ImmutableList.of("label:Override=+1"))))
          .legacy(Optional.of(true))
          .build();

  private static final String srReqResultSerial =
      "{\"submitRequirement\":{\"name\":\"CR\",\"description\":{\"value\":\"CR Description\"},"
          + "\"applicabilityExpression\":{\"value\":{"
          + "\"expressionString\":\"branch:refs/heads/master\"}},"
          + "\"submittabilityExpression\":{\"expressionString\":\"label:\\\"Code-Review=+2\\\"\"},"
          + "\"overrideExpression\":{\"value\":{\"expressionString\":\"label:Override=+1\"}},"
          + "\"allowOverrideInChildProjects\":false},"
          + "\"applicabilityExpressionResult\":{\"value\":{"
          + "\"expression\":{\"expressionString\":\"branch:refs/heads/master\"},"
          + "\"status\":\"PASS\",\"errorMessage\":{\"value\":null},"
          + "\"passingAtoms\":[\"refs/heads/master\"],"
          + "\"failingAtoms\":[]}},"
          + "\"submittabilityExpressionResult\":{\"value\":{"
          + "\"expression\":{\"expressionString\":\"label:\\\"Code-Review=+2\\\"\"},"
          + "\"status\":\"PASS\",\"errorMessage\":{\"value\":null},"
          + "\"passingAtoms\":[\"label:\\\"Code-Review=+2\\\"\"],"
          + "\"failingAtoms\":[]}},"
          + "\"overrideExpressionResult\":{\"value\":{"
          + "\"expression\":{\"expressionString\":\"label:Override=+1\"},"
          + "\"status\":\"PASS\",\"errorMessage\":{\"value\":null},"
          + "\"passingAtoms\":[],"
          + "\"failingAtoms\":[\"label:Override=+1\"]}},"
          + "\"patchSetCommitId\":\"4663ab9e9eb49a214e68e60f0fe5d0b6f44f763e\","
          + "\"legacy\":{\"value\":true},"
          + "\"forced\":{\"value\":null},"
          + "\"hidden\":{\"value\":null}}";

  private static final Gson gson = new ChangeNoteJson().getGson();

  @Test
  public void submitRequirementExpression_serialize() {
    assertThat(SubmitRequirementExpression.typeAdapter(gson).toJson(srReqExp))
        .isEqualTo(srReqExpSerial);
  }

  @Test
  public void submitRequirementExpression_deserialize() throws Exception {
    assertThat(SubmitRequirementExpression.typeAdapter(gson).fromJson(srReqExpSerial))
        .isEqualTo(srReqExp);
  }

  @Test
  public void submitRequirementExpression_roundTrip() throws Exception {
    SubmitRequirementExpression exp = SubmitRequirementExpression.create("label:Code-Review=+2");
    TypeAdapter<SubmitRequirementExpression> adapter =
        SubmitRequirementExpression.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(exp))).isEqualTo(exp);
  }

  @Test
  public void submitRequirement_serialize() throws Exception {
    assertThat(SubmitRequirement.typeAdapter(gson).toJson(sr)).isEqualTo(srSerial);
  }

  @Test
  public void submitRequirement_deserialize() throws Exception {
    assertThat(SubmitRequirement.typeAdapter(gson).fromJson(srSerial)).isEqualTo(sr);
  }

  @Test
  public void submitRequirement_roundTrip() throws Exception {
    TypeAdapter<SubmitRequirement> adapter = SubmitRequirement.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(sr))).isEqualTo(sr);
  }

  @Test
  public void submitRequirementExpressionResult_serialize() throws Exception {
    assertThat(SubmitRequirementExpressionResult.typeAdapter(gson).toJson(srExpResult))
        .isEqualTo(srExpResultSerial);
  }

  @Test
  public void submitRequirementExpressionResult_deserialize() throws Exception {
    assertThat(SubmitRequirementExpressionResult.typeAdapter(gson).fromJson(srExpResultSerial))
        .isEqualTo(srExpResult);
  }

  @Test
  public void submitRequirementExpressionResult_roundtrip() throws Exception {
    TypeAdapter<SubmitRequirementExpressionResult> adapter =
        SubmitRequirementExpressionResult.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(srExpResult))).isEqualTo(srExpResult);
  }

  @Test
  public void submitRequirementResult_serialize() throws Exception {
    assertThat(SubmitRequirementResult.typeAdapter(gson).toJson(srReqResult))
        .isEqualTo(srReqResultSerial);
  }

  @Test
  public void submitRequirementResult_deserialize_optionalSubmittabilityExpressionResultField()
      throws Exception {
    assertThat(SubmitRequirementResult.typeAdapter(gson).fromJson(srReqResultSerial))
        .isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_deserialize_nonOptionalSubmittabilityExpressionResultField()
      throws Exception {
    String oldFormatSrReqResultSerial =
        srReqResultSerial.replace(
            "\"submittabilityExpressionResult\":{\"value\":{"
                + "\"expression\":{\"expressionString\":\"label:\\\"Code-Review=+2\\\"\"},"
                + "\"status\":\"PASS\",\"errorMessage\":{\"value\":null},"
                + "\"passingAtoms\":[\"label:\\\"Code-Review=+2\\\"\"],"
                + "\"failingAtoms\":[]}},",
            "\"submittabilityExpressionResult\":{"
                + "\"expression\":{\"expressionString\":\"label:\\\"Code-Review=+2\\\"\"},"
                + "\"status\":\"PASS\",\"errorMessage\":{\"value\":null},"
                + "\"passingAtoms\":[\"label:\\\"Code-Review=+2\\\"\"],"
                + "\"failingAtoms\":[]},");
    assertThat(SubmitRequirementResult.typeAdapter(gson).fromJson(oldFormatSrReqResultSerial))
        .isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_roundTrip() throws Exception {
    TypeAdapter<SubmitRequirementResult> adapter = SubmitRequirementResult.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(srReqResult))).isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_withHidden_roundTrip() throws Exception {
    SubmitRequirementResult srResultWithHidden =
        srReqResult.toBuilder().hidden(Optional.of(true)).build();
    TypeAdapter<SubmitRequirementResult> adapter = SubmitRequirementResult.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(srResultWithHidden))).isEqualTo(srResultWithHidden);
  }

  @Test
  public void submitRequirementResult_deserializeNoHidden() throws Exception {
    String srResultSerialMandatoryLegacyFieldFormat =
        srReqResultSerial.replace(",hidden\":{\"value\":null}", "");
    assertThat(
            SubmitRequirementResult.typeAdapter(gson)
                .fromJson(srResultSerialMandatoryLegacyFieldFormat))
        .isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_deserializeNonExistentField() throws Exception {
    // Tests that unrecognized fields are skipped on deserialization (e.g. when the new fields are
    // introduced, the old binary can parse the new-format Json)
    String srResultSerialMandatoryLegacyFieldFormat =
        srReqResultSerial.replace(
            "\"hidden\":{\"value\":null}}", "\"non-existent\":{\"value\":null}}");
    assertThat(
            SubmitRequirementResult.typeAdapter(gson)
                .fromJson(srResultSerialMandatoryLegacyFieldFormat))
        .isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_emptySubmittabilityExpressionResultField_roundTrip()
      throws Exception {
    SubmitRequirementResult srResult =
        srReqResult
            .toBuilder()
            .submittabilityExpressionResult(Optional.empty())
            .applicabilityExpressionResult(Optional.empty())
            .overrideExpressionResult(Optional.empty())
            .build();
    TypeAdapter<SubmitRequirementResult> adapter = SubmitRequirementResult.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(srResult))).isEqualTo(srResult);
  }

  @Test
  public void deserializeSubmitRequirementResult_withJGitPatchsetIdFormat() throws Exception {
    String srResultSerialJgitFormat =
        srReqResultSerial.replace(
            "\"4663ab9e9eb49a214e68e60f0fe5d0b6f44f763e\"",
            "{\"w1\":1180937118,\"w2\":-1632331231,\"w3\":1315497487,"
                + "\"w4\":266719414,\"w5\":-196118978}");
    assertThat(SubmitRequirementResult.typeAdapter(gson).fromJson(srResultSerialJgitFormat))
        .isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_deserializeNonOptionalLegacyField() throws Exception {
    String srResultSerialMandatoryLegacyFieldFormat =
        srReqResultSerial.replace("\"legacy\":{\"value\":true}", "\"legacy\":true");
    assertThat(
            SubmitRequirementResult.typeAdapter(gson)
                .fromJson(srResultSerialMandatoryLegacyFieldFormat))
        .isEqualTo(srReqResult);
  }

  @Test
  public void submitRequirementResult_emptyLegacyField_roundTrip() throws Exception {
    SubmitRequirementResult srResult = srReqResult.toBuilder().legacy(Optional.empty()).build();
    TypeAdapter<SubmitRequirementResult> adapter = SubmitRequirementResult.typeAdapter(gson);
    assertThat(adapter.fromJson(adapter.toJson(srResult))).isEqualTo(srResult);
  }
}
