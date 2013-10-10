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
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.ChangeEvent;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;

import java.util.Map;

/** Does not invoke hooks. */
public final class DisabledChangeHooks implements ChangeHooks {
  @Override
  public void addChangeListener(ChangeListener listener, IdentifiedUser user) {
  }

  @Override
  public void doChangeAbandonedHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) {
  }

  @Override
  public void doChangeMergedHook(Change change, Account account,
      PatchSet patchSet, ReviewDb db) {
  }

  @Override
  public void doMergeFailedHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) {
  }

  @Override
  public void doChangeRestoredHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) {
  }

  @Override
  public void doClaSignupHook(Account account, ContributorAgreement cla) {
  }

  @Override
  public void doCommentAddedHook(Change change, Account account,
      PatchSet patchSet, String comment,
      Map<String, Short> approvals, ReviewDb db) {
  }

  @Override
  public void doPatchsetCreatedHook(Change change, PatchSet patchSet,
      ReviewDb db) {
  }

  @Override
  public void doDraftPublishedHook(Change change, PatchSet patchSet,
      ReviewDb db) {
  }

  @Override
  public void doRefUpdatedHook(NameKey refName, RefUpdate refUpdate,
      Account account) {
  }

  @Override
  public void doRefUpdatedHook(NameKey refName, ObjectId oldId, ObjectId newId,
      Account account) {
  }

  @Override
  public void doReviewerAddedHook(Change change, Account account, PatchSet patchSet,
      ReviewDb db) {
  }

  @Override
  public void doTopicChangedHook(Change change, Account account, String oldTopic,
      ReviewDb db) {
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
  }

  @Override
  public HookResult doRefUpdateHook(Project project, String refName,
      Account uploader, ObjectId oldId, ObjectId newId) {
    return null;
  }

  @Override
  public void postEvent(Change change, ChangeEvent event, ReviewDb db) {
  }

  @Override
  public void postEvent(Branch.NameKey branchName, ChangeEvent event) {
  }
}
