// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change.revisions;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.revisions.comments.ChangeRevisionsCommentsRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.drafts.ChangeRevisionsDraftsRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.files.ChangeRevisionsFilesRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.fixes.ChangeRevisionsFixesRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.reviewers.ChangeRevisionsReviewersRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.robotcomments.ChangeRevisionsRobotCommentsRestApiModule;

public class ChangeRevisionsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Revisions.class);

    DynamicMap.mapOf(binder(), REVISION_KIND);

    child(CHANGE_KIND, "revisions").to(Revisions.class);

    get(REVISION_KIND, "actions").to(GetRevisionActions.class);

    get(REVISION_KIND, "archive").to(GetArchive.class);

    post(REVISION_KIND, "cherrypick").to(CherryPick.class);

    get(REVISION_KIND, "commit").to(GetCommit.class);

    get(REVISION_KIND, "description").to(GetDescription.class);
    put(REVISION_KIND, "description").to(PutDescription.class);

    get(REVISION_KIND, "mergeable").to(Mergeable.class);

    get(REVISION_KIND, "mergelist").to(GetMergeList.class);

    get(REVISION_KIND, "patch").to(GetPatch.class);

    get(REVISION_KIND, "ported_comments").to(ListPortedComments.class);

    get(REVISION_KIND, "ported_drafts").to(ListPortedDrafts.class);

    post(REVISION_KIND, "rebase").to(Rebase.class);

    get(REVISION_KIND, "related").to(GetRelated.class);

    get(REVISION_KIND, "review").to(GetReview.class);
    post(REVISION_KIND, "review").to(PostReview.class);

    post(REVISION_KIND, "submit").to(Submit.class);

    get(REVISION_KIND, "submit_type").to(TestSubmitType.Get.class);

    post(REVISION_KIND, "test.submit_rule").to(TestSubmitRule.class);
    post(REVISION_KIND, "test.submit_type").to(TestSubmitType.class);

    install(new ChangeRevisionsCommentsRestApiModule());
    install(new ChangeRevisionsDraftsRestApiModule());
    install(new ChangeRevisionsFilesRestApiModule());
    install(new ChangeRevisionsFixesRestApiModule());
    install(new ChangeRevisionsReviewersRestApiModule());
    install(new ChangeRevisionsRobotCommentsRestApiModule());
  }
}
