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
          + "\"description\":\"CR description\","
          + "\"applicabilityExpression\":{\"expressionString\":\"branch:refs/heads/master\"},"
          + "\"submittabilityExpression\":{\"expressionString\":\"label:Code-Review=+2\"},"
          + "\"allowOverrideInChildProjects\":true}";

  private static final SubmitRequirementExpressionResult srExpResult =
      SubmitRequirementExpressionResult.create(
          SubmitRequirementExpression.create("label:Code-Review=MAX AND -label:Code-Review=MIN"),
          Status.FAIL,
          Optional.of("Some error message"),
          /* passingAtoms= */ ImmutableList.of("label:Code-Review=MAX"),
          /* failingAtoms= */ ImmutableList.of("label:Code-Review=MIN"));
  private static final String srExpResultSerial =
      "{\"expression\":{\"expressionString\":\"label:Code-Review=MAX AND -label:Code-Review=MIN\"},"
          + "\"status\":\"FAIL\","
          + "\"passingAtoms\":[\"label:Code-Review=MAX\"],"
          + "\"failingAtoms\":[\"label:Code-Review=MIN\"],"
          + "\"errorMessage\":\"Some error message\"}";

  private static final SubmitRequirementResult srReqResult =
      SubmitRequirementResult.builder()
          .submitRequirement(
              SubmitRequirement.builder()
                  .setName("CR")
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
      "{\"submitRequirement\":{\"name\":\"CR\","
          + "\"applicabilityExpression\":{\"expressionString\":\"branch:refs/heads/master\"},"
          + "\"submittabilityExpression\":{\"expressionString\":\"label:\\\"Code-Review=+2\\\"\"},"
          + "\"overrideExpression\":{\"expressionString\":\"label:Override=+1\"},"
          + "\"allowOverrideInChildProjects\":false},"
          + "\"applicabilityExpressionResult\":"
          + "{\"expression\":{\"expressionString\":\"branch:refs/heads/master\"},"
          + "\"status\":\"PASS\",\"passingAtoms\":[\"refs/heads/master\"],\"failingAtoms\":[]},"
          + "\"submittabilityExpressionResult\":"
          + "{\"expression\":{\"expressionString\":\"label:\\\"Code-Review=+2\\\"\"},"
          + "\"status\":\"PASS\",\"passingAtoms\":[\"label:\\\"Code-Review=+2\\\"\"],"
          + "\"failingAtoms\":[]},"
          + "\"overrideExpressionResult\":"
          + "{\"expression\":{\"expressionString\":\"label:Override=+1\"},"
          + "\"status\":\"PASS\",\"passingAtoms\":[],\"failingAtoms\":[\"label:Override=+1\"]},"
          + "\"patchSetCommitId\":\"4663ab9e9eb49a214e68e60f0fe5d0b6f44f763e\","
          + "\"legacy\":true}";

  @Test
  public void submitRequirementExpression_serialize() {
    assertThat(SubmitRequirementExpression.typeAdapter().toJson(srReqExp))
        .isEqualTo(srReqExpSerial);
  }

  @Test
  public void submitRequirementExpression_deserialize() throws Exception {
    assertThat(SubmitRequirementExpression.typeAdapter().fromJson(srReqExpSerial))
        .isEqualTo(srReqExp);
  }

  @Test
  public void submitRequirementExpression_roundTrip() throws Exception {
    SubmitRequirementExpression exp = SubmitRequirementExpression.create("label:Code-Review=+2");
    TypeAdapter<SubmitRequirementExpression> adapter = SubmitRequirementExpression.typeAdapter();
    assertThat(adapter.fromJson(adapter.toJson(exp))).isEqualTo(exp);
  }

  @Test
  public void submitRequirement_serialize() throws Exception {
    assertThat(SubmitRequirement.typeAdapter().toJson(sr)).isEqualTo(srSerial);
  }

  @Test
  public void submitRequirement_deserialize() throws Exception {
    assertThat(SubmitRequirement.typeAdapter().fromJson(srSerial)).isEqualTo(sr);
  }

  @Test
  public void submitRequirement_roundTrip() throws Exception {
    TypeAdapter<SubmitRequirement> adapter = SubmitRequirement.typeAdapter();
    assertThat(adapter.fromJson(adapter.toJson(sr))).isEqualTo(sr);
  }

  @Test
  public void submitRequirementExpressionResult_serialize() throws Exception {
    assertThat(SubmitRequirementExpressionResult.typeAdapter().toJson(srExpResult))
        .isEqualTo(srExpResultSerial);
  }

  @Test
  public void submitRequirementExpressionResult_deserialize() throws Exception {
    assertThat(SubmitRequirementExpressionResult.typeAdapter().fromJson(srExpResultSerial))
        .isEqualTo(srExpResult);
  }

  @Test
  public void submitRequirementExpressionResult_roundtrip() throws Exception {
    TypeAdapter<SubmitRequirementExpressionResult> adapter =
        SubmitRequirementExpressionResult.typeAdapter();
    assertThat(adapter.fromJson(adapter.toJson(srExpResult))).isEqualTo(srExpResult);
  }

  @Test
  public void serializeSubmitRequirementResult_serialize() throws Exception {
    assertThat(SubmitRequirementResult.typeAdapter().toJson(srReqResult))
        .isEqualTo(srReqResultSerial);
  }

  @Test
  public void serializeSubmitRequirementResult_deserialize() throws Exception {
    assertThat(SubmitRequirementResult.typeAdapter().fromJson(srReqResultSerial))
        .isEqualTo(srReqResult);
  }

  @Test
  public void deserializeSubmitRequirementResult_legacyPatchsetIdFormat() throws Exception {
    String srResultSerialLegacy =
        srReqResultSerial.replace(
            "\"4663ab9e9eb49a214e68e60f0fe5d0b6f44f763e\"",
            "{\"w1\":1180937118,\"w2\":-1632331231,\"w3\":1315497487,"
                + "\"w4\":266719414,\"w5\":-196118978}");
    assertThat(SubmitRequirementResult.typeAdapter().fromJson(srResultSerialLegacy))
        .isEqualTo(srReqResult);
  }
}
