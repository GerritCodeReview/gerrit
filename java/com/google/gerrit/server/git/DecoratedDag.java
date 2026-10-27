// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A DAG which focuses on tracking decorated git commits.
 *
 * <p>Using a {@link RevWalk} to determine reachability on the fly is very expensive since commits
 * are compressed and spread out on disk either in loose objects or pack files in which they may
 * also be deltafied. This simplified DAG contains less info and is easier to serialize than the
 * {@link RevCommit} DAG. This reduced DAG is decoration (ref) aware and much faster to walk than a
 * {@link RevWalk}.
 *
 * <p>Once built, the DAG only holds decorated nodes (commits pointed to by a ref, and initial
 * commits), each linked to its nearest decorated ancestors. Undecorated commits only exist
 * temporarily while the DAG is built, and are removed by {@link #prune()} and {@link
 * #clearUndecorated()}.
 */
class DecoratedDag extends ObjectIdOwnerMap<DecoratedDag.Node> {
  /** A {@code node} which tracks ref decorations, representing a git commit */
  public static class Node extends ObjectIdOwnerMap.Entry {
    public final Set<String> decorations;
    public Set<Node> parents = new HashSet<>(1);

    public Node(ObjectId id) {
      this(id, new HashSet<String>(1));
    }

    public Node(ObjectId id, Set<String> decorations) {
      super(id);
      this.decorations = decorations;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("ObjectIdOwnerMap.Entry", super.toString())
          .add("decorations", decorations)
          .add("parents", parents)
          .toString();
    }
  }

  /** Base class used to walk a {@link DecoratedDag} */
  public static class ParentWalker<V> {
    protected V result; // Use to store a value computing while walking

    /** Returns whether the walk should continue with the parents of {@code node}. */
    public boolean shouldWalkParents(DecoratedDag dag, Node node) throws IOException {
      return true;
    }

    public V getResult() {
      return result;
    }
  }

  public static class ParentWalkerNodeSet extends ParentWalker<Set<Node>> {
    public ParentWalkerNodeSet() {
      result = new HashSet<>();
    }
  }

  public static class ReachableTagWalker extends ParentWalkerNodeSet {
    @Override
    public boolean shouldWalkParents(DecoratedDag dag, Node node) {
      for (String refName : node.decorations) {
        if (interesting(refName)) {
          result.add(node);
          break;
        }
      }
      return true;
    }

    public boolean interesting(String refName) {
      return !DecoratedDag.IC_DECORATION.equals(refName) && refName.startsWith(Constants.R_TAGS);
    }
  }

  // The {@link DecoratedDag} includes initial commits (ICs) even if they aren't decorated so that
  // we have a complete "closed" set of DAGs. That means a {@link DecoratedDag} may actually contain
  // multiple isolated DAGs. Since we include initial commits in {@link DecoratedDag}s, the only
  // orphans are initial commits that are decorated.
  public static final String IC_DECORATION =
      ""; // Fake decoration to keep ICs in {@link DecoratedDag}.

  protected Set<Node> decorated = new HashSet<>();

  public void updateParents(RevCommit c) {
    Node node = getOrCreate(c);
    node.parents.clear();
    if (c.getParentCount() == 0) {
      setInitialCommit(node);
    } else {
      for (int pIdx = 0; pIdx < c.getParentCount(); pIdx++) {
        node.parents.add(getOrCreate(c.getParent(pIdx)));
      }
    }
  }

  @CanIgnoreReturnValue
  public <V> V walk(Collection<Node> nodes, ParentWalker<V> walk) throws IOException {
    Set<Node> walked = new HashSet<>(nodes.size());
    while (!nodes.isEmpty()) {
      Set<Node> parents = new HashSet<>(1);
      for (Node node : nodes) {
        if (walked.add(node)) {
          if (walk.shouldWalkParents(this, node)) {
            parents.addAll(node.parents);
          }
        }
      }
      nodes = parents;
    }
    return walk.getResult();
  }

  /** Walks with a walker that does not read from the repository. */
  @CanIgnoreReturnValue
  public <V> V walkInMemory(Collection<Node> nodes, ParentWalker<V> walk) {
    try {
      return walk(nodes, walk);
    } catch (IOException e) {
      throw new IllegalStateException("in-memory walk must not read the repository", e);
    }
  }

  public Node getOrCreate(ObjectId id) {
    Node node = get(id);
    if (node == null) {
      node = new Node(id);
      add(node);
    }
    return node;
  }

  protected void setInitialCommit(Node node) {
    node.decorations.add(IC_DECORATION);
    decorated.add(node);
  }

  @CanIgnoreReturnValue
  public Node decorate(ObjectId id, String refName) {
    Node node = getOrCreate(id);
    node.decorations.add(refName);
    decorated.add(node);
    return node;
  }

  /**
   * Drops all undecorated nodes from the id map. After a build or an update only decorated nodes
   * are referenced (by {@link #decorated} and as parents), so the map would otherwise keep every
   * commit of the history that was walked.
   */
  public void clearUndecorated() {
    if (size() == decorated.size()) {
      return;
    }
    clear();
    for (Node node : decorated) {
      add(node);
    }
  }

  /**
   * Links every decorated node directly to its nearest decorated ancestors, skipping undecorated
   * nodes. Pruning undecorated {@link Node}s can greatly reduce the size of the {@link
   * DecoratedDag} since most commits are not decorated. Visits the whole DAG; use it after a full
   * build.
   */
  public void prune() {
    Map<Node, Set<Node>> nearest = new HashMap<>();
    for (Node node : decorated) {
      Set<Node> parents = new HashSet<>();
      for (Node p : node.parents) {
        parents.addAll(nearestDecorated(p, nearest));
      }
      node.parents = parents;
    }
  }

  /**
   * Returns {@code node} if it is decorated, otherwise the nearest decorated ancestors of {@code
   * node}. Results for undecorated nodes are memoized in {@code nearest}, so that a prune visits
   * each undecorated commit once instead of once per path to it. The parents of the undecorated
   * nodes below {@code node} must be known.
   */
  static Set<Node> nearestDecorated(Node node, Map<Node, Set<Node>> nearest) {
    if (!node.decorations.isEmpty()) {
      return Set.of(node);
    }
    Set<Node> known = nearest.get(node);
    if (known != null) {
      return known;
    }
    // Iterative post-order walk: histories are far deeper than the call stack.
    Deque<Node> stack = new ArrayDeque<>();
    stack.push(node);
    while (!stack.isEmpty()) {
      Node n = stack.peek();
      if (nearest.containsKey(n)) {
        stack.pop();
        continue;
      }
      boolean ready = true;
      for (Node p : n.parents) {
        if (p.decorations.isEmpty() && !nearest.containsKey(p)) {
          stack.push(p);
          ready = false;
        }
      }
      if (!ready) {
        continue;
      }
      stack.pop();
      Set<Node> result = new HashSet<>();
      for (Node p : n.parents) {
        if (!p.decorations.isEmpty()) {
          result.add(p);
        } else {
          result.addAll(nearest.get(p));
        }
      }
      nearest.put(n, result);
    }
    return nearest.get(node);
  }
}
