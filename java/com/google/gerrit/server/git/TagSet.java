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

import com.google.common.base.MoreObjects;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.NodeProto;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdOwnerMap;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A TagSet keeps track of tag reachability from other refs to make reachability lookups fast.
 *
 * <p>Using a RevWalker to determine reachability on the fly is very expensive since commits are
 * compressed and spread out on disk either in loose objects or pack files in which they may also be
 * deltafied. This class uses a simplified DAG that contains less info and is easier to serialize
 * than the RevCommit DAG. This simplified DAG is decoration (ref) aware and much faster to walk
 * than a RevWalker.
 */
class TagSet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Project.NameKey projectName;
  protected final Dag dag;

  TagSet(Project.NameKey projectName) {
    this(projectName, new Dag());
  }

  TagSet(Project.NameKey projectName, Dag dag) {
    this.projectName = projectName;
    this.dag = dag;
  }

  Project.NameKey getProjectName() {
    return projectName;
  }

  boolean updateFastForward(String refName, ObjectId oldValue, ObjectId newValue) {
    return false;
  }

  public Set<ObjectId> getReachableTags(
      Repository repo, TagMatcher m, Iterable<Ref> refs, Iterable<Ref> tags) {
    Collection<Node> nodes = new ArrayList<>();
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
        getNodeIfUpToDate(repo, m.toUpdate, ref);
      }
    }

    if (!m.toUpdate.isEmpty()) { // shortcut walking if we need to refresh anyway
      return null;
    }
    return (Set<ObjectId>) (Set) dag.walk(nodes, new Dag.ReachableTagWalker());
  }

  protected Node getNodeIfUpToDate(Repository repo, Map<String, ObjectId> toUpdate, Ref ref) {
    ObjectId id = null;
    if (isTag(ref)) {
      try {
        ref = repo.getRefDatabase().peel(ref);
      } catch (IOException e) { // try the rest anyway
      }
      id = ref.getPeeledObjectId();
    }
    if (id == null) {
      id = ref.getObjectId();
    }
    if (id != null) {
      String refName = ref.getName();
      Node node = getDecorated(id);
      if (node != null) {
        if (node.decorations.contains(refName)) {
          return node;
        } // else Need to decorate ref
      } // else Need to insert ref
      try (RevWalk rw = new RevWalk(repo)) {
        RevCommit commit = rw.parseCommit(id);
        toUpdate.put(refName, id);
      } catch (IncorrectObjectTypeException notCommit) { // no use updating non commits
      } catch (IOException e) { // nothing we can do for this ref
      }
    }
    return null;
  }

  protected Node getDecorated(ObjectId id) {
    Node node = dag.get(id);
    if (node == null || node.decorations.isEmpty()) {
      return null;
    }
    return node;
  }

  void build(Repository git, TagSet old, TagMatcher m) {
    if (old != null && m != null) {
      copy(old.dag, dag); // Updating the Dag is not thread safe, so we copy it to update it.
      refresh(git, m.toUpdate);
      return;
    }

    try (RevWalk rw = new RevWalk(git)) {
      rw.setRetainBody(false);
      for (Ref ref : git.getAllRefs().values()) {
        if (!skip(ref)) {
          if (isTag(ref)) {
            try {
              ref = git.getRefDatabase().peel(ref);
            } catch (IOException e) { // try adding it anyway
            }
            addTag(rw, ref);
          } else {
            addRef(rw, ref);
          }
        }
      }

      // Traverse the complete history of all refs creating a Dag for all the commits.
      dag.walk(dag.decorated, new Dag.ParentSettingWalker(rw));
    }
    dag.prune();
  }

  static TagSet fromProto(TagSetProto proto) {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    Map<Integer, Node> nodeByNumber = new LinkedHashMap<>();

    Dag dag = new Dag();
    int n = 0;
    for (NodeProto np : proto.getNodesList()) {
      Node node =
          new Node(idConverter.fromByteString(np.getId()), new HashSet<>(np.getDecorationsList()));
      nodeByNumber.put(n++, node);
      dag.add(node);
      dag.decorated.add(node);
    }

    /* All nodes had to be read before we could directly reference them as parents numerically */
    n = 0;
    for (NodeProto np : proto.getNodesList()) {
      Node node = nodeByNumber.get(n++);
      for (Integer parentNumber : np.getParentNumbersList()) {
        node.parents.add(nodeByNumber.get(parentNumber));
      }
    }

    return new TagSet(Project.nameKey(proto.getProjectName()), dag);
  }

  TagSetProto toProto() {
    Map<Node, Integer> numberByNode = new HashMap<>();
    int nodeCnt = 0;
    for (Node node : dag.decorated) {
      numberByNode.put(node, nodeCnt++);
    }

    ObjectIdConverter idConverter = ObjectIdConverter.create();

    TagSetProto.Builder b = TagSetProto.newBuilder().setProjectName(projectName.get());
    for (Node node : dag.decorated) {
      Collection<Integer> parents = new ArrayList<>(node.parents.size());
      node.parents.forEach((parent) -> parents.add(numberByNode.get(parent)));
      b.addNodes(
          NodeProto.newBuilder()
              .setId(idConverter.toByteString(node))
              .addAllDecorations(node.decorations)
              .addAllParentNumbers(parents)
              .build());
    }
    return b.build();
  }

  /** Only use on a copy of a TagSet that is not in use by other threads. */
  public boolean refresh(Repository repo, Map<String, ObjectId> idByRef) {
    boolean updated = false;
    Map<String, Node> nodeByDecoration = new HashMap<>(dag.decorated.size());
    for (Node node : dag.decorated) {
      for (String ref : node.decorations) {
        nodeByDecoration.put(ref, node);
      }
    }

    RevWalk rw = null;
    try {
      for (Map.Entry<String, ObjectId> e : idByRef.entrySet()) {
        ObjectId id = e.getValue();
        String refName = e.getKey();
        Node node = getDecorated(id);
        if (node != null) {
          if (node.decorations.add(refName)) {
            updated = true; // a new decoration added to an already decorated node
          }
        } else {
          if (rw == null) {
            rw = new RevWalk(repo);
            rw.setRetainBody(false);
          }
          try {
            node = insertDecorated(rw, id, refName); // a previously undecorated commit
            updated = true;
          } catch (IncorrectObjectTypeException ex) {
          } catch (IOException ex) {
            logger.atWarning().withCause(ex).log(
                "Error refreshing tags for repository %s", projectName);
          }
        }

        Node old = nodeByDecoration.get(refName);
        if (old != null && !old.equals(node)) {
          old.decorations.remove(refName); // old node was still decorated
        }
        nodeByDecoration.put(refName, node);
      }
    } finally {
      if (rw != null) {
        rw.close();
      }
    }

    // Ref updates and new refs may have inserted undecorate commits, or they
    // may have left previously decorated commit undecorated, thus prune.
    dag.prune();
    return updated;
  }

  protected void copy(Dag oldDag, Dag newDag) {
    Map<Integer, Collection<Integer>> parentNumbersByNumber = new HashMap<>();
    for (Node oldNode : oldDag.decorated) {
      Node newNode = new Node(oldNode, new HashSet<>(oldNode.decorations));
      newNode.parents = new HashSet<>(oldNode.parents.size());

      newDag.add(newNode);
      newDag.decorated.add(newNode);
    }

    for (Node oldNode : oldDag.decorated) {
      Node newNode = newDag.get(oldNode);
      for (Node parent : oldNode.parents) {
        newNode.parents.add(newDag.get(parent));
      }
    }
  }

  private void addTag(RevWalk rw, Ref ref) {
    ObjectId id = ref.getPeeledObjectId();
    if (id == null) {
      id = ref.getObjectId();
    }
    decorate(rw, id, ref.getName());
  }

  private void addRef(RevWalk rw, Ref ref) {
    decorate(rw, ref.getObjectId(), ref.getName());
  }

  /** If a commit, decorate Node while building Dag */
  protected void decorate(RevWalk rw, ObjectId id, String name) {
    try {
      RevCommit commit = rw.parseCommit(id);
      dag.decorate(id, name);
    } catch (IncorrectObjectTypeException notCommit) {
      // No need to spam the logs.
      // Quite many refs will point to non-commits.
      // For instance, refs from refs/cache-automerge
      // will often end up here.
    } catch (IOException e) {
    }
  }

  static boolean skip(Ref ref) {
    return ref.isSymbolic()
        || ref.getObjectId() == null
        || PatchSet.isChangeRef(ref.getName())
        || RefNames.isNoteDbMetaRef(ref.getName())
        || ref.getName().startsWith(RefNames.REFS_CACHE_AUTOMERGE);
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

  /** Add a new decorated Node to an already built Dag */
  protected Node insertDecorated(RevWalk rw, ObjectId id, String refName)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    RevCommit commitToInsert = rw.parseCommit(id);
    Node nodeToInsert = dag.getOrCreate(id);

    Set<Node> decoratedAncestors =
        dag.walk(Collections.singleton(nodeToInsert), new Dag.UndecoratedWalker(rw));
    dag.decorate(id, refName); // Has to be done after walk to prevent short stop

    for (Node ancestor : decoratedAncestors) {
      nodeToInsert.parents.add(ancestor);
      for (Node child : dag.getChildrenOf(ancestor)) {
        if (!child.equals(nodeToInsert)) {
          try {
            RevCommit cChild = rw.parseCommit(child);
            if (rw.isMergedInto(commitToInsert, cChild)) {
              child.parents.remove(ancestor);
              child.parents.add(nodeToInsert);
            }
          } catch (IOException e) {
          }
        }
      }
    }

    return nodeToInsert;
  }

  /** A DAG which focuses on tracking decorated git commits */
  protected static class Dag extends ObjectIdOwnerMap<Node> {
    /** Base class used to walk a Dag */
    public static class ParentWalker<V> {
      protected V result;

      public boolean walkParents(Dag dag, Node node) {
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
      public boolean walkParents(Dag dag, Node node) {
        for (String refName : node.decorations) {
          if (interesting(refName)) {
            result.add(node);
            break;
          }
        }
        return true;
      }

      public boolean interesting(String refName) {
        return !Dag.IC_DECORATION.equals(refName) && isTag(refName);
      }
    }

    /**
     * Use to prune all undecorated Nodes. This can greatly reduce the size of the Dag since most
     * commits are not decorated. Unlike a RevWalker, this DAG can be serialized.
     */
    protected static class Pruner extends ParentWalker<Boolean> {
      protected static class DecoratedAncestorsFinder extends ParentWalkerNodeSet {
        @Override
        public boolean walkParents(Dag dag, Node node) {
          if (!node.decorations.isEmpty()) {
            result.add(node);
            return false;
          }
          return true;
        }
      }

      @Override
      public boolean walkParents(Dag dag, Node node) {
        node.parents = dag.walk(node.parents, new DecoratedAncestorsFinder());
        return true;
      }
    }

    protected static class ParentSettingWalker extends ParentWalkerNodeSet {
      protected RevWalk rw;

      public ParentSettingWalker(RevWalk rw) {
        this.rw = rw;
      }

      @Override
      public boolean walkParents(Dag dag, Node node) {
        try {
          RevCommit c = rw.parseCommit(node);
          dag.updateParents(c);
          return true;
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }

    protected static class UndecoratedWalker extends ParentSettingWalker {
      public UndecoratedWalker(RevWalk rw) {
        super(rw);
      }

      @Override
      public boolean walkParents(Dag dag, Node node) {
        if (!node.decorations.isEmpty()) {
          result.add(node);
          return false;
        }
        return super.walkParents(dag, node);
      }
    }

    // The DAG includes InitialCommits even if they aren't decorated so that we have
    // a complete "closed" set of DAGs. That means a Dag object may actually contain
    // multiple isolated DAGs. Since we include InitialCommits in such Dags, the only
    // orphans are initial commits that are decorated.
    public static final String IC_DECORATION = ""; // Fake decoration to keep ICs in our DAG.

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

    public void setInitialCommit(Node node) {
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
      walk(decorated, new Pruner());
    }
  }

  /** A node, for use in a Dag, which tracks ref decorations, representing a git commit */
  protected static class Node extends ObjectIdOwnerMap.Entry {
    public final Set<String> decorations;
    public Set<Node> parents = new HashSet<>(1);

    protected Node(ObjectId id) {
      this(id, new HashSet<String>(1));
    }

    protected Node(ObjectId id, Set<String> decorations) {
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
}
