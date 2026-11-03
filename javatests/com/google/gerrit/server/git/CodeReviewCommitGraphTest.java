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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests that {@link CodeReviewRevWalk} parses commits from the commit-graph when it can. */
public class CodeReviewCommitGraphTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private FileRepository repo;
  private TestRepository<FileRepository> tr;
  private RevCommit tip;

  @Before
  public void setUp() throws Exception {
    repo = new FileRepository(temporaryFolder.newFolder("repo.git"));
    repo.create(true);
    tr = new TestRepository<>(repo);
    RevCommit c1 = tr.commit().message("c1").create();
    RevCommit c2 = tr.commit().parent(c1).message("c2").create();
    tip = tr.commit().parent(c2).message("c3").create();
    tr.update("refs/heads/master", tip);
  }

  @Test
  public void commitsAreParsedFromCommitGraph() throws Exception {
    writeCommitGraph();
    assertThat(objectsOpenedWalkingFromTip()).isEqualTo(0);
  }

  @Test
  public void commitsAreParsedWithoutCommitGraph() throws Exception {
    assertThat(objectsOpenedWalkingFromTip()).isEqualTo(3);
  }

  private int objectsOpenedWalkingFromTip() throws Exception {
    CountingReader reader = new CountingReader(repo.newObjectReader());
    try (CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(reader)) {
      rw.setRetainBody(false);
      rw.markStart(rw.lookupCommit(tip));
      int commits = 0;
      while (rw.next() != null) {
        commits++;
      }
      assertThat(commits).isEqualTo(3);
      return reader.opens.get();
    }
  }

  private void writeCommitGraph() throws Exception {
    StoredConfig cfg = repo.getConfig();
    cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, "commitGraph", true);
    cfg.setBoolean(
        ConfigConstants.CONFIG_GC_SECTION,
        null,
        ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH,
        true);
    cfg.save();
    new GC(repo).gc().get();
  }

  /** Reader that counts object loads and keeps exposing the commit-graph of the wrapped reader. */
  private static class CountingReader extends ObjectReader.Filter {
    final AtomicInteger opens = new AtomicInteger();
    private final ObjectReader delegate;

    CountingReader(ObjectReader delegate) {
      this.delegate = delegate;
    }

    @Override
    protected ObjectReader delegate() {
      return delegate;
    }

    @Override
    public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
      opens.incrementAndGet();
      return super.open(objectId, typeHint);
    }

    @Override
    public Optional<CommitGraph> getCommitGraph() throws IOException {
      return delegate.getCommitGraph();
    }
  }
}
