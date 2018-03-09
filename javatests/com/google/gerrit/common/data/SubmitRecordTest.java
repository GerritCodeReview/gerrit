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

package com.google.gerrit.common.data;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;

public class SubmitRecordTest {

  private static SubmitRecord OK_RECORD;
  private static SubmitRecord NOT_READY_RECORD;

  static {
    OK_RECORD = new SubmitRecord();
    OK_RECORD.status = SubmitRecord.Status.OK;

    NOT_READY_RECORD = new SubmitRecord();
    NOT_READY_RECORD.status = SubmitRecord.Status.NOT_READY;
  }

  @Test
  public void findOkRecord() {
    Collection<SubmitRecord> submitRecords = new ArrayList<>();
    submitRecords.add(NOT_READY_RECORD);
    submitRecords.add(OK_RECORD);

    assertThat(SubmitRecord.findOkRecord(submitRecords).isPresent()).isTrue();
  }

  @Test
  public void emptyResultIfInvalid() {
    Collection<SubmitRecord> submitRecords = new ArrayList<>();
    submitRecords.add(NOT_READY_RECORD);

    assertThat(SubmitRecord.findOkRecord(submitRecords).isPresent()).isFalse();
  }
}
