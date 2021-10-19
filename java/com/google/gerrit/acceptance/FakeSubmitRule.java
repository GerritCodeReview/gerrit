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

package com.google.gerrit.acceptance;

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Singleton;
import java.util.Optional;

/** Fake submit rule that returns OK if the change contains one or more hashtags. */
@Singleton
public class FakeSubmitRule implements SubmitRule {
  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    SubmitRecord record = new SubmitRecord();
    record.status = cd.hashtags().isEmpty() ? Status.NOT_READY : Status.OK;
    record.ruleName = FakeSubmitRule.class.getSimpleName();
    return Optional.of(record);
  }
}
