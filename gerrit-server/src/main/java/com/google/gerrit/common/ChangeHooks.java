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

import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;

import java.util.Map;

/** Invokes hooks on server actions. */
public interface ChangeHooks {
  public void addChangeListener(ChangeListener listener, IdentifiedUser user);

  public void removeChangeListener(ChangeListener listener);

  /**
   * Fire the Patchset Created Hook.
   *
   * @param change The change itself.
   * @param patchSet The Patchset that was created.
   * @throws OrmException
   */
  public void doPatchsetCreatedHook(Change change, PatchSet patchSet,
      ReviewDb db) throws OrmException;

  /**
   * Fire the Comment Added Hook.
   *
   * @param change The change itself.
   * @param patchSet The patchset this comment is related to.
   * @param account The gerrit user who commited the change.
   * @param comment The comment given.
   * @param approvals Map of Approval Categories and Scores
   * @throws OrmException
   */
  public void doCommentAddedHook(Change change, Account account,
      PatchSet patchSet, String comment,
      Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> approvals, ReviewDb db)
      throws OrmException;

  /**
   * Fire the Change Merged Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who commited the change.
   * @param patchSet The patchset that was merged.
   * @throws OrmException
   */
  public void doChangeMergedHook(Change change, Account account,
      PatchSet patchSet, ReviewDb db) throws OrmException;

  /**
   * Fire the Change Abandoned Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who abandoned the change.
   * @param reason Reason for abandoning the change.
   * @throws OrmException
   */
  public void doChangeAbandonedHook(Change change, Account account,
      String reason, ReviewDb db) throws OrmException;

  /**
   * Fire the Change Restored Hook.
   *
   * @param change The change itself.
   * @param account The gerrit user who restored the change.
   * @param reason Reason for restoring the change.
   * @throws OrmException
   */
  public void doChangeRestoreHook(Change change, Account account,
      String reason, ReviewDb db) throws OrmException;

  /**
   * Fire the Ref Updated Hook
   *
   * @param refName The updated project and branch.
   * @param refUpdate An actual RefUpdate object
   * @param account The gerrit user who moved the ref
   */
  public void doRefUpdatedHook(Branch.NameKey refName, RefUpdate refUpdate,
      Account account);

  /**
   * Fire the Ref Updated Hook
   *
   * @param refName The Branch.NameKey of the ref that was updated
   * @param oldId The ref's old id
   * @param newId The ref's new id
   * @param account The gerrit user who moved the ref
   */
  public void doRefUpdatedHook(Branch.NameKey refName, ObjectId oldId,
      ObjectId newId, Account account);

  public void doClaSignupHook(Account account, ContributorAgreement cla);
}
