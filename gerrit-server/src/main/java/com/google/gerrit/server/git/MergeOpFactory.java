// Copyright (C) 2008 The Android Open Source Project
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
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.PersonIdent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class MergeOpFactory implements MergeOp.Factory {

  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ProjectCache projectCache;
  private final FunctionState.Factory functionState;
  private final ReplicationQueue replication;
  private final MergedSender.Factory mergedSenderFactory;
  private final MergeFailSender.Factory mergeFailSenderFactory;
  private final Provider<String> urlProvider;
  private final ApprovalTypes approvalTypes;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final MergeQueue mergeQueue;

  private final PersonIdent myIdent;
  private final ChangeHookRunner hooks;
  private final AccountCache accountCache;
  private final CreateCodeReviewNotes.Factory codeReviewNotesFactory;

  private static final Logger log = LoggerFactory.getLogger(MergeOpFactory.class);

  @Inject
  MergeOpFactory(final GitRepositoryManager grm,
      final SchemaFactory<ReviewDb> sf, final ProjectCache pc,
      final FunctionState.Factory fs, final ReplicationQueue rq,
      final MergedSender.Factory msf, final MergeFailSender.Factory mfsf,
      @CanonicalWebUrl @Nullable final Provider<String> cwu,
      final ApprovalTypes approvalTypes, final PatchSetInfoFactory psif,
      final IdentifiedUser.GenericFactory iuf,
      @GerritPersonIdent final PersonIdent myIdent,
      final MergeQueue mergeQueue, final ChangeHookRunner hooks,
      final AccountCache accountCache, final CreateCodeReviewNotes.Factory crnf) {

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

  }

  @Override
  public MergeOp create(Branch.NameKey branch) {

    final Project destProject;

    final ProjectState pe = projectCache.get(branch.getParentKey());
    if (pe == null) {
      log.error("No such project: " + branch.getParentKey());
      return null;
    }
    destProject = pe.getProject();

    switch (destProject.getSubmitType()) {
      case CHERRY_PICK:
        return new CherryPick(repoManager, schemaFactory, projectCache,
            functionState, replication, mergedSenderFactory,
            mergeFailSenderFactory, urlProvider, approvalTypes,
            patchSetInfoFactory, identifiedUserFactory, myIdent, mergeQueue,
            branch, hooks, accountCache, codeReviewNotesFactory, destProject);
      case FAST_FORWARD_ONLY:
        return new FastForwardOnly(repoManager, schemaFactory, projectCache,
            functionState, replication, mergedSenderFactory,
            mergeFailSenderFactory, approvalTypes,
            patchSetInfoFactory, identifiedUserFactory, myIdent, mergeQueue,
            branch, hooks, accountCache, codeReviewNotesFactory, destProject);
      case MERGE_ALWAYS:
        return new MergeAlways(repoManager, schemaFactory, projectCache,
            functionState, replication, mergedSenderFactory,
            mergeFailSenderFactory, approvalTypes,
            patchSetInfoFactory, identifiedUserFactory, myIdent, mergeQueue,
            branch, hooks, accountCache, codeReviewNotesFactory, destProject);
      case MERGE_IF_NECESSARY:
        return new MergeIfNecessary(repoManager, schemaFactory, projectCache,
            functionState, replication, mergedSenderFactory,
            mergeFailSenderFactory, approvalTypes,
            patchSetInfoFactory, identifiedUserFactory, myIdent, mergeQueue,
            branch, hooks, accountCache, codeReviewNotesFactory, destProject);
      default:
        return null;
    }
  }

}
