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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.change.DraftResource.DRAFT_KIND;
import static com.google.gerrit.server.change.PatchResource.PATCH_KIND;
import static com.google.gerrit.server.change.ReviewerResource.REVIEWER_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.change.Reviewed.DeleteReviewed;
import com.google.gerrit.server.change.Reviewed.PutReviewed;
import com.google.gerrit.server.config.FactoryModule;

public class Module extends RestApiModule {
  @Override
  protected void configure() {
    bind(Revisions.class);
    bind(Reviewers.class);
    bind(Drafts.class);
    bind(Patches.class);

    DynamicMap.mapOf(binder(), CHANGE_KIND);
    DynamicMap.mapOf(binder(), DRAFT_KIND);
    DynamicMap.mapOf(binder(), PATCH_KIND);
    DynamicMap.mapOf(binder(), REVIEWER_KIND);
    DynamicMap.mapOf(binder(), REVISION_KIND);

    get(CHANGE_KIND).to(GetChange.class);
    get(CHANGE_KIND, "detail").to(GetDetail.class);
    get(CHANGE_KIND, "topic").to(GetTopic.class);
    put(CHANGE_KIND, "topic").to(PutTopic.class);
    delete(CHANGE_KIND, "topic").to(PutTopic.class);
    post(CHANGE_KIND, "abandon").to(Abandon.class);
    post(CHANGE_KIND, "restore").to(Restore.class);
    post(CHANGE_KIND, "revert").to(Revert.class);
    post(CHANGE_KIND, "submit").to(Submit.CurrentRevision.class);

    post(CHANGE_KIND, "reviewers").to(PostReviewers.class);
    child(CHANGE_KIND, "reviewers").to(Reviewers.class);
    get(REVIEWER_KIND).to(GetReviewer.class);
    delete(REVIEWER_KIND).to(DeleteReviewer.class);

    child(CHANGE_KIND, "revisions").to(Revisions.class);
    get(REVISION_KIND, "review").to(GetReview.class);
    post(REVISION_KIND, "review").to(PostReview.class);
    post(REVISION_KIND, "submit").to(Submit.class);

    child(REVISION_KIND, "drafts").to(Drafts.class);
    put(REVISION_KIND, "drafts").to(CreateDraft.class);
    get(DRAFT_KIND).to(GetDraft.class);
    put(DRAFT_KIND).to(PutDraft.class);
    delete(DRAFT_KIND).to(DeleteDraft.class);

    child(REVISION_KIND, "files").to(Patches.class);
    put(PATCH_KIND, "reviewed").to(PutReviewed.class);
    delete(PATCH_KIND, "reviewed").to(DeleteReviewed.class);

    install(new FactoryModule() {
      @Override
      protected void configure() {
        factory(ReviewerResource.Factory.class);
        factory(AccountInfo.Loader.Factory.class);
        factory(EmailReviewComments.Factory.class);
      }
    });
  }
}
