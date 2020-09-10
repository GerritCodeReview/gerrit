// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.rules;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.server.rules.BlockSubmissionHashtagRule;
import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;

@NoHttpd
public class BlockSubmissionHashtagRuleIT extends AbstractDaemonTest {
  @Inject private BlockSubmissionHashtagRule rule;

  @Test
  public void blocksWhenBlockSubmissionHashtagIsPresent() throws Exception {
    PushOneCommit.Result r = createChange();
    HashtagsInput hashtagsInput = new HashtagsInput();
    hashtagsInput.add = ImmutableSet.of("BLOCK_SUBMISSION");
    gApi.changes().id(r.getChangeId()).setHashtags(hashtagsInput);

    Optional<SubmitRecord> submitRecord = rule.evaluate(r.getChange());

    assertThat(submitRecord).isPresent();
    SubmitRecord result = submitRecord.get();
    assertThat(result.status).isEqualTo(SubmitRecord.Status.NOT_READY);
    assertThat(result.requirements)
        .containsExactly(
            SubmitRequirement.builder()
                .setFallbackText("Change must not have BLOCK_SUBMISSION hashtag")
                .setType("block_submission_hashtag")
                .build());
  }

  @Test
  public void doesNothingByDefault() throws Exception {
    PushOneCommit.Result r = createChange();

    Optional<SubmitRecord> submitRecord = rule.evaluate(r.getChange());
    assertThat(submitRecord).isEmpty();
  }
}
