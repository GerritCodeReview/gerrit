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

package com.google.gerrit.server.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteString;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.server.cache.proto.Cache.ChangeKindKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.change.ChangeKindCacheImpl.NoCache;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class ChangeKindCacheImplTest {
  private InMemoryRepositoryManager repoManager;
  private ChangeKindCache changeKindCache;

  @Before
  public void setUp() {
    repoManager = new InMemoryRepositoryManager();
    // For simplicity, we use non-caching version, and as long as we call the method that doesn't
    // use ChangeData, we can provide null instead of constructing a factory.
    changeKindCache = new NoCache(new Config(), null, repoManager);
  }

  @Test
  public void keySerializer() throws Exception {
    ChangeKindCacheImpl.Key key =
        ChangeKindCacheImpl.Key.create(
            ObjectId.zeroId(),
            ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
            "aStrategy");
    CacheSerializer<ChangeKindCacheImpl.Key> s = new ChangeKindCacheImpl.Key.Serializer();
    byte[] serialized = s.serialize(key);
    assertThat(ChangeKindKeyProto.parseFrom(serialized))
        .isEqualTo(
            ChangeKindKeyProto.newBuilder()
                .setPrior(byteString(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                .setNext(
                    byteString(
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef,
                        0xde, 0xad, 0xbe, 0xef, 0xde, 0xad, 0xbe, 0xef))
                .setStrategyName("aStrategy")
                .build());
    assertThat(s.deserialize(serialized)).isEqualTo(key);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void keyFields() throws Exception {
    assertThatSerializedClass(ChangeKindCacheImpl.Key.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "prior", ObjectId.class, "next", ObjectId.class, "strategyName", String.class));
  }

  @Test
  public void commitMessageChanged() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().create();
    RevCommit firstRev = p.commit().parent(root).message("Commit message").create();
    RevCommit secondRev = p.commit().parent(root).message("Commit message update").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.NO_CODE_CHANGE);
  }

  @Test
  public void sameObject_noChange() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().create();
    RevCommit rev =
        p.commit().parent(root).message("Commit message").add("test.md", "Hello").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(), null, null, null, rev, rev))
        .isEqualTo(ChangeKind.NO_CHANGE);
  }

  @Test
  public void sameContent_noChange() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("test.md", "Hello").create();
    RevCommit secondRev =
        p.commit().parent(root).message("Commit message").add("test.md", "Hello").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.NO_CHANGE);
  }

  @Test
  public void contentChanged_rework() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("test.md", "Hello").create();
    RevCommit secondRev =
        p.commit().parent(root).message("Commit message").add("test.md", "Goodbye").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.REWORK);
  }

  @Test
  public void mergeConflict_rework() throws Exception {
    // Delete a change in one of the parents
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().add("foo", "foo-text").create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("foo", "bar-text").create();
    // File was deleted, but the commit is still writing new content to it.
    RevCommit newRoot = p.commit().parent(root).rm("foo").create();
    RevCommit secondRev =
        p.commit().parent(newRoot).message("Commit message").add("foo", "bar-text").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.REWORK);
  }

  @Test
  public void rebaseThenEdit_rework() throws Exception {
    // Delete a change in one of the parents
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().add("foo", "foo-text").create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("foo", "bar-text").create();
    // Unrelated file was added.
    RevCommit newRoot = p.commit().parent(root).add("baz", "baz-text").create();
    RevCommit secondRev =
        p.commit().parent(newRoot).message("Commit message").add("foo", "foobar-text").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.REWORK);
  }

  @Test
  public void trivialRebase() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().add("foo", "foo-text").create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("foo", "bar-text").create();
    // Unrelated file was added.
    RevCommit newRoot = p.commit().parent(root).add("baz", "baz-text").create();
    RevCommit secondRev =
        p.commit().parent(newRoot).message("Commit message").add("foo", "bar-text").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.TRIVIAL_REBASE);
  }

  @Test
  public void trivialRebaseCommitMessage() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root = p.commit().add("foo", "foo-text").create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("foo", "bar-text").create();
    // Unrelated file was added.
    RevCommit newRoot = p.commit().parent(root).add("baz", "baz-text").create();
    RevCommit secondRev =
        p.commit().parent(newRoot).message("Commit subject").add("foo", "bar-text").create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.TRIVIAL_REBASE_WITH_MESSAGE_UPDATE);
  }

  @Test
  public void trivialRebaseUnionContentMerge() throws Exception {
    TestRepository<Repo> p = newRepo("p");
    RevCommit root =
        p.commit().add("foo.txt", "foo-text").add(".gitattributes", "*.txt merge=union").create();
    RevCommit firstRev =
        p.commit().parent(root).message("Commit message").add("foo.txt", "bar-text").create();
    // Same file was added.
    RevCommit newRoot = p.commit().parent(root).add("foo.txt", "baz-text").create();
    // Simulate the rebase adding content from both without conflict markers
    RevCommit secondRev =
        p.commit()
            .parent(newRoot)
            .message("Commit message")
            .add("foo.txt", "baz-text\nbar-text")
            .create();

    assertThat(
            changeKindCache.getChangeKind(
                p.getRepository().getDescription().getProject(),
                null,
                null,
                null,
                firstRev,
                secondRev))
        .isEqualTo(ChangeKind.TRIVIAL_REBASE);
  }

  private TestRepository<Repo> newRepo(String name) throws Exception {
    return new TestRepository<>(repoManager.createRepository(Project.nameKey(name)));
  }
}
