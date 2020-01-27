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

package com.google.gerrit.acceptance.server.git.receive;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummaryKey;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.junit.Test;

/** Tests for applying limits to e.g. number of files per change. */
public class ReceiveCommitsLimitsIT extends AbstractDaemonTest {

  @Inject
  private @Named("diff_summary") Cache<DiffSummaryKey, DiffSummary> diffSummaryCache;

  @Test
  @GerritConfig(name = "change.maxFiles", value = "1")
  public void limitFileCount() throws Exception {
    PushOneCommit.Result result =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "foo",
                ImmutableMap.of("foo", "foo-1.0", "bar", "bar-1.0"))
            .to("refs/for/master");
    result.assertErrorStatus("Exceeding maximum number of files per change (2 > 1)");
  }

  @Test
  public void cacheKeyMatches() throws Exception {
    int cacheSizeBefore = diffSummaryCache.asMap().size();
    PushOneCommit.Result result =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "foo",
                ImmutableMap.of("foo", "foo-1.0", "bar", "bar-1.0"))
            .to("refs/for/master");
    result.assertOkStatus();

    // Assert that we only performed the diff computation once. This would e.g. catch
    // bugs/deviations in the computation of the cache key.
    assertThat(diffSummaryCache.asMap()).hasSize(cacheSizeBefore + 1);
  }
}
