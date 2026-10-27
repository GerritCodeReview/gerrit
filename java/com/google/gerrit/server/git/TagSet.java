// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.NodeProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.git.DecoratedDag.Node;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A TagSet keeps track of tag reachability from other refs to make reachability lookups fast.
 *
 * <p>Use a cacheable {@link DecoratedDag} to determine reachability on the fly. The DAG is built
 * the first time by calling build(). Confirm that all the interesting refs are in the DAG when
 * checking for reachability and use the TagMatcher to signal that the DAG is out of date. When the
 * DAG is out of date the TagMatcher will call refresh() which copies the Dag to a new {@link
 * TagSet} and then updates the new DAG with the new updates.
 *
 * <p>This set is used to determine which tags should be advertised when only a subset of refs is
 * visible to a user.
 *
 * <p>TagSets can be serialized for use in a persisted TagCache
 */
class TagSet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  static final ImmutableSet<String> SKIPPABLE_REF_PREFIXES =
      ImmutableSet.of(
          RefNames.REFS_CHANGES,
          RefNames.REFS_CACHE_AUTOMERGE,
          RefNames.REFS_DRAFT_COMMENTS,
          RefNames.REFS_STARRED_CHANGES);

  private final Project.NameKey projectName;

  protected final DecoratedDag dag;

  /**
   * Ids that refs point to which are not commits (e.g. tags of trees or blobs). They are not in the
   * DAG; remembering them avoids treating such refs as out of date on every lookup. Concurrent,
   * since lookups add to it on a published TagSet without holding a lock.
   */
  private final Set<ObjectId> nonCommits = ConcurrentHashMap.newKeySet();

  TagSet(Project.NameKey projectName) {
    this(projectName, new DecoratedDag());
  }

  TagSet(Project.NameKey projectName, DecoratedDag dag) {
    this.projectName = projectName;
    this.dag = dag;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  /**
   * Returns the tags reachable from {@code refs}, or {@code null} if the DAG does not know the
   * current value of one of {@code refs} or {@code tags}. Those are then recorded in {@code
   * m.toUpdate} and the caller must refresh the DAG and retry.
   */
  @Nullable
  Set<ObjectId> getReachableTags(
      Repository repo, TagMatcher m, Iterable<Ref> refs, Iterable<Ref> tags) throws IOException {
    List<Node> nodes = new ArrayList<>();
    for (Ref ref : refs) {
      if (!skip(ref)) {
        Node node = getNodeIfUpToDate(repo, m.toUpdate, ref);
        if (node != null) {
          nodes.add(node);
        }
      }
    }

    for (Ref ref : tags) {
      if (!skip(ref)) {
        Node unused = getNodeIfUpToDate(repo, m.toUpdate, ref);
      }
    }

    if (!m.toUpdate.isEmpty()) { // shortcut walking if we need to refresh anyway
      return null;
    }
    return reachableFrom(nodes);
  }

  /** Returns the tags reachable from the refs in {@code refs} that the DAG is up to date for. */
  Set<ObjectId> getReachableTags(Repository repo, Iterable<Ref> refs, Set<String> outOfDate)
      throws IOException {
    List<Node> nodes = new ArrayList<>();
    for (Ref ref : refs) {
      if (skip(ref) || outOfDate.contains(ref.getName())) {
        continue;
      }
      ObjectId id = peeledId(repo, ref);
      Node node = id != null ? getDecorated(id) : null;
      if (node != null && node.decorations.contains(ref.getName())) {
        nodes.add(node);
      }
    }
    return reachableFrom(nodes);
  }

  private Set<ObjectId> reachableFrom(List<Node> nodes) {
    Set<ObjectId> reachable = new HashSet<>();
    for (Node node : dag.walkInMemory(nodes, new DecoratedDag.ReachableTagWalker())) {
      reachable.add(node.copy());
    }
    return reachable;
  }

  /**
   * Returns the node of {@code ref} if the DAG has it with this ref name. Otherwise records the ref
   * in {@code toUpdate} if it points to a commit, and returns {@code null}.
   */
  @Nullable
  private Node getNodeIfUpToDate(Repository repo, Map<String, ObjectId> toUpdate, Ref ref)
      throws IOException {
    ObjectId id = peeledId(repo, ref);
    if (id == null) {
      return null;
    }
    String refName = ref.getName();
    Node node = getDecorated(id);
    if (node != null && node.decorations.contains(refName)) {
      return node;
    }
    if (nonCommits.contains(id)) {
      return null;
    }
    try (RevWalk rw = new RevWalk(repo)) {
      rw.parseCommit(id);
      toUpdate.put(refName, id.copy());
    } catch (IncorrectObjectTypeException notCommit) {
      // Remembered so that refs to non-commits, e.g. tags of blobs, don't cause a refresh on every
      // lookup.
      nonCommits.add(id.copy());
    }
    return null;
  }

  @Nullable
  private static ObjectId peeledId(Repository repo, Ref ref) throws IOException {
    if (isTag(ref)) {
      try {
        ref = repo.getRefDatabase().peel(ref);
      } catch (InterruptedIOException e) {
        throw e;
      } catch (IOException e) {
        // Use the unpeeled id.
      }
      if (ref.getPeeledObjectId() != null) {
        return ref.getPeeledObjectId();
      }
    }
    return ref.getObjectId();
  }

  @Nullable
  protected Node getDecorated(ObjectId id) {
    Node node = dag.get(id);
    if (node == null || node.decorations.isEmpty()) {
      return null;
    }
    return node;
  }

  /**
   * Populates this set from {@code git}: by copying {@code old} and applying the refs in {@code
   * m.toUpdate} if both are given, by walking the full history otherwise.
   *
   * @return {@code false} if the build was aborted because the thread was interrupted, in which
   *     case the tag set is incomplete and must not be cached.
   */
  boolean build(Repository git, @Nullable TagSet old, @Nullable TagMatcher m) throws IOException {
    try {
      if (old != null && m != null) {
        // Updating the {@link DecoratedDag} is not thread safe, copy it to update it.
        copy(old, this);
        refresh(git, m.toUpdate);
        return true;
      }

      try (RevWalk rw = new RevWalk(git)) {
        rw.setRetainBody(false);
        for (Ref ref :
            git.getRefDatabase()
                .getRefsByPrefixWithExclusions(RefDatabase.ALL, SKIPPABLE_REF_PREFIXES)) {
          if (skip(ref)) {
            continue;
          }
          ObjectId id = peeledId(git, ref);
          if (id != null) {
            decorate(rw, id, ref.getName());
          }
        }

        // Traverse the complete history of all refs creating a DAG for all the commits. The
        // RevWalk visits each commit once, however many refs reach it.
        for (Node node : dag.decorated) {
          rw.markStart(rw.parseCommit(node));
        }
        RevCommit c;
        while ((c = rw.next()) != null) {
          dag.updateParents(c);
        }
      } catch (InterruptedIOException e) {
        throw e;
      } catch (IOException e) {
        // A broken object only hides the tags behind it.
        logger.atWarning().withCause(e).log("Error building tags for repository %s", projectName);
      }
      dag.prune();
      dag.clearUndecorated();
      return true;
    } catch (InterruptedIOException e) {
      // The calling thread was interrupted, e.g. because the client disconnected. All further
      // object reads would fail as well, so give up on the whole build.
      logger.atFine().log("Interrupted while building tags for repository %s", projectName);
      return false;
    }
  }

  /**
   * Serializes a {@link TagSet} as a list of its decorated nodes. Parents are stored as indexes
   * into that list, so the nodes must be written and read in the same order.
   */
  enum Serializer implements CacheSerializer<TagSet> {
    INSTANCE;

    @Override
    public byte[] serialize(TagSet tagSet) {
      return Protos.toByteArray(toProto(tagSet));
    }

    @Override
    public TagSet deserialize(byte[] in) {
      return fromProto(Protos.parseUnchecked(TagSetProto.parser(), in));
    }

    static TagSetProto toProto(TagSet tagSet) {
      List<Node> nodes = new ArrayList<>(tagSet.dag.decorated);
      Map<Node, Integer> numberByNode = new HashMap<>(nodes.size());
      for (Node node : nodes) {
        numberByNode.put(node, numberByNode.size());
      }

      ObjectIdConverter idConverter = ObjectIdConverter.create();
      TagSetProto.Builder b = TagSetProto.newBuilder().setProjectName(tagSet.projectName.get());
      for (Node node : nodes) {
        NodeProto.Builder nb =
            NodeProto.newBuilder()
                .setId(idConverter.toByteString(node))
                .addAllDecorations(node.decorations);
        for (Node parent : node.parents) {
          Integer n = numberByNode.get(parent);
          checkState(n != null, "parent %s of %s is not decorated", parent.name(), node.name());
          nb.addParentNumbers(n);
        }
        b.addNodes(nb);
      }
      return b.build();
    }

    static TagSet fromProto(TagSetProto proto) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      List<Node> nodes = new ArrayList<>(proto.getNodesCount());
      DecoratedDag dag = new DecoratedDag();
      for (NodeProto np : proto.getNodesList()) {
        Node node =
            new Node(
                idConverter.fromByteString(np.getId()), new HashSet<>(np.getDecorationsList()));
        nodes.add(node);
        dag.add(node);
        dag.decorated.add(node);
      }

      // All {@link Node}s had to be read before we could numerically reference them as parents
      for (int n = 0; n < nodes.size(); n++) {
        for (int parentNumber : proto.getNodes(n).getParentNumbersList()) {
          nodes.get(n).parents.add(nodes.get(parentNumber));
        }
      }
      return new TagSet(Project.nameKey(proto.getProjectName()), dag);
    }
  }

  /**
   * Applies ref updates to the DAG. Only use on a copy of a {@link TagSet} that is not in use by
   * other threads.
   *
   * <p>Since the {@link DecoratedDag.Node}s are based on {@link ObjectId}s, and not refs, treat all
   * ref updates similarly in that they can only add and remove decorations to commits. In other
   * words, don't see updates from the point of view of the ref, i.e. don't care if the updates are
   * deletes, fast forwards, rewinds...
   *
   * @param idByRef new value of each updated ref
   * @return whether the DAG changed
   */
  @CanIgnoreReturnValue
  boolean refresh(Repository repo, Map<String, ObjectId> idByRef) throws IOException {
    boolean updated = false;
    Map<String, Node> nodeByDecoration = new HashMap<>(dag.decorated.size());
    for (Node node : dag.decorated) {
      for (String ref : node.decorations) {
        nodeByDecoration.put(ref, node);
      }
    }

    Set<Node> undecorated = new HashSet<>();
    Set<Node> inserted = new HashSet<>();
    // Built on the first insert, so that each insert does not scan the whole DAG for children.
    Map<Node, Set<Node>> children = null;
    try (RevWalk rw = new RevWalk(repo)) {
      rw.setRetainBody(false);
      for (Map.Entry<String, ObjectId> e : idByRef.entrySet()) {
        ObjectId id = e.getValue();
        String refName = e.getKey();
        Node node = getDecorated(id);
        if (node != null) {
          if (node.decorations.add(refName)) {
            updated = true; // a new decoration was added to an already decorated {@link Node}
          }
        } else {
          try {
            if (children == null) {
              children = dag.getChildren();
            }
            node = insertDecorated(rw, id, refName, children); // a previously undecorated commit
            inserted.add(node);
            updated = true;
          } catch (IncorrectObjectTypeException notCommit) {
            nonCommits.add(id.copy());
          } catch (InterruptedIOException ex) {
            throw ex;
          } catch (IOException ex) {
            logger.atWarning().withCause(ex).log(
                "Error refreshing tags for repository %s", projectName);
          }
        }

        Node old = nodeByDecoration.get(refName);
        if (old != null && !old.equals(node)) {
          old.decorations.remove(refName);
          undecorated.add(old);
          updated = true;
        }
        if (node != null) {
          nodeByDecoration.put(refName, node);
        } else {
          nodeByDecoration.remove(refName);
        }
      }
    }

    // Inserting may have added undecorated commits between the inserted nodes and their nearest
    // decorated ancestors. Only those inserted nodes need their parents pruned. This has to happen
    // before undecorated nodes are dropped, which also drops these intermediate commits.
    dag.pruneParents(inserted);
    // A ref update may leave the commit the ref pointed to before without decorations. Drop it, so
    // that the DAG does not grow with every update, and link its children to its parents.
    dag.removeUndecorated(undecorated);
    dag.clearUndecorated();
    return updated;
  }

  /**
   * Removes the decorations of refs that no longer exist. Only use on a copy of a {@link TagSet}
   * that is not in use by other threads.
   *
   * @return whether the DAG changed
   */
  @CanIgnoreReturnValue
  boolean removeDeletedRefs(Set<String> existingRefs) {
    Set<Node> undecorated = new HashSet<>();
    for (Node node : dag.decorated) {
      if (node.decorations.removeIf(
          r -> !r.equals(DecoratedDag.IC_DECORATION) && !existingRefs.contains(r))) {
        undecorated.add(node);
      }
    }
    dag.removeUndecorated(undecorated);
    return !undecorated.isEmpty();
  }

  /** Copies the decorated nodes of {@code src} into the empty {@code dst}. */
  private static void copy(TagSet src, TagSet dst) {
    for (Node oldNode : src.dag.decorated) {
      Node newNode = new Node(oldNode, new HashSet<>(oldNode.decorations));
      newNode.parents = new HashSet<>(oldNode.parents.size());

      dst.dag.add(newNode);
      dst.dag.decorated.add(newNode);
    }

    for (Node oldNode : src.dag.decorated) {
      Node newNode = dst.dag.get(oldNode);
      for (Node parent : oldNode.parents) {
        newNode.parents.add(dst.dag.get(parent));
      }
    }
    dst.nonCommits.addAll(src.nonCommits);
  }

  /** If a commit, decorate {@link Node} while building {@link DecoratedDag} */
  private void decorate(RevWalk rw, ObjectId id, String name) throws InterruptedIOException {
    try {
      rw.parseCommit(id);
      dag.decorate(id, name);
    } catch (IncorrectObjectTypeException notCommit) {
      // No need to spam the logs.
      // Quite many refs will point to non-commits.
      // For instance, refs from refs/cache-automerge
      // will often end up here.
      nonCommits.add(id.copy());
    } catch (InterruptedIOException e) {
      throw e;
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Error on %s of %s", name, projectName);
    }
  }

  static boolean skip(Ref ref) {
    return ref.isSymbolic()
        || ref.getObjectId() == null
        || SKIPPABLE_REF_PREFIXES.stream().anyMatch(p -> ref.getName().startsWith(p));
  }

  private static boolean isTag(Ref ref) {
    return isTag(ref.getName());
  }

  private static boolean isTag(String refName) {
    return refName.startsWith(Constants.R_TAGS);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("projectName", projectName)
        .add("decorated", dag.decorated)
        .toString();
  }

  /**
   * Insert a new decorated {@link ObjectId} to an already built {@link DecoratedDag}.
   *
   * <p>before: A <- B after: A <- commitToInsert <- B
   *
   * @param children children by parent, for the nodes of {@code dag.decorated}. May list children
   *     that no longer have that parent, but must list every child that has; this method adds the
   *     edges it creates.
   */
  private Node insertDecorated(
      RevWalk rw, ObjectId id, String refName, Map<Node, Set<Node>> children) throws IOException {
    RevCommit commitToInsert = rw.parseCommit(id);
    Node nodeToInsert = dag.getOrCreate(id);

    // Find "A"(s) pointed to by commitToInsert
    DecoratedDag.UpdateUndecoratedParents walker = new DecoratedDag.UpdateUndecoratedParents(rw);
    Set<Node> decoratedAncestors = dag.walk(Collections.singleton(nodeToInsert), walker);
    dag.decorate(id, refName); // Has to be done after walk to prevent short stop
    nodeToInsert.parents = new HashSet<>(decoratedAncestors);
    for (Node node : walker.reparented) {
      addChild(children, node);
    }
    addChild(children, nodeToInsert);

    // Find "B"(s):  A <- B, and make the ones that contain commitToInsert point to it instead.
    // RevWalk.isMergedInto() resets the walk, so a single RevWalk can be reused for each child.
    Map<Node, Boolean> containsInserted = new HashMap<>();
    for (Node ancestor : decoratedAncestors) {
      for (Node child : List.copyOf(children.getOrDefault(ancestor, Set.of()))) {
        if (child.equals(nodeToInsert)
            || !child.parents.contains(ancestor)
            || !dag.decorated.contains(child)) {
          continue;
        }
        Boolean contains = containsInserted.get(child);
        if (contains == null) {
          contains = rw.isMergedInto(commitToInsert, rw.parseCommit(child));
          containsInserted.put(child, contains);
        }
        if (contains) {
          // <- B
          child.parents.remove(ancestor);
          // commitToInsert <- B
          child.parents.add(nodeToInsert);
          children.computeIfAbsent(nodeToInsert, p -> new HashSet<>()).add(child);
        }
      }
    }

    return nodeToInsert;
  }

  private static void addChild(Map<Node, Set<Node>> children, Node child) {
    for (Node parent : child.parents) {
      children.computeIfAbsent(parent, p -> new HashSet<>()).add(child);
    }
  }
}
