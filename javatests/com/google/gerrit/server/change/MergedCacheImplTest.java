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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.ForwardingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.Project;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.server.cache.proto.Cache.MergedKeyProto;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.git.InMemoryInserter;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

public class MergedCacheImplTest {
  private final Project.NameKey project = Project.nameKey("test");
  private final Cache<MergedCacheImpl.Key, Boolean> entries = CacheBuilder.newBuilder().build();
  private final MergedCache cache = new MergedCacheImpl(entries);
  private TestRepository<Repo> repo;

  @Before
  public void setUp() throws Exception {
    repo = new TestRepository<>(new InMemoryRepositoryManager().createRepository(project));
  }

  @Test
  public void cachesOrderedPairsAndIsolatesProjects() throws Exception {
    RevCommit base = repo.commit().create();
    RevCommit tip = repo.commit().parent(base).create();
    RevCommit sibling = repo.commit().parent(base).create();
    try (RevWalk rw = new RevWalk(repo.getRepository())) {
      assertThat(cache.isMergedInto(project, rw, base, tip)).isTrue();
      assertThat(cache.isMergedInto(project, rw, tip, base)).isFalse();
      assertThat(cache.isMergedInto(project, rw, sibling, tip)).isFalse();
      assertThat(cache.isMergedInto(project, rw, tip, tip)).isTrue();
      assertThat(cache.isMergedInto(Project.nameKey("other"), rw, base, tip)).isTrue();
    }
    // A hit does not need a reader, including for successful false answers.
    RevWalk unused = mock(RevWalk.class);
    assertThat(cache.isMergedInto(project, unused, base.copy(), tip.copy())).isTrue();
    assertThat(cache.isMergedInto(project, unused, tip.copy(), base.copy())).isFalse();
    verifyNoInteractions(unused);
    assertThat(entries.size()).isEqualTo(5);
  }

  @Test
  public void normalizesKeysAndSerializesOnlyIds() throws Exception {
    RevCommit base = repo.commit().create();
    RevCommit tip = repo.commit().parent(base).create();
    MergedCacheImpl.Key parsed = new MergedCacheImpl.Key(project, base, tip);
    MergedCacheImpl.Key plain = new MergedCacheImpl.Key(project, base.copy(), tip.copy());
    assertThat(parsed).isEqualTo(plain);
    assertThat(parsed.hashCode()).isEqualTo(plain.hashCode());
    assertThat(parsed.commit().getClass()).isEqualTo(ObjectId.class);
    assertThat(parsed.into().getClass()).isEqualTo(ObjectId.class);
    byte[] bytes = MergedCacheImpl.KeySerializer.INSTANCE.serialize(parsed);
    assertThat(MergedKeyProto.parseFrom(bytes))
        .isEqualTo(
            MergedKeyProto.newBuilder()
                .setProject(Entities.Project_NameKey.newBuilder().setName("test"))
                .setCommit(ObjectIdConverter.create().toByteString(base))
                .setInto(ObjectIdConverter.create().toByteString(tip))
                .build());
    assertThat(MergedCacheImpl.KeySerializer.INSTANCE.deserialize(bytes)).isEqualTo(plain);
    repo.getRevWalk().reset();
    assertThat(parsed.hashCode()).isEqualTo(plain.hashCode());
  }

  @Test
  public void missingObjectIsNotCachedAndCanBeRetried() throws Exception {
    RevCommit tip = repo.commit().create();
    RevCommit base = repo.commit().parent(tip).create();
    RevWalk failing = mock(RevWalk.class);
    IOException failure = new IOException("missing object");
    when(failing.parseCommit(base)).thenThrow(failure);
    assertThat(
            assertThrows(IOException.class, () -> cache.isMergedInto(project, failing, base, tip)))
        .isSameInstanceAs(failure);
    assertThat(entries.size()).isEqualTo(0);
    try (RevWalk rw = new RevWalk(repo.getRepository())) {
      assertThat(cache.isMergedInto(project, rw, base, tip)).isFalse();
    }
  }

  @Test
  public void retriesAfterObjectIsInsertedUsingCallerReader() throws Exception {
    RevCommit base = repo.commit().create();
    CommitBuilder commit = new CommitBuilder();
    commit.setTreeId(base.getTree());
    commit.setParentId(base);
    commit.setAuthor(new PersonIdent("A", "a@example.com"));
    commit.setCommitter(commit.getAuthor());
    commit.setMessage("new commit");
    try (ObjectInserter inserter = new InMemoryInserter(repo.getRepository())) {
      ObjectId tip = inserter.idFor(Constants.OBJ_COMMIT, commit.build());
      try (RevWalk rw = new RevWalk(repo.getRepository())) {
        assertThrows(IOException.class, () -> cache.isMergedInto(project, rw, base, tip));
      }
      assertThat(entries.size()).isEqualTo(0);
      inserter.insert(commit);
      try (RevWalk rw = new RevWalk(inserter.newReader())) {
        assertThat(cache.isMergedInto(project, rw, base, tip)).isTrue();
      }
    }
  }

  @Test
  public void restoresInterruptForNestedCancellation() throws Exception {
    RevWalk failing = mock(RevWalk.class);
    ObjectId id = ObjectId.zeroId();
    when(failing.parseCommit(id)).thenThrow(new IOException(new InterruptedException("cancel")));
    try {
      assertThrows(
          InterruptedIOException.class, () -> cache.isMergedInto(project, failing, id, id));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(entries.size()).isEqualTo(0);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  public void handlesUncheckedLoadFailure() throws Exception {
    IOException cause = new IOException("failure");
    Cache<MergedCacheImpl.Key, Boolean> failing =
        new ForwardingCache.SimpleForwardingCache<>(entries) {
          @Override
          public Boolean get(MergedCacheImpl.Key key, Callable<? extends Boolean> loader)
              throws ExecutionException {
            throw new UncheckedExecutionException(cause);
          }
        };
    assertThat(
            assertThrows(
                IOException.class,
                () ->
                    new MergedCacheImpl(failing)
                        .isMergedInto(
                            project, mock(RevWalk.class), ObjectId.zeroId(), ObjectId.zeroId())))
        .isSameInstanceAs(cause);
  }
}
