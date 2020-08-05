// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.edit.tree;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * A creator for a new Git tree. To create the new tree, the tree of another commit is taken as a
 * basis and modified. Alternatively, an empty tree can serve as base.
 */
public class TreeCreator {

  private final ObjectId baseTreeId;
  private final ImmutableList<? extends ObjectId> baseParents;
  private final List<TreeModification> treeModifications = new ArrayList<>();

  public static TreeCreator basedOn(RevCommit baseCommit) {
    requireNonNull(baseCommit, "baseCommit is required");
    return new TreeCreator(baseCommit.getTree(), ImmutableList.copyOf(baseCommit.getParents()));
  }

  public static TreeCreator basedOnEmptyTree() {
    return new TreeCreator(ObjectId.zeroId(), ImmutableList.of());
  }

  private TreeCreator(ObjectId baseTreeId, ImmutableList<? extends ObjectId> baseParents) {
    this.baseTreeId = requireNonNull(baseTreeId, "baseTree is required");
    this.baseParents = baseParents;
  }

  /**
   * Apply modifications to the tree which is taken as a basis. If this method is called multiple
   * times, the modifications are applied subsequently in exactly the order they were provided.
   *
   * @param treeModifications modifications which should be applied to the base tree
   */
  public void addTreeModifications(List<TreeModification> treeModifications) {
    requireNonNull(treeModifications, "treeModifications must not be null");
    this.treeModifications.addAll(treeModifications);
  }

  /**
   * Creates the new tree. When this method is called, the specified base tree is read from the
   * repository, the specified modifications are applied, and the resulting tree is written to the
   * object store of the repository.
   *
   * @param repository the affected Git repository
   * @return the {@code ObjectId} of the created tree
   * @throws IOException if problems arise when accessing the repository
   */
  public ObjectId createNewTreeAndGetId(Repository repository) throws IOException {
    DirCache newTree = createNewTree(repository);
    return writeAndGetId(repository, newTree);
  }

  private DirCache createNewTree(Repository repository) throws IOException {
    DirCache newTree = readBaseTree(repository);
    List<DirCacheEditor.PathEdit> pathEdits = getPathEdits(repository);
    applyPathEdits(newTree, pathEdits);
    return newTree;
  }

  private DirCache readBaseTree(Repository repository) throws IOException {
    try (ObjectReader objectReader = repository.newObjectReader()) {
      DirCache dirCache = DirCache.newInCore();
      DirCacheBuilder dirCacheBuilder = dirCache.builder();
      if (!ObjectId.zeroId().equals(baseTreeId)) {
        dirCacheBuilder.addTree(new byte[0], DirCacheEntry.STAGE_0, objectReader, baseTreeId);
      }
      dirCacheBuilder.finish();
      return dirCache;
    }
  }

  private List<DirCacheEditor.PathEdit> getPathEdits(Repository repository) throws IOException {
    List<DirCacheEditor.PathEdit> pathEdits = new ArrayList<>();
    for (TreeModification treeModification : treeModifications) {
      pathEdits.addAll(
          treeModification.getPathEdits(repository, baseTreeId, ImmutableList.copyOf(baseParents)));
    }
    return pathEdits;
  }

  private static void applyPathEdits(DirCache tree, List<DirCacheEditor.PathEdit> pathEdits) {
    DirCacheEditor dirCacheEditor = tree.editor();
    pathEdits.forEach(dirCacheEditor::add);
    dirCacheEditor.finish();
  }

  private static ObjectId writeAndGetId(Repository repository, DirCache tree) throws IOException {
    try (ObjectInserter objectInserter = repository.newObjectInserter()) {
      ObjectId treeId = tree.writeTree(objectInserter);
      objectInserter.flush();
      return treeId;
    }
  }
}
