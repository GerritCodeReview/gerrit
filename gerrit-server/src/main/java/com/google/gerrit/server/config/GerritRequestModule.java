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

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.mail.AbandonedSender;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.MergeFailSender;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.RegisterNewEmailSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryRewriter;
import com.google.inject.servlet.RequestScoped;

/** Bindings for {@link RequestScoped} entities. */
public class GerritRequestModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(RequestCleanup.class).in(RequestScoped.class);
    bind(ReviewDb.class).toProvider(RequestScopedReviewDbProvider.class).in(
        RequestScoped.class);
    bind(IdentifiedUser.RequestFactory.class).in(SINGLETON);
    bind(AccountResolver.class);
    bind(ChangeQueryRewriter.class);

    bind(ChangeControl.Factory.class).in(SINGLETON);
    bind(GroupControl.Factory.class).in(SINGLETON);
    bind(ProjectControl.Factory.class).in(SINGLETON);

    factory(ChangeQueryBuilder.Factory.class);
    factory(ReceiveCommits.Factory.class);
    factory(MergeOp.Factory.class);

    // Not really per-request, but dammit, I don't know where else to
    // easily park this stuff.
    //
    factory(AddReviewerSender.Factory.class);
    factory(CreateChangeSender.Factory.class);
    factory(PublishComments.Factory.class);
    factory(ReplacePatchSetSender.Factory.class);
    factory(AbandonedSender.Factory.class);
    factory(CommentSender.Factory.class);
    factory(MergedSender.Factory.class);
    factory(MergeFailSender.Factory.class);
    factory(RegisterNewEmailSender.Factory.class);
    factory(PerformCreateGroup.Factory.class);
  }
}
