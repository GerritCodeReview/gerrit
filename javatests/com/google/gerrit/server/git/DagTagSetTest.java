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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;
import static com.google.gerrit.server.cache.testing.CacheSerializerTestUtil.byteString;
import static org.eclipse.jgit.lib.ObjectReader.OBJ_ANY;
import static org.junit.Assert.assertThrows;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.proto.Cache.DagTagSetHolderProto.DagTagSetProto;
import com.google.gerrit.server.cache.proto.Cache.DagTagSetHolderProto.DagTagSetProto.NodeProto;
import com.google.gerrit.server.git.DecoratedDag.Node;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.commitgraph.CommitGraph;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.GC;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DagTagSetTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void roundTripToProto() {
    DecoratedDag dag = new DecoratedDag();

    Node ic1 = new Node(ObjectId.fromString("0000000000000000000000000000000000000001"));
    dag.setInitialCommit(ic1);

    String branch1 = "refs/heads/branch1";
    Node ic2 =
        new Node(
            ObjectId.fromString("0000000000000000000000000000000000000002"), decorations(branch1));
    dag.setInitialCommit(ic2);

    String tag1 = "refs/tags/tag1";
    Node ic3 =
        new Node(
            ObjectId.fromString("0000000000000000000000000000000000000003"), decorations(tag1));
    dag.setInitialCommit(ic3);

    String tag2 = "refs/tags/tag2";
    String branch2 = "refs/heads/branch2";
    Node ic4 =
        new Node(
            ObjectId.fromString("0000000000000000000000000000000000000004"),
            decorations(tag2, branch2));
    dag.setInitialCommit(ic4);

    String tag3 = "refs/tags/tag3";
    ObjectId idTag3 = ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3");
    dag.decorate(idTag3, tag3);
    Node nodeTag3 = dag.getOrCreate(idTag3);
    nodeTag3.parents.add(ic4);

    ObjectId idMaster = ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    String master = "refs/heads/master";
    dag.decorate(idMaster, master);
    Node nodeMaster = dag.getOrCreate(idMaster);
    nodeMaster.parents.add(nodeTag3);

    String tag4 = "refs/tags/tag4";
    ObjectId idTag4 = ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa4");
    dag.decorate(idTag4, tag4);
    Node nodeTag4 = dag.getOrCreate(idTag4);
    nodeTag4.parents.add(nodeTag3);

    String branch3 = "refs/heads/branch3";
    ObjectId idBranch3 = ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb3");
    dag.decorate(idBranch3, branch3);
    Node nodeBranch3 = dag.getOrCreate(idBranch3);
    nodeBranch3.parents.add(nodeTag4);

    DagTagSet tagSet = new DagTagSet(Project.nameKey("project"), dag);

    DagTagSetProto proto = DagTagSet.Serializer.toProto(tagSet);
    assertThat(proto)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            DagTagSetProto.newBuilder()
                .setProjectName("project")
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01))
                        .addAllDecorations(decorations(""))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02))
                        .addAllDecorations(decorations("", branch1))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03))
                        .addAllDecorations(decorations("", tag1))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04))
                        .addAllDecorations(decorations("", tag2, branch2))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xa3))
                        .addAllDecorations(decorations(tag3))
                        .addAllParentNumbers(parentNumbers(3))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb))
                        .addAllDecorations(decorations(master))
                        .addAllParentNumbers(parentNumbers(4))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa,
                                0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xaa, 0xa4))
                        .addAllDecorations(decorations(tag4))
                        .addAllParentNumbers(parentNumbers(4))
                        .build())
                .addNodes(
                    NodeProto.newBuilder()
                        .setId(
                            byteString(
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb,
                                0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xbb, 0xb3))
                        .addAllDecorations(decorations(branch3))
                        .addAllParentNumbers(parentNumbers(6))
                        .build())
                .build());

    assertEqual(tagSet, DagTagSet.Serializer.fromProto(proto));
    assertEqual(
        tagSet,
        DagTagSet.Serializer.INSTANCE.deserialize(DagTagSet.Serializer.INSTANCE.serialize(tagSet)));
  }

  private Set<String> decorations(String... refs) {
    Set<String> decorations = new HashSet<>();
    for (String ref : refs) {
      decorations.add(ref);
    }
    return decorations;
  }

  private List<Integer> parentNumbers(Integer... parents) {
    List<Integer> parentNums = new ArrayList<>();
    for (Integer parent : parents) {
      parentNums.add(parent);
    }
    return parentNums;
  }

  @Test
  public void tagSetFields() {
    assertThatSerializedClass(DagTagSet.class)
        .hasFields(
            ImmutableMap.of(
                "projectName",
                Project.NameKey.class,
                "dag",
                DecoratedDag.class,
                "nonCommits",
                new TypeLiteral<Set<ObjectId>>() {}.getType(),
                "unreadable",
                new TypeLiteral<Set<ObjectId>>() {}.getType()));
  }

  @Test
  public <V> void dagFields() {
    assertThatSerializedClass(DecoratedDag.class)
        .extendsClass(new TypeLiteral<ObjectIdOwnerMap<Node>>() {}.getType());

    assertThatSerializedClass(DecoratedDag.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("decorated", new TypeLiteral<Set<Node>>() {}.getType())
                .put("directory", new TypeLiteral<Node[][]>() {}.getType())
                .put("size", int.class)
                .put("grow", int.class)
                .put("bits", int.class)
                .put("mask", int.class)
                .build());
  }

  @Test
  public void nodeFields() {
    assertThatSerializedClass(Node.class).extendsClass(ObjectIdOwnerMap.Entry.class);
    assertThatSerializedClass(Node.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("decorations", new TypeLiteral<Set<String>>() {}.getType())
                .put("parents", new TypeLiteral<Set<Node>>() {}.getType())
                .put("next", ObjectIdOwnerMap.Entry.class)
                .put("w1", int.class)
                .put("w2", int.class)
                .put("w3", int.class)
                .put("w4", int.class)
                .put("w5", int.class)
                .build());
  }

  @Test
  public void lookupDoesNotReadTheObjectOfATagOfANonCommitAgain() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    InMemoryRepository repo = countingRepo(reads);
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update(MASTER, tr.commit().create());
      tr.update("refs/tags/blob", tr.tag("blob", tr.blob("x")));
    }
    DagTagSet tagSet = build(repo);
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef(MASTER));
    ImmutableList<Ref> tags =
        ImmutableList.copyOf(repo.getRefDatabase().getRefsByPrefix("refs/tags/"));

    reads.set(0);
    DagTagMatcher m = matcherWith(ImmutableMap.of());
    assertThat(tagSet.getReachableTags(repo, m, include, tags)).isNotNull();
    assertThat(m.toUpdate).isEmpty();
    // The DAG remembers that the tag does not point to a commit, so a lookup reads no objects.
    assertThat(reads.get()).isEqualTo(0);
  }

  @Test
  public void buildParsesCommitsFromCommitGraph() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    FileRepository repo =
        new FileRepository(temporaryFolder.newFolder("project.git")) {
          @Override
          public ObjectReader newObjectReader() {
            ObjectReader delegate = super.newObjectReader();
            return new ObjectReader.Filter() {
              @Override
              protected ObjectReader delegate() {
                return delegate;
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId) throws IOException {
                return open(objectId, OBJ_ANY);
              }

              @Override
              public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
                reads.incrementAndGet();
                return super.open(objectId, typeHint);
              }

              @Override
              public Optional<CommitGraph> getCommitGraph() throws IOException {
                return delegate.getCommitGraph();
              }
            };
          }
        };
    repo.create(true);
    try (TestRepository<FileRepository> tr = new TestRepository<>(repo)) {
      RevCommit c1 = tr.commit().create();
      RevCommit c2 = tr.commit().parent(c1).create();
      tr.update(MASTER, tr.commit().parent(c2).create());
      tr.update("refs/tags/v1", c1);
    }
    StoredConfig cfg = repo.getConfig();
    cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, "commitGraph", true);
    cfg.setBoolean(
        ConfigConstants.CONFIG_GC_SECTION,
        null,
        ConfigConstants.CONFIG_KEY_WRITE_COMMIT_GRAPH,
        true);
    cfg.save();
    new GC(repo).gc().get();

    reads.set(0);
    DagTagSet tagSet = build(repo);
    // Only the branch and tag tips are read; the commit between them comes from the graph.
    assertThat(reads.get()).isEqualTo(2);
    assertThat(reachableTags(repo, tagSet, MASTER)).hasSize(1);
  }

  @Test
  public void buildStopsReadingObjectsWhenInterrupted() throws Exception {
    AtomicInteger interruptedReads = new AtomicInteger();
    InMemoryRepository repo = interruptibleRepo(interruptedReads, new AtomicReference<>());
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      for (int i = 0; i < 10; i++) {
        tr.update("refs/heads/branch" + i, tr.commit().parent(root).create());
      }
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }

    Thread.currentThread().interrupt();
    try {
      assertThat(new DagTagSet(Project.nameKey("project")).build(repo)).isFalse();
    } finally {
      Thread.interrupted();
    }

    // The first failed read must abort the build instead of failing (and logging) once per ref.
    assertThat(interruptedReads.get()).isEqualTo(1);
  }

  /**
   * Interrupts a build while the history is walked, then checks that the incomplete DagTagSet was
   * not cached and that the next lookup finds the tag reachable.
   */
  @Test
  public void tagSetInterruptedDuringHistoryWalkIsNotCached() throws Exception {
    AtomicReference<ObjectId> failOn = new AtomicReference<>();
    InMemoryRepository repo = interruptibleRepo(new AtomicInteger(), failOn);
    Ref tagRef;
    RevCommit middle;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      middle = tr.commit().parent(root).create();
      tr.update(MASTER, tr.commit().parent(middle).create());
      tr.update("refs/tags/v1", tr.tag("v1", root));
      tagRef = repo.exactRef("refs/tags/v1");
    }
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef(MASTER));
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");

    // The middle commit is only read while walking the history, i.e. after all refs and tags have
    // already been added to the DAG.
    failOn.set(middle.copy());
    assertThrows(
        InterruptedIOException.class,
        () -> tagCache.get(project).matcher(tagCache, repo, include, tags));
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNull();

    failOn.set(null);
    DagTagMatcher m = tagCache.get(project).matcher(tagCache, repo, include, tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNotNull();
  }

  @Test
  public void interruptedRefreshDoesNotReplaceTheCachedTagSet() throws Exception {
    AtomicReference<ObjectId> failOn = new AtomicReference<>();
    InMemoryRepository repo = interruptibleRepo(new AtomicInteger(), failOn);
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update(MASTER, root);
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused =
        tagCache
            .get(project)
            .matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    DagTagSet cached = guavaCache.getIfPresent(project.get()).getTagSet();

    RevCommit next;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      next = tr.commit().parent(root).create();
      tr.update(MASTER, next);
    }
    failOn.set(next.copy());
    assertThrows(
        InterruptedIOException.class,
        () ->
            tagCache
                .get(project)
                .matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags));
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isSameInstanceAs(cached);
  }

  @Test
  public void tagOnCommitsAddedByAFastForwardBecomesReachable() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    RevCommit tagged = tr.commit().parent(root).create();
    tr.update("refs/tags/v1", tr.tag("v1", tagged));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    DagTagMatcher m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isFalse();

    tr.update(MASTER, tr.commit().parent(tagged).create());
    m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
  }

  @Test
  public void tagOnCommitsAddedByAForcePushBecomesReachable() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, tr.commit().parent(root).create());
    RevCommit tagged = tr.commit().parent(root).create();
    tr.update("refs/tags/v1", tr.tag("v1", tagged));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    DagTagMatcher m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isFalse();

    // Not a fast-forward: the old tip of master is not an ancestor of the new one.
    tr.update(MASTER, tr.commit().parent(tagged).create());
    m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
  }

  @Test
  public void unreadableRefOnlyHidesTheTagsItReaches() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update(MASTER, root);
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);

    // A ref the DAG does not know yet, to a commit that cannot be read.
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit broken = tr.commit().parent(root).create();
      tr.update("refs/heads/broken", broken);
      missing.set(broken.copy());
    }
    DagTagMatcher m =
        holder.matcher(
            tagCache,
            repo,
            ImmutableList.of(repo.exactRef(MASTER), repo.exactRef("refs/heads/broken")),
            tags);
    assertThat(m.isReachable(tagRef)).isTrue();
  }

  @Test
  public void refWithUnreadableHistoryDoesNotRefreshTheDagOnEveryLookup() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update(MASTER, root);
      tr.update("refs/tags/v1", tr.tag("v1", root));
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);

    // The tip of the new branch can be read, the commit below it cannot.
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit unreadable = tr.commit().parent(root).create();
      tr.update("refs/heads/broken", tr.commit().parent(unreadable).create());
      missing.set(unreadable.copy());
    }
    ImmutableList<Ref> include =
        ImmutableList.of(repo.exactRef(MASTER), repo.exactRef("refs/heads/broken"));
    assertThat(holder.matcher(tagCache, repo, include, tags).isReachable(tagRef)).isTrue();
    DagTagSet afterFirst = holder.getTagSet();

    DagTagMatcher m = holder.matcher(tagCache, repo, include, tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(m.toUpdate).isEmpty();
    assertThat(holder.getTagSet()).isSameInstanceAs(afterFirst);
  }

  /**
   * A lookup read master before a push, and another lookup already published the DAG with master
   * after the push. The tags reachable from the commit the first lookup read must still be found.
   */
  @Test
  public void lookupWithARefOlderThanTheDagFindsItsTags() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v1", tr.tag("v1", root));
    RevCommit a = tr.commit().parent(root).create();
    tr.update(MASTER, tr.commit().parent(a).create());
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    Ref old = repo.exactRef(MASTER);

    tr.update(MASTER, tr.commit().parent(tr.getRevWalk().parseCommit(old.getObjectId())).create());
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    DagTagSet published = holder.getTagSet();

    DagTagMatcher m = holder.matcher(tagCache, repo, ImmutableList.of(old), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(holder.getTagSet()).isSameInstanceAs(published);
  }

  /**
   * A ref that no lookup includes moved. A refresh for another ref must update it too, so that its
   * decoration does not stay on a commit that may be gone after a gc.
   */
  @Test
  public void refreshUpdatesRefsTheLookupDidNotInclude() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    tr.update("refs/heads/hidden", root);
    tr.update("refs/tags/v1", tr.tag("v1", root));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);

    RevCommit moved = tr.commit().parent(root).create();
    tr.update("refs/heads/hidden", moved);
    tr.update(MASTER, tr.commit().parent(root).create());
    var unused2 = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);

    assertThat(holder.getTagSet().dag.get(moved).decorations).contains("refs/heads/hidden");
  }

  /**
   * Refs to commits that cannot be read, from before and after the DAG was built, are read once and
   * then treated as reaching no tag, not read again on every lookup.
   */
  @Test
  public void refToAnUnreadableCommitIsNotReadOnEveryLookup() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    AtomicInteger missingReads = new AtomicInteger();
    InMemoryRepository repo = missingObjectRepo(missing, missingReads);
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update(MASTER, root);
      tr.update("refs/tags/v1", tr.tag("v1", root));
      RevCommit broken = tr.commit().parent(root).create();
      tr.update("refs/heads/broken", broken);
      missing.set(broken.copy());
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    ImmutableList<Ref> include =
        ImmutableList.of(repo.exactRef(MASTER), repo.exactRef("refs/heads/broken"));

    assertThat(holder.matcher(tagCache, repo, include, tags).isReachable(tagRef)).isTrue();
    missingReads.set(0);
    assertThat(holder.matcher(tagCache, repo, include, tags).isReachable(tagRef)).isTrue();
    assertThat(missingReads.get()).isEqualTo(0);

    // A ref the DAG does not know yet.
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit later = tr.commit().parent(root).create();
      tr.update("refs/heads/later", later);
      missing.set(later.copy());
    }
    include = ImmutableList.of(repo.exactRef(MASTER), repo.exactRef("refs/heads/later"));
    assertThat(holder.matcher(tagCache, repo, include, tags).isReachable(tagRef)).isTrue();
    missingReads.set(0);
    assertThat(holder.matcher(tagCache, repo, include, tags).isReachable(tagRef)).isTrue();
    assertThat(missingReads.get()).isEqualTo(0);
  }

  /**
   * A ref outside refs/tags/, e.g. a mirror ref, points to an annotated tag object. It reaches the
   * tags in the history of the tagged commit, whether it existed when the DAG was built or came
   * later.
   */
  @Test
  public void nonTagRefToAnAnnotatedTagReachesTheTagsOfItsCommit() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v0", tr.tag("v0", root));
    RevCommit tip = tr.commit().parent(root).create();
    tr.update(MASTER, tip);
    tr.update("refs/upstream/old", tr.tag("old", tip));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v0");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);

    DagTagMatcher m =
        holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef("refs/upstream/old")), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(m.toUpdate).isEmpty();

    tr.update("refs/upstream/new", tr.tag("new", tip));
    m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef("refs/upstream/new")), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef("refs/upstream/new")), tags);
    assertThat(m.toUpdate).isEmpty();
  }

  @Test
  public void lookupAfterARefIsDeletedHidesTagsOnlyItReached() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    RevCommit side = tr.commit().parent(root).create();
    tr.update("refs/heads/side", side);
    tr.update("refs/tags/side-tag", tr.tag("side-tag", side));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");
    Ref sideTag = repo.exactRef("refs/tags/side-tag");
    DagTagMatcher m =
        tagCache
            .get(project)
            .matcher(
                tagCache,
                repo,
                repo.getRefDatabase().getRefsByPrefix("refs/heads/"),
                ImmutableList.of(sideTag));
    assertThat(m.isReachable(sideTag)).isTrue();

    tr.delete("refs/heads/side");
    tr.update(MASTER, tr.commit().parent(root).create());
    m =
        tagCache
            .get(project)
            .matcher(
                tagCache,
                repo,
                repo.getRefDatabase().getRefsByPrefix("refs/heads/"),
                ImmutableList.of(sideTag));
    assertThat(m.isReachable(sideTag)).isFalse();
    assertThat(decorations(guavaCache.getIfPresent(project.get()).getTagSet()))
        .doesNotContain("refs/heads/side");
  }

  private static final String MASTER = "refs/heads/master";

  private static TestRepository<Repo> newRepo() throws Exception {
    return new TestRepository<>(
        new InMemoryRepositoryManager().createRepository(Project.nameKey("project")));
  }

  private static DagTagSet build(Repository repo) throws Exception {
    DagTagSet tagSet = new DagTagSet(Project.nameKey("project"));
    assertThat(tagSet.build(repo)).isTrue();
    return tagSet;
  }

  private static DagTagMatcher matcherWith(Map<String, ObjectId> idByRef) throws Exception {
    DagTagMatcher m =
        new DagTagMatcher(
            null,
            null,
            null,
            ImmutableList.of(),
            new DagTagSet(Project.nameKey("p")),
            ImmutableList.of());
    m.toUpdate.putAll(idByRef);
    return m;
  }

  private static Set<? extends ObjectId> reachableTags(
      Repository repo, DagTagSet tagSet, String ref) throws Exception {
    DagTagMatcher m = matcherWith(ImmutableMap.of());
    Set<? extends ObjectId> tags =
        tagSet.getReachableTags(
            repo,
            m,
            ImmutableList.of(repo.exactRef(ref)),
            repo.getRefDatabase().getRefsByPrefix("refs/tags/"));
    assertWithMessage("out of date: %s", m.toUpdate).that(tags).isNotNull();
    return tags;
  }

  private static Set<String> decorations(DagTagSet tagSet) {
    Set<String> refs = new HashSet<>();
    tagSet.dag.decorated.forEach(n -> refs.addAll(n.decorations));
    return refs;
  }

  /** A repository that counts object reads. */
  private static InMemoryRepository countingRepo(AtomicInteger reads) {
    return new InMemoryRepository(new DfsRepositoryDescription("project")) {
      @Override
      public ObjectReader newObjectReader() {
        ObjectReader delegate = super.newObjectReader();
        return new ObjectReader.Filter() {
          @Override
          protected ObjectReader delegate() {
            return delegate;
          }

          @Override
          public ObjectLoader open(AnyObjectId objectId) throws IOException {
            return open(objectId, OBJ_ANY);
          }

          @Override
          public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
            reads.incrementAndGet();
            return super.open(objectId, typeHint);
          }
        };
      }
    };
  }

  /** A repository in which reading {@code missing} fails as if the object did not exist. */
  private static InMemoryRepository missingObjectRepo(AtomicReference<ObjectId> missing) {
    return missingObjectRepo(missing, new AtomicInteger());
  }

  /** Like {@link #missingObjectRepo(AtomicReference)}, counting the reads of {@code missing}. */
  private static InMemoryRepository missingObjectRepo(
      AtomicReference<ObjectId> missing, AtomicInteger missingReads) {
    return new InMemoryRepository(new DfsRepositoryDescription("project")) {
      @Override
      public ObjectReader newObjectReader() {
        ObjectReader delegate = super.newObjectReader();
        return new ObjectReader.Filter() {
          @Override
          protected ObjectReader delegate() {
            return delegate;
          }

          @Override
          public ObjectLoader open(AnyObjectId objectId) throws IOException {
            return open(objectId, OBJ_ANY);
          }

          @Override
          public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
            if (objectId.equals(missing.get())) {
              missingReads.incrementAndGet();
              throw new MissingObjectException(objectId.copy(), "unknown");
            }
            return super.open(objectId, typeHint);
          }
        };
      }
    };
  }

  /**
   * Mimics JGit's file-based object reader, which throws InterruptedIOException for every object
   * read once the current thread is interrupted. Reading {@code failOn} also throws.
   */
  private static InMemoryRepository interruptibleRepo(
      AtomicInteger interruptedReads, AtomicReference<ObjectId> failOn) {
    return new InMemoryRepository(new DfsRepositoryDescription("project")) {
      @Override
      public ObjectReader newObjectReader() {
        ObjectReader delegate = super.newObjectReader();
        return new ObjectReader.Filter() {
          @Override
          protected ObjectReader delegate() {
            return delegate;
          }

          @Override
          public ObjectLoader open(AnyObjectId objectId) throws IOException {
            return open(objectId, OBJ_ANY);
          }

          @Override
          public ObjectLoader open(AnyObjectId objectId, int typeHint) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
              interruptedReads.incrementAndGet();
              throw new InterruptedIOException();
            }
            if (objectId.equals(failOn.get())) {
              throw new InterruptedIOException();
            }
            return super.open(objectId, typeHint);
          }
        };
      }
    };
  }

  static void assertEqual(@Nullable DagTagSet a, @Nullable DagTagSet b) {
    if (a == null || b == null) {
      assertWithMessage("only one DagTagSet is null out of\n%s\n%s", a, b)
          .that(a == null && b == null)
          .isTrue();
      return;
    }
    assertThat(a.getProjectName()).isEqualTo(b.getProjectName());
    assertThat(a.dag.decorated).containsExactlyElementsIn(b.dag.decorated);
  }
}
