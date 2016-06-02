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

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.testutil.GerritBaseTests;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ChangeFieldTest extends GerritBaseTests {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

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
    Table<ReviewerStateInternal, Account.Id, Timestamp> t =
        HashBasedTable.create();
    Timestamp t1 = TimeUtil.nowTs();
    t.put(ReviewerStateInternal.REVIEWER, new Account.Id(1), t1);
    Timestamp t2 = TimeUtil.nowTs();
    t.put(ReviewerStateInternal.CC, new Account.Id(2), t2);
    ReviewerSet reviewers = ReviewerSet.fromTable(t);

    List<String> values = ChangeField.getReviewerFieldValues(reviewers);
    assertThat(values).containsExactly(
        "REVIEWER,1",
        "REVIEWER,1," + t1.getTime(),
        "CC,2",
        "CC,2," + t2.getTime());

    assertThat(ChangeField.parseReviewerFieldValues(values))
        .isEqualTo(reviewers);
  }
}
