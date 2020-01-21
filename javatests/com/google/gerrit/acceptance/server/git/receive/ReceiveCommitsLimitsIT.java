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
