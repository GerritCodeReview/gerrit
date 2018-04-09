// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChangeFieldTest extends GerritBaseTests {
  @Before
  public void setUp() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void reviewerFieldValues() {
    Table<ReviewerStateInternal, Account.Id, Timestamp> t = HashBasedTable.create();
    Timestamp t1 = TimeUtil.nowTs();
    t.put(ReviewerStateInternal.REVIEWER, new Account.Id(1), t1);
    Timestamp t2 = TimeUtil.nowTs();
    t.put(ReviewerStateInternal.CC, new Account.Id(2), t2);
    ReviewerSet reviewers = ReviewerSet.fromTable(t);

    List<String> values = ChangeField.getReviewerFieldValues(reviewers);
    assertThat(values)
        .containsExactly(
            "REVIEWER,1", "REVIEWER,1," + t1.getTime(), "CC,2", "CC,2," + t2.getTime());

    assertThat(ChangeField.parseReviewerFieldValues(new Change.Id(1), values)).isEqualTo(reviewers);
  }

  @Test
  public void formatSubmitRecordValues() {
    assertThat(
            ChangeField.formatSubmitRecordValues(
                ImmutableList.of(
                    record(
                        SubmitRecord.Status.OK,
                        label(SubmitRecord.Label.Status.MAY, "Label-1", null),
                        label(SubmitRecord.Label.Status.OK, "Label-2", 1))),
                new Account.Id(1)))
        .containsExactly("OK", "MAY,label-1", "OK,label-2", "OK,label-2,0", "OK,label-2,1");
  }

  @Test
  public void storedSubmitRecords() {
    assertStoredRecordRoundTrip(record(SubmitRecord.Status.CLOSED));

    SubmitRecord r =
        record(
            SubmitRecord.Status.OK,
            label(SubmitRecord.Label.Status.MAY, "Label-1", null),
            label(SubmitRecord.Label.Status.OK, "Label-2", 1));

    assertStoredRecordRoundTrip(r);
  }

  @Test
  public void storedSubmitRecordsWithRequirement() {
    SubmitRecord r =
        record(
            SubmitRecord.Status.OK,
            label(SubmitRecord.Label.Status.MAY, "Label-1", null),
            label(SubmitRecord.Label.Status.OK, "Label-2", 1));

    SubmitRequirement sr =
        SubmitRequirement.builder()
            .setType("short_type")
            .setFallbackText("Fallback text may contain special symbols like < > \\ / ; :")
            .addCustomValue("custom_data", "my value")
            .build();
    r.requirements = Collections.singletonList(sr);

    assertStoredRecordRoundTrip(r);
  }

  @Test
  public void storedSubmitRequirementWithoutCustomData() {
    SubmitRecord r =
        record(
            SubmitRecord.Status.OK,
            label(SubmitRecord.Label.Status.MAY, "Label-1", null),
            label(SubmitRecord.Label.Status.OK, "Label-2", 1));

    // Doesn't have any custom data value
    SubmitRequirement sr =
        SubmitRequirement.builder().setFallbackText("short_type").setType("ci_status").build();
    r.requirements = Collections.singletonList(sr);

    assertStoredRecordRoundTrip(r);
  }

  private static SubmitRecord record(SubmitRecord.Status status, SubmitRecord.Label... labels) {
    SubmitRecord r = new SubmitRecord();
    r.status = status;
    if (labels.length > 0) {
      r.labels = ImmutableList.copyOf(labels);
    }
    return r;
  }

  private static SubmitRecord.Label label(
      SubmitRecord.Label.Status status, String label, Integer appliedBy) {
    SubmitRecord.Label l = new SubmitRecord.Label();
    l.status = status;
    l.label = label;
    if (appliedBy != null) {
      l.appliedBy = new Account.Id(appliedBy);
    }
    return l;
  }

  private static void assertStoredRecordRoundTrip(SubmitRecord... records) {
    List<SubmitRecord> recordList = ImmutableList.copyOf(records);
    List<String> stored =
        ChangeField.storedSubmitRecords(recordList)
            .stream()
            .map(s -> new String(s, UTF_8))
            .collect(toList());
    assertThat(ChangeField.parseSubmitRecords(stored))
        .named("JSON %s" + stored)
        .isEqualTo(recordList);
  }
}
