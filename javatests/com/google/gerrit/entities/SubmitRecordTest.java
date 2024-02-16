// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SubmitRecordTest {
  private static final SubmitRecord OK_RECORD;
  private static final SubmitRecord FORCED_RECORD;
  private static final SubmitRecord NOT_READY_RECORD;

  static {
    OK_RECORD = new SubmitRecord();
    OK_RECORD.status = SubmitRecord.Status.OK;

    FORCED_RECORD = new SubmitRecord();
    FORCED_RECORD.status = SubmitRecord.Status.FORCED;

    NOT_READY_RECORD = new SubmitRecord();
    NOT_READY_RECORD.status = SubmitRecord.Status.NOT_READY;
  }

  @Test
  public void okIfAllOkay() {
    List<SubmitRecord> submitRecords = new ArrayList<>();
    submitRecords.add(OK_RECORD);

    assertThat(SubmitRecord.allRecordsOK(submitRecords)).isTrue();
  }

  @Test
  public void okWhenEmpty() {
    List<SubmitRecord> submitRecords = new ArrayList<>();

    assertThat(SubmitRecord.allRecordsOK(submitRecords)).isTrue();
  }

  @Test
  public void okWhenForced() {
    List<SubmitRecord> submitRecords = new ArrayList<>();
    submitRecords.add(FORCED_RECORD);

    assertThat(SubmitRecord.allRecordsOK(submitRecords)).isTrue();
  }

  @Test
  public void emptyResultIfInvalid() {
    List<SubmitRecord> submitRecords = new ArrayList<>();
    submitRecords.add(NOT_READY_RECORD);
    submitRecords.add(OK_RECORD);

    assertThat(SubmitRecord.allRecordsOK(submitRecords)).isFalse();
  }

  @Test
  public void deepCopy() {
    SubmitRecord record = new SubmitRecord();
    record.status = SubmitRecord.Status.CLOSED;
    record.errorMessage = "ouch";
    record.requirements =
        ImmutableList.of(
            LegacySubmitRequirement.builder().setFallbackText("foo").setType("baz").build());
    SubmitRecord.Label label = new SubmitRecord.Label();
    label.label = "Code-Review";
    record.labels = ImmutableList.of(label);

    assertThat(record).isNotSameInstanceAs(record.deepCopy());
    assertThat(record).isEqualTo(record.deepCopy());
  }
}
