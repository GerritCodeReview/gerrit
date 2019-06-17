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
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A DAG which focuses on tracking decorated git commits
 *
 * <p>Using a {@link RevWalk} to determine reachability on the fly is very expensive since commits
 * are compressed and spread out on disk either in loose objects or pack files in which they may
 * also be deltafied. This simplified DAG contains less info and is easier to serialize than the
 * {@link RevCommit} DAG. This reduced DAG is decoration (ref) aware and much faster to walk than a
 * {@link RevWalk}.
 */
public class DecoratedDag extends ObjectIdOwnerMap<DecoratedDag.Node> {
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

    /* return whether to continue walking or not */
    public boolean walkParents(DecoratedDag dag, Node node) {
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
    public boolean walkParents(DecoratedDag dag, Node node) {
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

  /**
   * Pruning undecorated {@link Node}s can greatly reduce the size of the {@link DecoratedDag} since
   * most commits are not decorated.
   */
  public static class UndecoratedPruner extends ParentWalker<Boolean> {
    public static class DecoratedAncestorsFinder extends ParentWalkerNodeSet {
      @Override
      public boolean walkParents(DecoratedDag dag, Node node) {
        if (!node.decorations.isEmpty()) {
          result.add(node);
          return false;
        }
        return true;
      }
    }

    @Override
    public boolean walkParents(DecoratedDag dag, Node node) {
      node.parents = dag.walk(node.parents, new DecoratedAncestorsFinder());
      return true;
    }
  }

  public static class UpdateParents extends ParentWalkerNodeSet {
    protected RevWalk rw;

    public UpdateParents(RevWalk rw) {
      this.rw = rw;
    }

    @Override
    public boolean walkParents(DecoratedDag dag, Node node) {
      try {
        RevCommit c = rw.parseCommit(node);
        dag.updateParents(c);
        return true;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Stops when it finds decorated ancestors */
  public static class UpdateUndecoratedParents extends UpdateParents {
    public UpdateUndecoratedParents(RevWalk rw) {
      super(rw);
    }

    @Override
    public boolean walkParents(DecoratedDag dag, Node node) {
      if (!node.decorations.isEmpty()) {
        result.add(node);
        return false;
      }
      return super.walkParents(dag, node);
    }
  }

  // The {@link DecoratedDag} includes initial commits (ICs) even if they aren't decorated so that
  // we have
  // a complete "closed" set of DAGs. That means a {@link DecoratedDag} may actually contain
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

  public <V> V walk(Collection<Node> nodes, ParentWalker<V> walk) {
    Set<Node> walked = new HashSet<>(nodes.size());
    while (!nodes.isEmpty()) {
      Set<Node> parents = new HashSet<>(1);
      for (Node node : nodes) {
        if (walked.add(node)) {
          if (walk.walkParents(this, node)) {
            parents.addAll(node.parents);
          }
        }
      }
      nodes = parents;
    }
    return walk.getResult();
  }

  public Set<Node> getChildrenOf(Node parent) {
    Set<Node> nodes = new HashSet<>();
    for (Node node : decorated) {
      if (node.parents.contains(parent)) {
        nodes.add(node);
      }
    }
    return nodes;
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

  public Node decorate(ObjectId id, String refName) {
    Node node = getOrCreate(id);
    node.decorations.add(refName);
    decorated.add(node);
    return node;
  }

  public void prune() {
    walk(decorated, new UndecoratedPruner());
  }
}
