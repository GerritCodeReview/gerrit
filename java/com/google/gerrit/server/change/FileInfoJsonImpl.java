package com.google.gerrit.server.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Package private interface to compute and return the list of modified files between two commits.
 */
interface FileInfoJsonImpl {

  /**
   * Computes the list of modified files for a given change and patchset against the parent commit.
   *
   * @param change a Gerrit change.
   * @param patchSet a single revision of the change.
   * @return a mapping of the file paths to their related diff information.
   */
  default Map<String, FileInfo> getFileInfoMap(Change change, PatchSet patchSet)
      throws ResourceConflictException, PatchListNotAvailableException {
    return getFileInfoMap(change, patchSet.commitId(), null);
  }

  /**
   * Computes the list of modified files for a given change and patchset against its parent. For
   * merge commits, callers can use 0, 1, 2, etc... to choose a specific parent. The first parent is
   * 0.
   *
   * @param change a Gerrit change.
   * @param objectId a commit SHA-1 identifying a patchset commit.
   * @param parentNum an integer identifying the parent number used for comparison.
   * @return a mapping of the file paths to their related diff information.
   */
  default Map<String, FileInfo> getFileInfoMap(Change change, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException {
    return getFileInfoMap(change.getProject(), objectId, parentNum);
  }

  /**
   * Computes the list of modified files for a given change and patchset identified by its {@code
   * objectId} against a specified base patchset.
   *
   * @param change a Gerrit change.
   * @param objectId a commit SHA-1 identifying a patchset commit.
   * @param base a base patchset to compare the commit identified by {@code objectId} against.
   * @return a mapping of the file paths to their related diff information.
   */
  Map<String, FileInfo> getFileInfoMap(Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException;

  /**
   * Computes the list of modified files for a given project and commit against its parent. For
   * merge commits, callers can use 0, 1, 2, etc... to choose a specific parent. The first parent is
   * 0.
   *
   * @param project a project identifying a repository.
   * @param objectId a commit SHA-1 identifying a patchset commit.
   * @param parentNum an integer identifying the parent number used for comparison.
   * @return a mapping of the file paths to their related diff information.
   */
  Map<String, FileInfo> getFileInfoMap(Project.NameKey project, ObjectId objectId, int parentNum)
      throws ResourceConflictException, PatchListNotAvailableException;
}
