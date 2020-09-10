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

package com.google.gerrit.server.rules;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import java.util.Optional;

/** Submit rule that the change doesn't have a hashtag that blocks submission. */
@Singleton
public class BlockSubmissionHashtagRule implements SubmitRule {

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class)
          .annotatedWith(Exports.named("BlockSubmissionHashtagRule"))
          .to(BlockSubmissionHashtagRule.class);
    }
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    if (cd.hashtags().contains("BLOCK_SUBMISSION")) {
      return notReady();
    }
    return Optional.empty();
  }

  private Optional<SubmitRecord> notReady() {
    SubmitRecord submitRecordNotReady = new SubmitRecord();
    submitRecordNotReady.status = SubmitRecord.Status.NOT_READY;
    submitRecordNotReady.requirements =
        ImmutableList.of(
            SubmitRequirement.builder()
                .setFallbackText("Change must not have BLOCK_SUBMISSION hashtag")
                .setType("block_submission_hashtag")
                .build());
    return Optional.of(submitRecordNotReady);
  }
}
