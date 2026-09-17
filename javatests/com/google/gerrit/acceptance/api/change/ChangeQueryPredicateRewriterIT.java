// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangePredicates;
import com.google.gerrit.server.query.change.ChangeQueryPredicateRewriter;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ChangeQueryPredicateRewriterIT extends AbstractDaemonTest {
  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        bind(ChangeQueryPredicateRewriter.class)
            .annotatedWith(Exports.named("TestPredicateRewriter"))
            .to(FakePredicateRewriter.class);
      }
    };
  }

  @Inject private FakePredicateRewriter rewriter;

  @Before
  public void resetRewriter() {
    // The daemon (and its singletons) may be reused across test methods in this class, so start
    // each test from a known-clean state.
    rewriter.reset();
  }

  @Test
  public void inactiveRewriterLeavesQueryUnchanged() throws Exception {
    PushOneCommit.Result r = createChange();

    List<ChangeInfo> result = query();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).changeId).isEqualTo(r.getChangeId());
  }

  @Test
  public void activeRewriterIsAppliedBeforeSourcePlanning() throws Exception {
    createChange();
    rewriter.narrow(true);

    List<ChangeInfo> result = query();

    // The fake rewriter ANDs in a hashtag that no change has, so the query only returns
    // no matches if the rewrite was actually applied before the change index was searched.
    assertThat(result).isEmpty();
  }

  @Test
  public void rewriterParseExceptionIsSurfacedAsBadRequest() throws Exception {
    createChange();
    rewriter.failWith(new QueryParseException("custom rewrite failure"));

    BadRequestException thrown = assertThrows(BadRequestException.class, this::query);
    assertThat(thrown).hasMessageThat().contains("custom rewrite failure");
  }

  @Test
  public void rewriterReturningNullFailsWithClearErrorInsteadOfNpe() throws Exception {
    createChange();
    rewriter.returnNull(true);

    BadRequestException thrown = assertThrows(BadRequestException.class, this::query);
    assertThat(thrown).hasMessageThat().contains("returned a null query rewrite");
  }

  private List<ChangeInfo> query() throws Exception {
    return gApi.changes().query("status:open project:" + project.get()).get();
  }

  @Singleton
  private static class FakePredicateRewriter implements ChangeQueryPredicateRewriter {
    private volatile boolean narrow;
    private volatile boolean returnNull;
    private volatile QueryParseException failure;

    void narrow(boolean narrow) {
      this.narrow = narrow;
    }

    void returnNull(boolean returnNull) {
      this.returnNull = returnNull;
    }

    void failWith(QueryParseException failure) {
      this.failure = failure;
    }

    void reset() {
      this.narrow = false;
      this.returnNull = false;
      this.failure = null;
    }

    @Override
    @Nullable
    public Predicate<ChangeData> rewrite(Predicate<ChangeData> in) throws QueryParseException {
      if (failure != null) {
        throw failure;
      }
      if (returnNull) {
        return null;
      }
      if (narrow) {
        return Predicate.and(in, ChangePredicates.hashtag("no-change-has-this-hashtag"));
      }
      return in;
    }
  }
}
