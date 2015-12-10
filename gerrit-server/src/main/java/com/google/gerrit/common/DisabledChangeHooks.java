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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.events.Event;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;

import java.util.Map;
import java.util.Set;

/** Does not invoke hooks. */
public final class DisabledChangeHooks implements ChangeHooks, EventDispatcher,
    EventSource {
  @Override
  public void addEventListener(EventListener listener, CurrentUser user) {
  }

  @Override
  public void doChangeAbandonedHook(Change change, Account account,
      PatchSet patchSet, String reason, ReviewDb db) {
  }

  @Override
  public void doChangeMergedHook(Change change, Account account,
      PatchSet patchSet, ReviewDb db, String mergeResultRev) {
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
  public void doClaSignupHook(Account account, String claName) {
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
  public void doRefUpdatedHook(Branch.NameKey refName, RefUpdate refUpdate,
      Account account) {
  }

  @Override
  public void doRefUpdatedHook(Branch.NameKey refName, ObjectId oldId,
      ObjectId newId, Account account) {
  }

  @Override
  public void doReviewerAddedHook(Change change, Account account, PatchSet patchSet,
      ReviewDb db) {
  }

  @Override
  public void doReviewerDeletedHook(Change change, Account account, PatchSet patchSet,
      ReviewDb db) {
  }

  @Override
  public void doTopicChangedHook(Change change, Account account, String oldTopic,
      ReviewDb db) {
  }

  @Override
  public void doHashtagsChangedHook(Change change, Account account, Set<String> added,
      Set<String> removed, Set<String> hashtags, ReviewDb db) {
  }

  @Override
  public void removeEventListener(EventListener listener) {
  }

  @Override
  public HookResult doRefUpdateHook(Project project, String refName,
      Account uploader, ObjectId oldId, ObjectId newId) {
    return null;
  }

  @Override
  public void doProjectCreatedHook(Project.NameKey project, String headName) {
  }

  @Override
  public void postEvent(Change change, Event event, ReviewDb db) {
  }

  @Override
  public void postEvent(Branch.NameKey branchName, Event event) {
  }
}
