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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.DagTagSetHolderProto.DagTagSetProto;
import com.google.gerrit.server.cache.proto.Cache.DagTagSetHolderProto.DagTagSetProto.NodeProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.git.DecoratedDag.Node;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * A DagTagSet keeps track of tag reachability from other refs to make reachability lookups fast.
 *
 * <p>Use a cacheable {@link DecoratedDag} to determine reachability on the fly. The DAG is built by
 * calling build(). Confirm that all the interesting refs are in the DAG when checking for
 * reachability and use the DagTagMatcher to signal that the DAG is out of date. When the DAG is out
 * of date the DagTagSetHolder builds a new one.
 *
 * <p>This set is used to determine which tags should be advertised when only a subset of refs is
 * visible to a user.
 *
 * <p>TagSets can be serialized for use in a persisted DagTagCache
 */
class DagTagSet {
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
   * since lookups add to it on a published DagTagSet without holding a lock.
   */
  private final Set<ObjectId> nonCommits = ConcurrentHashMap.newKeySet();

  /**
   * Ids of refs whose commits the build of this TagSet could not read. They are treated as reaching
   * no tag instead of rebuilding the DAG on every lookup, and are tried again by the next build.
   * Only written before the TagSet is published.
   */
  private final Set<ObjectId> unreadable = new HashSet<>();

  DagTagSet(Project.NameKey projectName) {
    this(projectName, new DecoratedDag());
  }

  DagTagSet(Project.NameKey projectName, DecoratedDag dag) {
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
  Set<? extends ObjectId> getReachableTags(
      Repository repo, DagTagMatcher m, Iterable<Ref> refs, Iterable<Ref> tags) throws IOException {
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

  /**
   * Returns the tags reachable from {@code refs} at the ids they have there, also for refs the DAG
   * is not up to date for: their commits are walked down to the nearest commits the DAG has,
   * without changing the DAG. Refs whose commits cannot be read reach no tag.
   */
  Set<? extends ObjectId> getReachableTagsReadingCommits(Repository repo, Iterable<Ref> refs)
      throws IOException {
    List<Node> nodes = new ArrayList<>();
    try (RevWalk rw = new RevWalk(repo)) {
      rw.setRetainBody(false);
      for (Ref ref : refs) {
        ObjectId id = skip(ref) ? null : peeledId(repo, ref);
        if (id == null) {
          continue;
        }
        Node node = getDecorated(id);
        if (node != null) {
          nodes.add(node);
          continue;
        }
        try {
          nodes.addAll(nearestDecoratedCommits(rw, id));
        } catch (IncorrectObjectTypeException notCommit) {
          // Reaches no tag.
        } catch (InterruptedIOException e) {
          throw e;
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("Error on %s of %s", ref.getName(), projectName);
        }
      }
    }
    return reachableFrom(nodes);
  }

  /** Walks the commits from {@code id} down to the nearest ones the DAG has, and returns those. */
  private Set<Node> nearestDecoratedCommits(RevWalk rw, ObjectId id) throws IOException {
    Set<Node> nearest = new HashSet<>();
    Set<ObjectId> seen = new HashSet<>();
    Deque<RevCommit> todo = new ArrayDeque<>();
    todo.push(rw.parseCommit(id));
    while (!todo.isEmpty()) {
      RevCommit c = todo.pop();
      if (!seen.add(c)) {
        continue;
      }
      Node node = getDecorated(c);
      if (node != null) {
        nearest.add(node);
        continue;
      }
      for (RevCommit parent : c.getParents()) {
        todo.push(rw.parseCommit(parent));
      }
    }
    return nearest;
  }

  private Set<Node> reachableFrom(List<Node> nodes) {
    // The nodes are the ids; the published DAG does not change, so they need no copy.
    return dag.walkInMemory(nodes, new DecoratedDag.ReachableTagWalker());
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
    if (nonCommits.contains(id) || unreadable.contains(id)) {
      return null;
    }
    try (RevWalk rw = new RevWalk(repo)) {
      // Peels annotated tags that refs outside refs/tags/ point to, which peeledId() leaves.
      RevCommit commit = rw.parseCommit(id);
      Node peeled = getDecorated(commit);
      if (peeled != null && peeled.decorations.contains(refName)) {
        return peeled;
      }
      toUpdate.put(refName, commit.copy());
    } catch (IncorrectObjectTypeException notCommit) {
      // Remembered so that refs to non-commits, e.g. tags of blobs, don't cause a refresh on every
      // lookup.
      nonCommits.add(id.copy());
    } catch (InterruptedIOException e) {
      throw e;
    } catch (IOException e) {
      // The next build tries it once, logs the error and records the id in unreadable.
      toUpdate.put(refName, id.copy());
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

  /**
   * Returns the current id of each of {@code refs} that the DAG is out of date for, as {@link
   * #getReachableTags(Repository, DagTagMatcher, Iterable, Iterable)} finds them.
   */
  Map<String, ObjectId> outOfDate(Repository repo, Iterable<Ref> refs) throws IOException {
    Map<String, ObjectId> toUpdate = new HashMap<>();
    for (Ref ref : refs) {
      if (!skip(ref)) {
        Node unused = getNodeIfUpToDate(repo, toUpdate, ref);
      }
    }
    return toUpdate;
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
   * Populates this set from {@code git} by walking the full history.
   *
   * @return {@code false} if the build was aborted because the thread was interrupted, in which
   *     case the tag set is incomplete and must not be cached.
   */
  boolean build(Repository git) throws IOException {
    try {
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
   * Serializes a {@link DagTagSet} as a list of its decorated nodes. Parents are stored as indexes
   * into that list, so the nodes must be written and read in the same order.
   */
  enum Serializer implements CacheSerializer<DagTagSet> {
    INSTANCE;

    @Override
    public byte[] serialize(DagTagSet tagSet) {
      return Protos.toByteArray(toProto(tagSet));
    }

    @Override
    public DagTagSet deserialize(byte[] in) {
      return fromProto(Protos.parseUnchecked(DagTagSetProto.parser(), in));
    }

    static DagTagSetProto toProto(DagTagSet tagSet) {
      List<Node> nodes = new ArrayList<>(tagSet.dag.decorated);
      Map<Node, Integer> numberByNode = new HashMap<>(nodes.size());
      for (Node node : nodes) {
        numberByNode.put(node, numberByNode.size());
      }

      ObjectIdConverter idConverter = ObjectIdConverter.create();
      DagTagSetProto.Builder b =
          DagTagSetProto.newBuilder().setProjectName(tagSet.projectName.get());
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

    static DagTagSet fromProto(DagTagSetProto proto) {
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
      return new DagTagSet(Project.nameKey(proto.getProjectName()), dag);
    }
  }

  /** Copies the decorated nodes of {@code src} into the empty {@code dst}. */
  /** If a commit, decorate {@link Node} while building {@link DecoratedDag} */
  private void decorate(RevWalk rw, ObjectId id, String name) throws InterruptedIOException {
    try {
      // The commit, also for refs outside refs/tags/ that point to annotated tags.
      dag.decorate(rw.parseCommit(id), name);
    } catch (IncorrectObjectTypeException notCommit) {
      // No need to spam the logs.
      // Quite many refs will point to non-commits.
      // For instance, refs from refs/cache-automerge
      // will often end up here.
      nonCommits.add(id.copy());
    } catch (InterruptedIOException e) {
      throw e;
    } catch (IOException e) {
      unreadable.add(id.copy());
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
}
