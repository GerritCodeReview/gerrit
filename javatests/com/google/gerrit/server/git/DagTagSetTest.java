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
                new TypeLiteral<Set<ObjectId>>() {}.getType(),
                "incomplete",
                boolean.class));
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

    DagTagSet tagSet = build(repo);

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
    DagTagSet tagSet = build(repo);
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
    DagTagSet tagSet = build(repo);
    assertThat(tagSet.dag.get(top).parents).containsExactly(tagSet.dag.get(mid));

    // Deleting refs/heads/mid leaves the mid node undecorated; master must still reach v1.
    tr.delete("refs/heads/mid");
    DagTagSet updated = new DagTagSet(tagSet.getProjectName());
    assertThat(updated.build(repo, tagSet, ImmutableMap.of())).isTrue();
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
    DagTagSet tagSet = build(repo);

    RevCommit tagged;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tagged = tr.commit().parent(tip).create();
      tr.update("refs/tags/v1", tr.tag("v1", tagged));
      tr.update(MASTER, tr.commit().parent(tagged).create());
    }

    reads.set(0);
    DagTagSet updated =
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
      DagTagSet updated = build(repo);
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

  /**
   * One refresh fast-forwards master from x to tip and adds a tag on n, between them. Applying
   * master first leaves x without refs while n is inserted; tip must still be linked to n, in
   * either order.
   */
  @Test
  public void tagBetweenTheOldAndNewTipOfAMovedRefIsReachableInEitherOrder() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v0", tr.tag("v0", root));
    RevCommit x = tr.commit().parent(root).create();
    RevCommit n = tr.commit().parent(x).create();
    RevCommit tip = tr.commit().parent(n).create();
    for (ImmutableMap<String, ObjectId> idByRef :
        ImmutableList.of(
            ImmutableMap.<String, ObjectId>of(MASTER, tip.copy(), "refs/tags/t", n.copy()),
            ImmutableMap.<String, ObjectId>of("refs/tags/t", n.copy(), MASTER, tip.copy()))) {
      tr.delete("refs/tags/t");
      tr.update(MASTER, x);
      DagTagSet updated = build(repo);
      tr.update(MASTER, tip);
      tr.update("refs/tags/t", tr.tag("t", n));
      updated.refresh(repo, idByRef);
      assertWithMessage("order %s", idByRef.keySet())
          .that(reachableTags(repo, updated, MASTER))
          .containsExactly(root.copy(), n.copy());
      assertThat(updated.dag.get(tip).parents).containsExactly(updated.dag.get(n));
    }
  }

  /**
   * Master is a merge of x and y, both children of a. A tag on x must not take the place of a as a
   * parent of master, since master also reaches a through y: a tag on y, added by a later or the
   * same refresh, must still be reachable from master.
   */
  @Test
  public void tagsOnBothSidesOfAMergeAreReachable() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit a = tr.commit().create();
    tr.update("refs/tags/v0", tr.tag("v0", a));
    RevCommit x = tr.commit().parent(a).create();
    RevCommit y = tr.commit().parent(a).create();
    RevCommit merge = tr.commit().parent(x).parent(y).create();
    tr.update(MASTER, merge);
    DagTagSet tagSet = build(repo);

    tr.update("refs/tags/x", tr.tag("x", x));
    DagTagSet afterX = refreshCopy(repo, tagSet, "refs/tags/x", x);
    assertThat(afterX.dag.get(merge).parents).containsExactly(afterX.dag.get(x), afterX.dag.get(a));
    tr.update("refs/tags/y", tr.tag("y", y));
    DagTagSet afterY = refreshCopy(repo, afterX, "refs/tags/y", y);
    assertThat(reachableTags(repo, afterY, MASTER)).containsExactly(a.copy(), x.copy(), y.copy());
    assertThat(afterY.dag.get(merge).parents).containsExactly(afterY.dag.get(x), afterY.dag.get(y));

    for (ImmutableMap<String, ObjectId> idByRef :
        ImmutableList.of(
            ImmutableMap.<String, ObjectId>of("refs/tags/x", x.copy(), "refs/tags/y", y.copy()),
            ImmutableMap.<String, ObjectId>of("refs/tags/y", y.copy(), "refs/tags/x", x.copy()))) {
      DagTagSet updated = refreshCopy(repo, tagSet, idByRef);
      assertWithMessage("order %s", idByRef.keySet())
          .that(reachableTags(repo, updated, MASTER))
          .containsExactly(a.copy(), x.copy(), y.copy());
    }
  }

  /**
   * After a ref was moved away from n, n has no refs left until the refresh ends. A later insert in
   * the same refresh that walks through n must keep n's links to decorated nodes, so that the DAG
   * can be serialized.
   */
  @Test
  public void insertWalkingThroughANodeThatLostItsRefsKeepsTheDagClosed() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update("refs/tags/v0", tr.tag("v0", root));
    RevCommit between = tr.commit().parent(root).create();
    RevCommit n = tr.commit().parent(between).create();
    tr.update("refs/heads/side", tr.commit().parent(n).create());
    tr.update(MASTER, n);
    RevCommit above = tr.commit().parent(n).create();
    DagTagSet tagSet = build(repo);

    tr.update(MASTER, tr.commit().parent(root).create());
    tr.update("refs/tags/t", tr.tag("t", above));
    // refresh() applies the refs in the order of the (ordered) map: master first.
    DagTagSet updated = tagSet;
    updated.refresh(
        repo,
        ImmutableMap.of(MASTER, repo.exactRef(MASTER).getObjectId(), "refs/tags/t", above.copy()));

    for (Node node : updated.dag.decorated) {
      assertWithMessage("parents of %s", node)
          .that(updated.dag.decorated)
          .containsAtLeastElementsIn(node.parents);
    }
    var unused = DagTagSet.Serializer.toProto(updated);
    assertThat(reachableTags(repo, updated, "refs/heads/side")).containsExactly(root.copy());
  }

  /**
   * A new branch with unrelated history must be linked to the root commit of that history, so that
   * tags added there later, by the same or a later refresh, are reachable from it.
   */
  @Test
  public void tagsInTheHistoryOfANewOrphanBranchAreReachable() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    DagTagSet tagSet = build(repo);
    RevCommit orphanRoot = tr.commit().create();
    RevCommit middle = tr.commit().parent(orphanRoot).create();
    RevCommit tip = tr.commit().parent(middle).create();
    String orphan = "refs/heads/orphan";
    tr.update(orphan, tip);

    DagTagSet withBranch = refreshCopy(repo, tagSet, orphan, tip);
    assertThat(withBranch.dag.get(tip).parents).containsExactly(withBranch.dag.get(orphanRoot));
    tr.update("refs/tags/root", tr.tag("root", orphanRoot));
    tr.update("refs/tags/middle", tr.tag("middle", middle));
    DagTagSet withTags =
        refreshCopy(
            repo,
            withBranch,
            ImmutableMap.of(
                "refs/tags/root", orphanRoot.copy(), "refs/tags/middle", middle.copy()));
    assertThat(reachableTags(repo, withTags, orphan))
        .containsExactly(orphanRoot.copy(), middle.copy());

    for (ImmutableMap<String, ObjectId> idByRef :
        ImmutableList.of(
            ImmutableMap.<String, ObjectId>of(
                orphan, tip.copy(), "refs/tags/middle", middle.copy()),
            ImmutableMap.<String, ObjectId>of(
                "refs/tags/middle", middle.copy(), orphan, tip.copy()))) {
      tr.delete("refs/tags/root");
      tr.delete("refs/tags/middle");
      tr.delete(orphan);
      DagTagSet updated = build(repo);
      tr.update(orphan, tip);
      tr.update("refs/tags/middle", tr.tag("middle", middle));
      updated.refresh(repo, idByRef);
      assertWithMessage("order %s", idByRef.keySet())
          .that(reachableTags(repo, updated, orphan))
          .containsExactly(middle.copy());
    }
  }

  @Test
  public void refToANewRootCommitIsNotItsOwnParent() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    tr.update(MASTER, tr.commit().create());
    DagTagSet tagSet = build(repo);
    RevCommit orphanRoot = tr.commit().create();
    tr.update("refs/tags/root", tr.tag("root", orphanRoot));

    DagTagSet updated = refreshCopy(repo, tagSet, "refs/tags/root", orphanRoot);
    assertThat(updated.dag.get(orphanRoot).parents).isEmpty();
    assertThat(updated.dag.get(orphanRoot).decorations).contains("refs/tags/root");
    var unused = DagTagSet.Serializer.toProto(updated);
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
    DagTagSet tagSet = build(repo);

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
    assertEqual(tagSet, DagTagSet.Serializer.fromProto(DagTagSet.Serializer.toProto(tagSet)));
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
    DagTagSet tagSet = build(repo);

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
      assertThat(new DagTagSet(Project.nameKey("project")).build(repo, null, null)).isFalse();
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

  /**
   * Two lookups see the same DAG and find different refs out of date. The first refresh must update
   * both, and the second one then has nothing left to do.
   */
  @Test
  public void concurrentLookupsApplyTheirUpdatesOnTopOfEachOther() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    tr.update("refs/tags/v1", tr.tag("v1", root));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    DagTagSet seenByBoth = holder.getTagSet();

    tr.update("refs/heads/a", tr.commit().parent(root).create());
    tr.update("refs/heads/b", tr.commit().parent(root).create());
    DagTagMatcher first =
        new DagTagMatcher(
            holder,
            tagCache,
            repo,
            ImmutableList.of(repo.exactRef("refs/heads/a")),
            seenByBoth,
            tags);
    DagTagMatcher second =
        new DagTagMatcher(
            holder,
            tagCache,
            repo,
            ImmutableList.of(repo.exactRef("refs/heads/b")),
            seenByBoth,
            tags);
    assertThat(first.toUpdate.keySet()).containsExactly("refs/heads/a");
    assertThat(second.toUpdate.keySet()).containsExactly("refs/heads/b");

    DagTagSet afterFirst = holder.refreshForTesting(tagCache, repo);
    assertThat(afterFirst).isSameInstanceAs(holder.getTagSet());
    DagTagSet afterSecond = holder.refreshForTesting(tagCache, repo);
    assertThat(afterSecond).isSameInstanceAs(afterFirst);

    assertThat(decorations(holder.getTagSet()))
        .containsAtLeast("refs/heads/a", "refs/heads/b", MASTER, "refs/tags/v1");
  }

  /**
   * Two lookups see the same DAG and find the same ref out of date. The second one refreshes after
   * the first published a DAG that already has the ref, and must reuse it instead of copying it.
   */
  @Test
  public void lookupWhoseUpdatesWereAppliedMeanwhileReusesThePublishedDag() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    tr.update("refs/tags/v1", tr.tag("v1", root));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    DagTagSet seenByBoth = holder.getTagSet();

    tr.update(MASTER, tr.commit().parent(root).create());
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef(MASTER));
    DagTagMatcher second = new DagTagMatcher(holder, tagCache, repo, include, seenByBoth, tags);
    assertThat(second.toUpdate.keySet()).containsExactly(MASTER);

    DagTagSet afterFirst = holder.refreshForTesting(tagCache, repo);
    assertThat(afterFirst).isNotSameInstanceAs(seenByBoth);
    assertThat(holder.refreshForTesting(tagCache, repo)).isSameInstanceAs(afterFirst);
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
   * A commit in the history could not be read while the DAG was built, which hides the tag behind
   * it. Once the commit can be read again, the next refresh must rebuild the DAG instead of keeping
   * the missing links for good.
   */
  @Test
  public void dagBuiltWithAReadErrorIsRebuiltByTheNextRefresh() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update("refs/tags/v1", tr.tag("v1", root));
      RevCommit mid = tr.commit().parent(root).create();
      tr.update(MASTER, tr.commit().parent(mid).create());
      missing.set(mid.copy());
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    assertThat(
            holder
                .matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags)
                .isReachable(tagRef))
        .isFalse();
    // Also after the persisted cache was read back, e.g. after a restart.
    assertThat(
            DagTagSet.Serializer.fromProto(DagTagSet.Serializer.toProto(holder.getTagSet()))
                .isIncomplete())
        .isTrue();

    missing.set(null);
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update("refs/heads/other", root);
    }
    // A lookup that refreshes the DAG for the new branch.
    var unused =
        holder.matcher(
            tagCache,
            repo,
            ImmutableList.of(repo.exactRef(MASTER), repo.exactRef("refs/heads/other")),
            tags);
    DagTagMatcher m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
  }

  /**
   * A refresh moves x, the only ref on a, away and inserts y, a child of a. The insert walks
   * through a and fails below it. a's other child k must not be left without ancestors: the DAG has
   * to be marked incomplete so that the next refresh rebuilds it.
   */
  @Test
  public void readErrorBelowANodeThatLostItsRefsMarksTheDagIncomplete() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    RevCommit root;
    RevCommit below;
    RevCommit a;
    RevCommit y;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update("refs/tags/v0", tr.tag("v0", root));
      below = tr.commit().parent(root).create();
      a = tr.commit().parent(below).create();
      tr.update("refs/heads/x", a);
      tr.update("refs/heads/k", tr.commit().parent(a).create());
    }
    DagTagSet tagSet = build(repo);
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update("refs/heads/x", root);
      y = tr.commit().parent(a).create();
      tr.update("refs/heads/y", y);
    }

    missing.set(below.copy());
    // refresh() applies the refs in the order of the (ordered) map: x first.
    tagSet.refresh(repo, ImmutableMap.of("refs/heads/x", root.copy(), "refs/heads/y", y.copy()));
    assertThat(tagSet.isIncomplete()).isTrue();
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
   * A deleted ref's commit was removed by a gc. A refresh that inserts a tag below it must not read
   * that commit, which would mark the DAG incomplete.
   */
  @Test
  public void refreshDoesNotReadTheCommitsOfDeletedRefs() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    RevCommit root;
    RevCommit x;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update(MASTER, root);
      tr.update("refs/tags/v0", tr.tag("v0", root));
      x = tr.commit().parent(root).create();
      tr.update("refs/heads/deleted", tr.commit().parent(x).create());
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> include = ImmutableList.of(repo.exactRef(MASTER));
    var unused =
        holder.matcher(tagCache, repo, include, ImmutableList.of(repo.exactRef("refs/tags/v0")));

    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      missing.set(repo.exactRef("refs/heads/deleted").getObjectId().copy());
      tr.delete("refs/heads/deleted");
      tr.update("refs/tags/t", tr.tag("t", x));
    }
    var unused2 =
        holder.matcher(tagCache, repo, include, ImmutableList.of(repo.exactRef("refs/tags/t")));
    assertThat(holder.getTagSet().isIncomplete()).isFalse();
  }

  /**
   * master was moved away from a commit that a gc then removed. The refresh inserting master's new
   * commit must not read the old one, which would mark the DAG incomplete.
   */
  @Test
  public void refreshDoesNotReadTheOldCommitsOfMovedRefs() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    RevCommit root;
    RevCommit old;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update("refs/tags/v0", tr.tag("v0", root));
      old = tr.commit().parent(root).create();
      tr.update(MASTER, old);
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v0");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);

    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update(MASTER, tr.commit().message("new").parent(root).create());
      missing.set(old.copy());
    }
    DagTagMatcher m = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(holder.getTagSet().isIncomplete()).isFalse();
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
   * A commit in the history cannot be read for good, so every build leaves the DAG incomplete. A
   * refresh must not rebuild it again as long as no ref changed.
   */
  @Test
  public void incompleteDagIsNotRebuiltWhenNoRefChanged() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    InMemoryRepository repo = missingObjectRepo(missing);
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      RevCommit root = tr.commit().create();
      tr.update("refs/tags/v1", tr.tag("v1", root));
      RevCommit mid = tr.commit().parent(root).create();
      tr.update(MASTER, tr.commit().parent(mid).create());
      missing.set(mid.copy());
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    DagTagSet incomplete = holder.getTagSet();
    assertThat(incomplete.isIncomplete()).isTrue();

    assertThat(holder.refreshForTesting(tagCache, repo)).isSameInstanceAs(incomplete);
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

  /**
   * A commit in the history cannot be read for good. After one full rebuild that came back
   * incomplete again, refs that change are applied as updates instead of further full rebuilds,
   * which would read the whole history under the lock on every push.
   */
  @Test
  public void dagThatStaysIncompleteAfterARebuildIsUpdatedIncrementally() throws Exception {
    AtomicReference<ObjectId> missing = new AtomicReference<>();
    AtomicInteger missingReads = new AtomicInteger();
    InMemoryRepository repo = missingObjectRepo(missing, missingReads);
    RevCommit root;
    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      root = tr.commit().create();
      tr.update("refs/tags/v1", tr.tag("v1", root));
      RevCommit mid = tr.commit().parent(root).create();
      tr.update(MASTER, tr.commit().parent(mid).create());
      missing.set(mid.copy());
    }
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    Ref tagRef = repo.exactRef("refs/tags/v1");
    ImmutableList<Ref> tags = ImmutableList.of(tagRef);
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);
    assertThat(holder.getTagSet().isIncomplete()).isTrue();

    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update("refs/heads/a", tr.commit().parent(root).create());
    }
    unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef("refs/heads/a")), tags);
    assertThat(holder.getTagSet().isIncomplete()).isTrue();

    try (TestRepository<InMemoryRepository> tr = new TestRepository<>(repo)) {
      tr.update("refs/heads/b", tr.commit().message("b").parent(root).create());
    }
    missingReads.set(0);
    DagTagMatcher m =
        holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef("refs/heads/b")), tags);
    assertThat(m.isReachable(tagRef)).isTrue();
    assertThat(missingReads.get()).isEqualTo(0);
    assertThat(holder.getTagSet().isIncomplete()).isTrue();
  }

  /**
   * A lookup found master out of date and waited for the lock while another lookup published master
   * at a newer commit. The waiting lookup must not move master back.
   */
  @Test
  public void refreshDoesNotMoveARefBackToTheValueALookupSawBeforeWaiting() throws Exception {
    TestRepository<Repo> tr = newRepo();
    Repository repo = tr.getRepository();
    RevCommit root = tr.commit().create();
    tr.update(MASTER, root);
    tr.update("refs/tags/v1", tr.tag("v1", root));
    Cache<String, DagTagSetHolder> guavaCache = CacheBuilder.newBuilder().build();
    DagTagCache tagCache = new DagTagCache(guavaCache);
    DagTagSetHolder holder = tagCache.get(Project.nameKey("project"));
    ImmutableList<Ref> tags = ImmutableList.of(repo.exactRef("refs/tags/v1"));
    var unused = holder.matcher(tagCache, repo, ImmutableList.of(repo.exactRef(MASTER)), tags);

    // The waiting lookup saw master at y; master is at z when it gets the lock.
    RevCommit y = tr.commit().parent(root).create();
    tr.update(MASTER, y);
    RevCommit z = tr.commit().parent(y).create();
    tr.update(MASTER, z);
    var unused2 = holder.refreshForTesting(tagCache, repo);
    DagTagSet afterStale = holder.refreshForTesting(tagCache, repo);

    assertThat(afterStale.dag.get(z).decorations).contains(MASTER);
    assertThat(afterStale.dag.get(y)).isNull();
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
    assertThat(tagSet.build(repo, null, null)).isTrue();
    return tagSet;
  }

  private static DagTagSet refreshCopy(Repository repo, DagTagSet old, String ref, ObjectId id)
      throws Exception {
    return refreshCopy(repo, old, ImmutableMap.of(ref, id.copy()));
  }

  private static DagTagSet refreshCopy(
      Repository repo, DagTagSet old, Map<String, ObjectId> idByRef) throws Exception {
    DagTagSet updated = new DagTagSet(old.getProjectName());
    assertThat(updated.build(repo, old, idByRef)).isTrue();
    var unused = updated.removeDeletedRefs(refNames(repo));
    return updated;
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

  private static Set<String> refNames(Repository repo) throws Exception {
    Set<String> names = new HashSet<>();
    for (Ref r : repo.getRefDatabase().getRefs()) {
      names.add(r.getName());
    }
    return names;
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

  private static ObjectId tagSetRoot(DagTagSet tagSet) {
    return tagSet.dag.decorated.stream()
        .filter(n -> n.decorations.contains("refs/tags/v0"))
        .findFirst()
        .orElseThrow();
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
