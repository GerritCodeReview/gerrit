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
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto;
import com.google.gerrit.server.cache.proto.Cache.TagSetHolderProto.TagSetProto.NodeProto;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.git.DecoratedDag.Node;
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
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * A TagSet keeps track of tag reachability from other refs to make reachability lookups fast.
 *
 * <p>Use a cacheable {@link DecoratedDag} to determine reachability on the fly.
 */
class TagSet {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Project.NameKey projectName;
  protected final DecoratedDag dag;

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

  boolean updateFastForward(String refName, ObjectId oldValue, ObjectId newValue) {
    return false;
  }

  @SuppressWarnings("unchecked")
  public Set<ObjectId> getReachableTags(
      Repository repo, TagMatcher m, Iterable<Ref> refs, Iterable<Ref> tags) {
    Collection<Node> nodes = new ArrayList<>();
    for (Ref ref : Iterables.concat(refs, tags)) {
      if (!skip(ref)) {
        Node node = getNodeIfUpToDate(repo, m.toUpdate, ref);
        if (node != null) {
          nodes.add(node);
        }
      }
    }

    if (!m.toUpdate.isEmpty()) { // shortcut walking if we need to refresh anyway
      return null;
    }
    return (Set<ObjectId>) (Set) dag.walk(nodes, new DecoratedDag.ReachableTagWalker());
  }

  @Nullable
  protected Node getNodeIfUpToDate(Repository repo, Map<String, ObjectId> toUpdate, Ref ref) {
    ObjectId id = null;
    if (isTag(ref)) {
      try {
        ref = repo.getRefDatabase().peel(ref);
      } catch (IOException e) {
        // try the rest anyway
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
        }
        // else Need to decorate ref
      }
      // else Need to insert ref

      try (RevWalk rw = new RevWalk(repo)) {
        RevCommit commit = rw.parseCommit(id);
        toUpdate.put(refName, id);
      } catch (IncorrectObjectTypeException notCommit) {
        // no use updating non commits
      } catch (IOException e) {
        // nothing we can do for this ref
      }
    }
    return null;
  }

  @Nullable
  protected Node getDecorated(ObjectId id) {
    Node node = dag.get(id);
    if (node == null || node.decorations.isEmpty()) {
      return null;
    }
    return node;
  }

  void build(Repository git, TagSet old, TagMatcher m) throws IOException {
    if (old != null && m != null) {
      copy(
          old.dag,
          dag); // Updating the {@link DecoratedDag} is not thread safe, copy it to update it.
      refresh(git, m.toUpdate);
      return;
    }

    try (RevWalk rw = new RevWalk(git)) {
      rw.setRetainBody(false);
      for (Ref ref : git.getRefDatabase().getRefs()) {
        if (!skip(ref)) {
          if (isTag(ref)) {
            try {
              ref = git.getRefDatabase().peel(ref);
            } catch (IOException e) {
              // try adding it anyway
            }
            addTag(rw, ref);
          } else {
            addRef(rw, ref);
          }
        }
      }

      // Traverse the complete history of all refs creating a DAG for all the commits.
      dag.walk(dag.decorated, new DecoratedDag.ParentSettingWalker(rw));
    }
    dag.prune();
  }

  static TagSet fromProto(TagSetProto proto) {
    ObjectIdConverter idConverter = ObjectIdConverter.create();
    Map<Integer, Node> nodeByNumber = new LinkedHashMap<>();

    DecoratedDag dag = new DecoratedDag();
    int n = 0;
    for (NodeProto np : proto.getNodesList()) {
      Node node =
          new Node(idConverter.fromByteString(np.getId()), new HashSet<>(np.getDecorationsList()));
      nodeByNumber.put(n++, node);
      dag.add(node);
      dag.decorated.add(node);
    }

    // All {@link Node}s had to be read before we could numerically reference them as parents
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

  /** Only use on a copy of a {@link TagSet} that is not in use by other threads. */
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
            updated = true; // a new decoration was added to an already decorated {@link Node}
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
          old.decorations.remove(refName); // old {@link Node} was still decorated
        }
        nodeByDecoration.put(refName, node);
      }
    } finally {
      if (rw != null) {
        rw.close();
      }
    }

    // We may need pruning since ref updates and new refs may have inserted undecorated commits,
    // or they may have left previously decorated commits undecorated.
    dag.prune();
    return updated;
  }

  protected void copy(DecoratedDag oldDecoratedDag, DecoratedDag newDecoratedDag) {
    Map<Integer, Collection<Integer>> parentNumbersByNumber = new HashMap<>();
    for (Node oldNode : oldDecoratedDag.decorated) {
      Node newNode = new Node(oldNode, new HashSet<>(oldNode.decorations));
      newNode.parents = new HashSet<>(oldNode.parents.size());

      newDecoratedDag.add(newNode);
      newDecoratedDag.decorated.add(newNode);
    }

    for (Node oldNode : oldDecoratedDag.decorated) {
      Node newNode = newDecoratedDag.get(oldNode);
      for (Node parent : oldNode.parents) {
        newNode.parents.add(newDecoratedDag.get(parent));
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

  /** If a commit, decorate {@link Node} while building {@link DecoratedDag} */
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

  /** Add a new decorated {@link Node} to an already built {@link DecoratedDag} */
  protected Node insertDecorated(RevWalk rw, ObjectId id, String refName)
      throws IncorrectObjectTypeException, IOException, MissingObjectException {
    RevCommit commitToInsert = rw.parseCommit(id);
    Node nodeToInsert = dag.getOrCreate(id);

    Set<Node> decoratedAncestors =
        dag.walk(Collections.singleton(nodeToInsert), new DecoratedDag.UndecoratedWalker(rw));
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
}
