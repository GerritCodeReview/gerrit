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
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.NodeProto;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class TagSetTest {
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

    TagSet tagSet = new TagSet(Project.nameKey("project"), dag);

    TagSetProto proto = TagSet.Serializer.toProto(tagSet);
    assertThat(proto)
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            TagSetProto.newBuilder()
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

    assertEqual(tagSet, TagSet.Serializer.fromProto(proto));
    assertEqual(
        tagSet,
        TagSet.Serializer.INSTANCE.deserialize(TagSet.Serializer.INSTANCE.serialize(tagSet)));
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
    assertThatSerializedClass(TagSet.class)
        .hasFields(
            ImmutableMap.of(
                "projectName", Project.NameKey.class,
                "dag", DecoratedDag.class,
                "nonCommits", new TypeLiteral<Set<ObjectId>>() {}.getType()));
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
  public void refreshRemovesUndecoratedNodesFromDecoratedSet() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit c0 = tr.commit().create();
    RevCommit c1 = tr.commit().parent(c0).create();
    RevCommit c2 = tr.commit().parent(c1).create();
    tr.update(MASTER, c1);

    TagSet tagSet = build(repo);

    tr.update(MASTER, c2);
    tagSet.refresh(repo, ImmutableMap.of(MASTER, c2.copy()));

    Node newNode = tagSet.dag.get(c2);
    assertThat(tagSet.dag.decorated).contains(newNode);
    assertThat(tagSet.dag.get(c1)).isNull();
    assertThat(newNode.parents).containsExactly(tagSet.dag.get(c0));
  }

  @Test
  public void dagSizeStaysBoundedWhenARefMovesRepeatedly() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v1", tr.tag("v1", root));
    RevCommit tip = tr.commit().parent(root).create();
    tr.update(MASTER, tip);
    TagSet tagSet = build(repo);
    int initial = tagSet.dag.decorated.size();

    for (int i = 0; i < 50; i++) {
      tip = tr.commit().parent(tip).create();
      tr.update(MASTER, tip);
      tagSet = refreshCopy(repo, tagSet, MASTER, tip);
    }

    assertThat(tagSet.dag.decorated).hasSize(initial);
    assertThat(tagSet.dag.size()).isEqualTo(initial);
    assertThat(reachableTags(repo, tagSet, MASTER)).containsExactly(root.copy());
  }

  @Test
  public void removingAnUndecoratedNodeKeepsReachabilityThroughIt() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v1", tr.tag("v1", root));
    RevCommit mid = tr.commit().parent(root).create();
    tr.update("refs/heads/mid", mid);
    RevCommit top = tr.commit().parent(mid).create();
    tr.update(MASTER, top);
    TagSet tagSet = build(repo);
    assertThat(tagSet.dag.get(top).parents).containsExactly(tagSet.dag.get(mid));

    // Deleting refs/heads/mid leaves the mid node undecorated; master must still reach v1.
    tr.delete("refs/heads/mid");
    TagSet updated = new TagSet(tagSet.getProjectName());
    assertThat(updated.build(repo, tagSet, matcherWith(ImmutableMap.of()))).isTrue();
    assertThat(updated.removeDeletedRefs(refNames(repo))).isTrue();

    assertThat(updated.dag.get(mid)).isNull();
    assertThat(updated.dag.get(top).parents).containsExactly(updated.dag.get(root));
    assertThat(reachableTags(repo, updated, MASTER)).containsExactly(root.copy());
  }

  @Test
  public void newTagIsAddedWithoutWalkingTheWholeHistory() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    InMemoryRepository repo = countingRepo(reads);
    RevCommit tip;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tip = tr.commit().create();
      tr.update("refs/tags/v0", tr.tag("v0", tip));
      for (int i = 0; i < 200; i++) {
        tip = tr.commit().parent(tip).create();
      }
      tr.update(MASTER, tip);
    }
    TagSet tagSet = build(repo);

    RevCommit tagged;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tagged = tr.commit().parent(tip).create();
      tr.update("refs/tags/v1", tr.tag("v1", tagged));
      tr.update(MASTER, tr.commit().parent(tagged).create());
    }

    reads.set(0);
    TagSet updated =
        refreshCopy(
            repo,
            tagSet,
            ImmutableMap.of(
                "refs/tags/v1", tagged.copy(), MASTER, repo.exactRef(MASTER).getObjectId()));

    // Only the new commits are read, not the 200 commits of history below master's old tip.
    assertThat(reads.get()).isLessThan(20);
    assertThat(reachableTags(repo, updated, MASTER))
        .containsExactly(tagged.copy(), updated.dag.get(tagSetRoot(updated)).copy());
  }

  @Test
  public void refsInsertedByOneRefreshAreLinkedToEachOther() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v0", tr.tag("v0", root));
    RevCommit a = tr.commit().parent(root).create();
    RevCommit b = tr.commit().parent(a).create();
    RevCommit tip = tr.commit().parent(b).create();
    tr.update(MASTER, tip);
    // Each insert must see the edges the previous one added, in either order. refresh() applies
    // the refs in the order of the (ordered) map.
    for (ImmutableMap<String, ObjectId> idByRef :
        ImmutableList.of(
            ImmutableMap.<String, ObjectId>of("refs/tags/a", a.copy(), "refs/tags/b", b.copy()),
            ImmutableMap.<String, ObjectId>of("refs/tags/b", b.copy(), "refs/tags/a", a.copy()))) {
      tr.delete("refs/tags/a");
      tr.delete("refs/tags/b");
      TagSet updated = build(repo);
      tr.update("refs/tags/a", tr.tag("a", a));
      tr.update("refs/tags/b", tr.tag("b", b));
      updated.refresh(repo, idByRef);
      assertThat(updated.dag.get(tip).parents).containsExactly(updated.dag.get(b));
      assertThat(updated.dag.get(b).parents).containsExactly(updated.dag.get(a));
      assertThat(updated.dag.get(a).parents).containsExactly(updated.dag.get(root));
      assertThat(reachableTags(repo, updated, MASTER))
          .containsExactly(root.copy(), a.copy(), b.copy());
    }
  }

  @Test
  public void refreshMovingARefAndInsertingBelowItsOldCommitKeepsTheDagClosed() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v0", tr.tag("v0", root));
    RevCommit mid = tr.commit().parent(root).create();
    RevCommit x = tr.commit().parent(mid).create();
    tr.update("refs/heads/a", x);
    tr.update(MASTER, tr.commit().parent(x).create());
    TagSet tagSet = build(repo);

    // Moving refs/heads/a leaves x undecorated, then inserting refs/heads/b below x walks through
    // x and replaces its parents with commits that are not in the DAG.
    RevCommit y = tr.commit().parent(root).create();
    RevCommit z = tr.commit().parent(x).create();
    tr.update("refs/heads/a", y);
    tr.update("refs/heads/b", z);
    tagSet.refresh(
        repo,
        ImmutableMap.<String, ObjectId>of("refs/heads/a", y.copy(), "refs/heads/b", z.copy()));

    for (Node node : tagSet.dag.decorated) {
      for (Node parent : node.parents) {
        assertWithMessage("parent of %s", node.name()).that(tagSet.dag.decorated).contains(parent);
      }
    }
    assertEqual(tagSet, TagSet.Serializer.fromProto(TagSet.Serializer.toProto(tagSet)));
    assertThat(reachableTags(repo, tagSet, MASTER)).containsExactly(root.copy());
    assertThat(reachableTags(repo, refreshCopy(repo, tagSet, ImmutableMap.of()), "refs/heads/b"))
        .containsExactly(root.copy());
  }

  @Test
  public void insertedNodeKeepsItsParentsWhenItWasAlreadyInTheDag() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    RevCommit mid = tr.commit().parent(root).create();
    tr.update("refs/tags/v0", tr.tag("v0", root));
    tr.update("refs/heads/a", mid);
    tr.update(MASTER, tr.commit().parent(mid).create());
    TagSet tagSet = build(repo);

    // Move refs/heads/a away (mid becomes undecorated), then tag mid: it is inserted again.
    RevCommit other = tr.commit().parent(root).create();
    tr.update("refs/heads/a", other);
    tagSet = refreshCopy(repo, tagSet, "refs/heads/a", other);
    tr.update("refs/tags/v1", tr.tag("v1", mid));
    tagSet = refreshCopy(repo, tagSet, "refs/tags/v1", mid);

    assertThat(tagSet.dag.get(mid).parents).containsExactly(tagSet.dag.get(root));
    assertThat(reachableTags(repo, tagSet, MASTER)).containsExactly(root.copy(), mid.copy());
    assertThat(reachableTags(repo, tagSet, "refs/heads/a")).containsExactly(root.copy());
  }

  @Test
  public void lookupDoesNotReadTheObjectOfATagOfANonCommitAgain() throws Exception {
    AtomicInteger reads = new AtomicInteger();
    InMemoryRepository repo = countingRepo(reads);
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update(MASTER, tr.commit().create());
      tr.update("refs/tags/blob", tr.tag("blob", tr.blob("x")));
    }
    TagSet tagSet = build(repo);
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef(MASTER));
    ImmutableList<Ref> tags =
        ImmutableList.copyOf(repo.getRefDatabase().getRefsByPrefix("refs/tags/"));

    reads.set(0);
    TagMatcher m = matcherWith(ImmutableMap.of());
    assertThat(tagSet.getReachableTags(repo, m, include, tags)).isNotNull();
    assertThat(m.toUpdate).isEmpty();
    // The DAG remembers that the tag does not point to a commit, so a lookup reads no objects.
    assertThat(reads.get()).isEqualTo(0);
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
      assertThat(new TagSet(Project.nameKey("project")).build(repo, null, null)).isFalse();
    } finally {
      Thread.interrupted();
    }

    // The first failed read must abort the build instead of failing (and logging) once per ref.
    assertThat(interruptedReads.get()).isEqualTo(1);
  }

  /**
   * Interrupts a build while the history is walked, then checks that the incomplete TagSet was not
   * cached and that the next lookup finds the tag reachable.
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
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");

    // The middle commit is only read while walking the history, i.e. after all refs and tags have
    // already been added to the DAG.
    failOn.set(middle.copy());
    assertThrows(
        InterruptedIOException.class,
        () -> tagCache.get(project).matcher(tagCache, repo, include, tags));
    assertThat(guavaCache.getIfPresent(project.get()).getTagSet()).isNull();

    failOn.set(null);
    TagMatcher m = tagCache.get(project).matcher(tagCache, repo, include, tags);
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
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused =
        tagCache
            .get(project)
            .matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    TagSet cached = guavaCache.getIfPresent(project.get()).getTagSet();

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

  /**
   * Two lookups see the same DAG and find different refs out of date. The second one refreshes
   * after the first published its DAG; it must build on that DAG, not on the one it looked at,
   * which would drop the first lookup's update.
   */
  @Test
  public void concurrentLookupsApplyTheirUpdatesOnTopOfEachOther() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    tr.update("refs/tags/v1", tr.tag("v1", root));
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    TagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    TagSet seenByBoth = holder.getTagSet();

    tr.update("refs/heads/a", tr.commit().parent(root).create());
    tr.update("refs/heads/b", tr.commit().parent(root).create());
    TagMatcher first =
        new TagMatcher(
            holder,
            tagCache,
            repo,
            ImmutableList.of(repo.exactRef("refs/heads/a")),
            seenByBoth,
            tags);
    TagMatcher second =
        new TagMatcher(
            holder,
            tagCache,
            repo,
            ImmutableList.of(repo.exactRef("refs/heads/b")),
            seenByBoth,
            tags);
    assertThat(first.toUpdate.keySet()).containsExactly("refs/heads/a");
    assertThat(second.toUpdate.keySet()).containsExactly("refs/heads/b");

    TagSet afterFirst = holder.refreshForTesting(tagCache, repo, first);
    assertThat(afterFirst).isSameInstanceAs(holder.getTagSet());
    TagSet afterSecond = holder.refreshForTesting(tagCache, repo, second);
    assertThat(afterSecond).isSameInstanceAs(holder.getTagSet());

    assertThat(decorations(holder.getTagSet()))
        .containsAtLeast("refs/heads/a", "refs/heads/b", MASTER, "refs/tags/v1");
  }

  @Test
  public void tagOnCommitsAddedByAFastForwardBecomesReachable() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    RevCommit tagged = tr.commit().parent(root).create();
    tr.update("refs/tags/v1", tr.tag("v1", tagged));
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    TagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    TagMatcher m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isFalse();

    tr.update(MASTER, tr.commit().parent(tagged).create());
    m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
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
    Cache<String, TagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    TagCache tagCache = new TagCache(guavaCache);
    Project.NameKey project = Project.nameKey("project");
    Ref sideTag = repo.exactRef("refs/tags/side-tag");
    TagMatcher m =
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

  private static TagSet build(Repository repo) throws Exception {
    TagSet tagSet = new TagSet(Project.nameKey("project"));
    assertThat(tagSet.build(repo, null, null)).isTrue();
    return tagSet;
  }

  private static TagSet refreshCopy(Repository repo, TagSet old, String ref, ObjectId id)
      throws Exception {
    return refreshCopy(repo, old, ImmutableMap.of(ref, id.copy()));
  }

  private static TagSet refreshCopy(Repository repo, TagSet old, Map<String, ObjectId> idByRef)
      throws Exception {
    TagSet updated = new TagSet(old.getProjectName());
    assertThat(updated.build(repo, old, matcherWith(idByRef))).isTrue();
    var unused = updated.removeDeletedRefs(refNames(repo));
    return updated;
  }

  private static TagMatcher matcherWith(Map<String, ObjectId> idByRef) throws Exception {
    TagMatcher m =
        new TagMatcher(
            null,
            null,
            null,
            ImmutableList.of(),
            new TagSet(Project.nameKey("p")),
            ImmutableList.of());
    m.toUpdate.putAll(idByRef);
    return m;
  }

  private static Set<String> refNames(Repository repo) throws Exception {
    Set<String> names = new HashSet<>();
    for (Ref r : repo.getRefDatabase().getRefs()) {
      names.add(r.getName());
    }
    return names;
  }

  private static Set<ObjectId> reachableTags(Repository repo, TagSet tagSet, String ref)
      throws Exception {
    TagMatcher m = matcherWith(ImmutableMap.of());
    Set<ObjectId> tags =
        tagSet.getReachableTags(
            repo,
            m,
            ImmutableList.of(repo.exactRef(ref)),
            repo.getRefDatabase().getRefsByPrefix("refs/tags/"));
    assertWithMessage("out of date: %s", m.toUpdate).that(tags).isNotNull();
    return tags;
  }

  private static ObjectId tagSetRoot(TagSet tagSet) {
    return tagSet.dag.decorated.stream()
        .filter(n -> n.decorations.contains("refs/tags/v0"))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> decorations(TagSet tagSet) {
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

  static void assertEqual(@Nullable TagSet a, @Nullable TagSet b) {
    if (a == null || b == null) {
      assertWithMessage("only one TagSet is null out of\n%s\n%s", a, b)
          .that(a == null && b == null)
          .isTrue();
      return;
    }
    assertThat(a.getProjectName()).isEqualTo(b.getProjectName());
    assertThat(a.dag.decorated).containsExactlyElementsIn(b.dag.decorated);
  }
}
