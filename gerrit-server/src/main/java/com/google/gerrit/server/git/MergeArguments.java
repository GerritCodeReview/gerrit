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
}