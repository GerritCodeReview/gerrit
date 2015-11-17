// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.gerrit.common.ChangeHookRunner.HookResult;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;

import java.util.Map;
import java.util.Set;

/** Invokes hooks on server actions. */
public interface ChangeHooks {
  /**
   * Fire the Patchset Created Hook.
   *
   * @param change The change itself.
   * @param patchSet The Patchset that was created.
   * @throws OrmException
   */
  void doPatchsetCreatedHook(Change change, PatchSet patchSet,
      ReviewDb db) throws OrmException;

  /**
   * Fire the Draft Published Hook.
   *
   * @param change The change itself.
   * @param patchSet The Patchset that was published.
   * @throws OrmException
   */
  void doDraftPublishedHook(Change change, PatchSet patchSet,
      ReviewDb db) throws OrmException;

  /**
   * Fire the Comment Added Hook.
   *
   * @param change The change itself.
   * @param patchSet The patchset this comment is related to.
   * @param account The gerrit user who added the comment.
   * @param comment The comment given.
   * @param approvals Map of label IDs to scores
   * @param approvalStatus Map of label IDs to score status
   * @throws OrmException
   */
  void doCommentAddedHook(Change change, Account account,
      PatchSet patchSet, String comment,
      Map<String, Short> approvals, Map<String, Boolean> approvalStatus,
      ReviewDb db)
      throws OrmException;

  /**
   * Fire the Change Merged Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who submitted the change.
   * @param patchSet The patchset that was merged.
   * @param mergeResultRev The SHA-1 of the merge result revision.
   * @throws OrmException
   */
  void doChangeMergedHook(Change change, Account account,
      PatchSet patchSet, ReviewDb db, String mergeResultRev) throws OrmException;

  /**
   * Fire the Merge Failed Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who attempted to submit the change.
   * @param patchSet The patchset that failed to merge.
   * @param reason The reason that the change failed to merge.
   * @throws OrmException
   */
  void doMergeFailedHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) throws OrmException;

  /**
   * Fire the Change Abandoned Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who abandoned the change.
   * @param reason Reason for abandoning the change.
   * @throws OrmException
   */
  void doChangeAbandonedHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) throws OrmException;

  /**
   * Fire the Change Restored Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who restored the change.
   * @param reason Reason for restoring the change.
   * @throws OrmException
   */
  void doChangeRestoredHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) throws OrmException;

  /**
   * Fire the Ref Updated Hook
   *
   * @param refName The updated project and branch.
   * @param refUpdate An actual RefUpdate object
   * @param account The gerrit user who moved the ref
   */
  void doRefUpdatedHook(Branch.NameKey refName, RefUpdate refUpdate,
      Account account);

  /**
   * Fire the Ref Updated Hook
   *
   * @param refName The Branch.NameKey of the ref that was updated
   * @param oldId The ref's old id
   * @param newId The ref's new id
   * @param account The gerrit user who moved the ref
   */
  void doRefUpdatedHook(Branch.NameKey refName, ObjectId oldId,
      ObjectId newId, Account account);

  /**
   * Fire the Reviewer Added Hook
   *
   * @param change The change itself.
   * @param patchSet The patchset that the reviewer was added on.
   * @param account The gerrit user who was added as reviewer.
   */
  void doReviewerAddedHook(Change change, Account account,
      PatchSet patchSet, ReviewDb db) throws OrmException;

  /**
   * Fire the Topic Changed Hook
   *
   * @param change The change itself.
   * @param account The gerrit user who changed the topic.
   * @param oldTopic The old topic name.
   */
  void doTopicChangedHook(Change change, Account account,
      String oldTopic, ReviewDb db) throws OrmException;

  void doClaSignupHook(Account account, String claName);

  /**
   * Fire the Ref update Hook
   *
   * @param project The target project
   * @param refName The Branch.NameKey of the ref provided by client
   * @param uploader The gerrit user running the command
   * @param oldId The ref's old id
   * @param newId The ref's new id
   */
  HookResult doRefUpdateHook(Project project,  String refName,
       Account uploader, ObjectId oldId, ObjectId newId);

  /**
   * Fire the hashtags changed Hook.
   * @param change The change
   * @param account The gerrit user changing the hashtags
   * @param added List of hashtags that were added to the change
   * @param removed List of hashtags that were removed from the change
   * @param hashtags List of hashtags on the change after adding or removing
   * @param db The database
   * @throws OrmException
   */
  void doHashtagsChangedHook(Change change, Account account,
      Set<String>added, Set<String> removed, Set<String> hashtags,
      ReviewDb db) throws OrmException;

  /**
   * Fire the project created hook
   *
   * @param project The project that was created
   * @param headName The head name of the created project
   */
  void doProjectCreatedHook(Project.NameKey project, String headName);
}
