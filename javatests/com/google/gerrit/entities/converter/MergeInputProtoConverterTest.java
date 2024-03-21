/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.entities.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import java.lang.reflect.Type;
import java.util.Objects;
import org.junit.Test;

public class MergeInputProtoConverterTest {
  private final MergeInputProtoConverter mergeInputProtoConverter =
      MergeInputProtoConverter.INSTANCE;

  // Helper method that creates a MergeInput with all possible value.
  private MergeInput createMergeInput() {
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "test-source";
    mergeInput.sourceBranch = "test-source-branch";
    mergeInput.strategy = "test-strategy";
    mergeInput.allowConflicts = true;
    return mergeInput;
  }

  private void assertMergeInputEquals(MergeInput expected, MergeInput actual) {
    assertThat(
            Objects.equals(expected.source, actual.source)
                && Objects.equals(expected.sourceBranch, actual.sourceBranch)
                && Objects.equals(expected.strategy, actual.strategy)
                && expected.allowConflicts == actual.allowConflicts)
        .isTrue();
  }

  @Test
  public void allValuesConvertedToProto() {

    Entities.MergeInput proto = mergeInputProtoConverter.toProto(createMergeInput());

    Entities.MergeInput expectedProto =
        Entities.MergeInput.newBuilder()
            .setSource("test-source")
            .setSourceBranch("test-source-branch")
            .setStrategy("test-strategy")
            .setAllowConflicts(true)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    MergeInput mergeInput = createMergeInput();

    MergeInput convertedMergeInput =
        mergeInputProtoConverter.fromProto(mergeInputProtoConverter.toProto(mergeInput));

    assertMergeInputEquals(mergeInput, convertedMergeInput);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(MergeInput.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("source", String.class)
                .put("sourceBranch", String.class)
                .put("strategy", String.class)
                .put("allowConflicts", boolean.class)
                .build());
  }
}
