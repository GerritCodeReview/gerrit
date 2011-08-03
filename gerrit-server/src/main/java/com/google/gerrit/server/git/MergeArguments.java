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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.PersonIdent;

import javax.annotation.Nullable;

class MergeArguments {
  final GitRepositoryManager repoManager;
  final SchemaFactory<ReviewDb> schemaFactory;
  final ProjectCache projectCache;
  final FunctionState.Factory functionState;
  final ReplicationQueue replication;
  final MergedSender.Factory mergedSenderFactory;
  final MergeFailSender.Factory mergeFailSenderFactory;
  final Provider<String> urlProvider;
  final ApprovalTypes approvalTypes;
  final PatchSetInfoFactory patchSetInfoFactory;
  final IdentifiedUser.GenericFactory identifiedUserFactory;
  final MergeQueue mergeQueue;
  final PersonIdent myIdent;
  final ChangeHookRunner hooks;
  final AccountCache accountCache;
  final CreateCodeReviewNotes.Factory codeReviewNotesFactory;
  final ChangeControl.GenericFactory changeControlFactory;
  final TagCache tagCache;

  Branch.NameKey destBranch;
  Project destProject;

  @Inject
  MergeArguments(final GitRepositoryManager grm,
      final SchemaFactory<ReviewDb> sf, final ProjectCache pc,
      final FunctionState.Factory fs, final ReplicationQueue rq,
      final MergedSender.Factory msf, final MergeFailSender.Factory mfsf,
      @CanonicalWebUrl @Nullable final Provider<String> cwu,
      final ApprovalTypes approvalTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf,
      @GerritPersonIdent final PersonIdent myIdent,
      final MergeQueue mergeQueue, final ChangeHookRunner hooks,
      final AccountCache accountCache, final CreateCodeReviewNotes.Factory crnf,
      final ChangeControl.GenericFactory changeControlFactory,
      final TagCache tagCache) {
    repoManager = grm;
    schemaFactory = sf;
    functionState = fs;
    projectCache = pc;
    replication = rq;
    mergedSenderFactory = msf;
    mergeFailSenderFactory = mfsf;
    urlProvider = cwu;
    this.approvalTypes = approvalTypes;
    patchSetInfoFactory = psif;
    identifiedUserFactory = iuf;
    this.mergeQueue = mergeQueue;
    this.hooks = hooks;
    this.accountCache = accountCache;
    codeReviewNotesFactory = crnf;
    this.myIdent = myIdent;
    this.changeControlFactory = changeControlFactory;
    this.tagCache = tagCache;
  }
}