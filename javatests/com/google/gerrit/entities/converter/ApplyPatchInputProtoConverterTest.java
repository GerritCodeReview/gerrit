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
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import java.lang.reflect.Type;
import java.util.Objects;
import org.junit.Test;

public class ApplyPatchInputProtoConverterTest {
  private final ApplyPatchInputProtoConverter applyPatchInputProtoConverter =
      ApplyPatchInputProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    ApplyPatchInput applyPatchInput = new ApplyPatchInput();
    applyPatchInput.patch = "test-patch";
    applyPatchInput.allowConflicts = true;
    Entities.ApplyPatchInput proto = applyPatchInputProtoConverter.toProto(applyPatchInput);

    Entities.ApplyPatchInput expectedProto =
        Entities.ApplyPatchInput.newBuilder()
            .setPatch("test-patch")
            .setAllowConflicts(true)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    ApplyPatchInput applyPatchInput = new ApplyPatchInput();
    applyPatchInput.patch = "test-patch";
    applyPatchInput.allowConflicts = true;

    ApplyPatchInput convertedApplyPatchInput =
        applyPatchInputProtoConverter.fromProto(
            applyPatchInputProtoConverter.toProto(applyPatchInput));

    assertThat(Objects.equals(applyPatchInput.patch, convertedApplyPatchInput.patch)).isTrue();
    assertThat(applyPatchInput.allowConflicts).isEqualTo(convertedApplyPatchInput.allowConflicts);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(ApplyPatchInput.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("patch", String.class)
                .put("allowConflicts", Boolean.class)
                .build());
  }
}
