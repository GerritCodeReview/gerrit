// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.audit.AuditModule;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.changedetail.DeleteDraftPatchSet;
import com.google.gerrit.server.changedetail.PublishDraft;
import com.google.gerrit.server.git.AsyncReceiveCommits;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.SubmoduleOp;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.mail.CommitMessageEditedSender;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.RebasedPatchSetSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.AddReviewer;
import com.google.gerrit.server.patch.RemoveReviewer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.PerRequestProjectControlCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.SuggestParentCandidates;
import com.google.inject.servlet.RequestScoped;

/** Bindings for {@link RequestScoped} entities. */
public class GerritRequestModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(RequestCleanup.class).in(RequestScoped.class);
    bind(RequestScopedReviewDbProvider.class);
    bind(IdentifiedUser.RequestFactory.class).in(SINGLETON);
    bind(MetaDataUpdate.User.class).in(RequestScoped.class);
    bind(ApprovalsUtil.class);

    bind(PerRequestProjectControlCache.class).in(RequestScoped.class);
    bind(ChangeControl.Factory.class).in(SINGLETON);
    bind(ProjectControl.Factory.class).in(SINGLETON);

    factory(SubmoduleOp.Factory.class);
    factory(MergeOp.Factory.class);
    install(new AsyncReceiveCommits.Module());

    // Not really per-request, but dammit, I don't know where else to
    // easily park this stuff.
    //
    factory(AddReviewer.Factory.class);
    factory(AddReviewerSender.Factory.class);
    factory(CommitMessageEditedSender.Factory.class);
    factory(CreateChangeSender.Factory.class);
    factory(DeleteDraftPatchSet.Factory.class);
    factory(PublishDraft.Factory.class);
    factory(ReplacePatchSetSender.Factory.class);
    factory(RebasedPatchSetSender.Factory.class);
    factory(RemoveReviewer.Factory.class);
    factory(MergedSender.Factory.class);
    factory(MergeFailSender.Factory.class);
    factory(GroupMembers.Factory.class);
    factory(CreateProject.Factory.class);
    factory(SuggestParentCandidates.Factory.class);
    factory(BanCommit.Factory.class);

    // Built-in REST API endpoints may depend on request-scoped data or
    // factories bound above.
    install(new AuditModule());
    install(new com.google.gerrit.server.account.Module());
    install(new com.google.gerrit.server.change.Module());
    install(new com.google.gerrit.server.group.Module());
    install(new com.google.gerrit.server.project.Module());
  }
}
