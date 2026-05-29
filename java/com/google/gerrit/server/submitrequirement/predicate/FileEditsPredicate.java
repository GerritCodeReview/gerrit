// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.submitrequirement.predicate;

import com.google.auto.value.AutoValue;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.FilePathAdapter;
import com.google.gerrit.server.patch.Text;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.patch.filediff.TaggedEdit;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * A submit-requirement predicate that can be used in submit requirements expressions. This
 * predicate is fulfilled if the diff between the latest patchset of the change and the base commit
 * includes a specific file path pattern with some specific content modification. The modification
 * could be an added, deleted or replaced content.
 */
public class FileEditsPredicate extends SubmitRequirementPredicate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private DiffOperations diffOperations;
  private GitRepositoryManager repoManager;
  private final FileEditsArgs fileEditsArgs;

  public interface Factory {
    FileEditsPredicate create(FileEditsArgs fileEditsArgs);
  }

  @AutoValue
  public abstract static class FileEditsArgs {
    abstract String filePattern();

    abstract String editPattern();

    public static FileEditsArgs create(String filePattern, String contentPattern) {
      return new AutoValue_FileEditsPredicate_FileEditsArgs(filePattern, contentPattern);
    }
  }

  @AssistedInject
  public FileEditsPredicate(
      DiffOperations diffOperations,
      GitRepositoryManager repoManager,
      @Assisted FileEditsPredicate.FileEditsArgs fileEditsArgs) {
    super("fileEdits", fileEditsArgs.filePattern() + "," + fileEditsArgs.editPattern());
    this.diffOperations = diffOperations;
    this.repoManager = repoManager;
    this.fileEditsArgs = fileEditsArgs;
  }

  @Override
  public boolean match(ChangeData cd) {
    try {
      Map<String, FileDiffOutput> modifiedFiles =
          diffOperations.listModifiedFilesAgainstParent(
              cd.project(),
              cd.currentPatchSet().commitId(),
              /* parentNum= */ 0,
              DiffOptions.DEFAULTS);
      FileDiffOutput firstDiff =
          Iterables.getFirst(modifiedFiles.values(), /* defaultValue= */ null);
      if (firstDiff == null) {
        // No available diffs. We cannot identify old and new commit IDs.
        // engine.fail();
        return false;
      }

      Pattern filePattern = null;
      Pattern editPattern = null;
      if (fileEditsArgs.filePattern().startsWith("^")) {
        // We validated the pattern before creating this predicate. No need to revalidate.
        String pattern = fileEditsArgs.filePattern();
        filePattern = Pattern.compile(pattern);
      }
      if (fileEditsArgs.editPattern().startsWith("^")) {
        // We validated the pattern before creating this predicate. No need to revalidate.
        String pattern = fileEditsArgs.editPattern();
        editPattern = Pattern.compile(pattern);
      }
      try (Repository repo = repoManager.openRepository(cd.project());
          ObjectReader reader = repo.newObjectReader();
          RevWalk rw = new RevWalk(reader)) {
        RevTree aTree =
            firstDiff.oldCommitId().equals(ObjectId.zeroId())
                ? null
                : rw.parseTree(firstDiff.oldCommitId());
        RevTree bTree = rw.parseCommit(firstDiff.newCommitId()).getTree();

        for (FileDiffOutput entry : modifiedFiles.values()) {
          String newName =
              FilePathAdapter.getNewPath(entry.oldPath(), entry.newPath(), entry.changeType());
          String oldName = FilePathAdapter.getOldPath(entry.oldPath(), entry.changeType());

          if (Patch.isMagic(newName)) {
            continue;
          }

          if (match(newName, fileEditsArgs.filePattern(), filePattern)
              || (oldName != null && match(oldName, fileEditsArgs.filePattern(), filePattern))) {
            List<Edit> edits =
                entry.edits().stream().map(TaggedEdit::jgitEdit).collect(Collectors.toList());
            if (edits.isEmpty()) {
              continue;
            }
            Text tA;
            if (oldName != null) {
              tA = load(aTree, oldName, reader);
            } else {
              tA = load(aTree, newName, reader);
            }
            Text tB = load(bTree, newName, reader);
            for (Edit edit : edits) {
              if (tA != Text.EMPTY) {
                String aDiff = tA.getString(edit.getBeginA(), edit.getEndA(), true);
                if (match(aDiff, fileEditsArgs.editPattern(), editPattern)) {
                  return true;
                }
              }
              if (tB != Text.EMPTY) {
                String bDiff = tB.getString(edit.getBeginB(), edit.getEndB(), true);
                if (match(bDiff, fileEditsArgs.editPattern(), editPattern)) {
                  return true;
                }
              }
            }
          }
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Error while evaluating commit edits.");
        return false;
      }
    } catch (DiffNotAvailableException e) {
      logger.atSevere().withCause(e).log("Diff error while evaluating commit edits.");
      return false;
    }
    return false;
  }

  @Override
  public int getCost() {
    return 10;
  }

  private Text load(@Nullable ObjectId tree, String path, ObjectReader reader) throws IOException {
    if (tree == null || path == null) {
      return Text.EMPTY;
    }
    final TreeWalk tw = TreeWalk.forPath(reader, path, tree);
    if (tw == null) {
      return Text.EMPTY;
    }
    if (tw.getFileMode(0).getObjectType() != Constants.OBJ_BLOB) {
      return Text.EMPTY;
    }
    return new Text(reader.open(tw.getObjectId(0), Constants.OBJ_BLOB));
  }

  private boolean match(String text, String search, @Nullable Pattern searchPattern) {
    return searchPattern == null ? text.contains(search) : searchPattern.matcher(text).find();
  }
}
