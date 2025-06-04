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

/** Guice module that binds all REST endpoints for {@code /changes/<change-id>/revisions}. */
public class ChangeRevisionsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(Revisions.class);

    DynamicMap.mapOf(binder(), REVISION_KIND);

    /** List revisions of change {@code GET /changes/<change-id>/revisions}. */
    child(CHANGE_KIND, "revisions").to(Revisions.class);

    /**
     * Get actions of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/actions}.
     */
    get(REVISION_KIND, "actions").to(GetRevisionActions.class);

    /**
     * Get archived change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/archive}.
     */
    get(REVISION_KIND, "archive").to(GetArchive.class);

    /**
     * Cherry pick change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/cherrypick}.
     */
    post(REVISION_KIND, "cherrypick").to(CherryPick.class);

    /**
     * Get commit details of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/commit}.
     */
    get(REVISION_KIND, "commit").to(GetCommit.class);

    /**
     * Get description of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/description}.
     */
    get(REVISION_KIND, "description").to(GetDescription.class);
    /**
     * Update description of change revision {@code PUT
     * /changes/<change-id>/revisions/<revision-id>/description}.
     */
    put(REVISION_KIND, "description").to(PutDescription.class);

    /**
     * Check mergeability of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/mergeable}.
     */
    get(REVISION_KIND, "mergeable").to(Mergeable.class);

    /**
     * Get list of commits merged with change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/mergelist}.
     */
    get(REVISION_KIND, "mergelist").to(GetMergeList.class);

    /**
     * Get patch of change revision {@code GET /changes/<change-id>/revisions/<revision-id>/patch}.
     */
    get(REVISION_KIND, "patch").to(GetPatch.class);

    /**
     * Get ported comments of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/ported_comments}.
     */
    get(REVISION_KIND, "ported_comments").to(ListPortedComments.class);

    /**
     * Get ported draft comments of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/ported_drafts}.
     */
    get(REVISION_KIND, "ported_drafts").to(ListPortedDrafts.class);

    /** Rebase change revision {@code POST /changes/<change-id>/revisions/<revision-id>/rebase}. */
    post(REVISION_KIND, "rebase").to(Rebase.class);

    /**
     * Get related changes of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/related}.
     */
    get(REVISION_KIND, "related").to(GetRelated.class);

    /**
     * Get review of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/review}.
     */
    get(REVISION_KIND, "review").to(GetReview.class);
    /**
     * Post review of change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/review}.
     */
    post(REVISION_KIND, "review").to(PostReview.class);

    /** Submit change revision {@code POST /changes/<change-id>/revisions/<revision-id>/submit}. */
    post(REVISION_KIND, "submit").to(Submit.class);

    /**
     * Get submit type of change revision {@code GET
     * /changes/<change-id>/revisions/<revision-id>/submit_type}.
     */
    get(REVISION_KIND, "submit_type").to(TestSubmitType.Get.class);

    /**
     * Test submit rule for change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/test.submit_rule}.
     */
    post(REVISION_KIND, "test.submit_rule").to(TestSubmitRule.class);
    /**
     * Test submit type for change revision {@code POST
     * /changes/<change-id>/revisions/<revision-id>/test.submit_type}.
     */
    post(REVISION_KIND, "test.submit_type").to(TestSubmitType.class);

    /** Module for {@code /changes/<change-id>/revisions/<revision-id>/comments}. */
    install(new ChangeRevisionsCommentsRestApiModule());
    /** Module for {@code /changes/<change-id>/revisions/<revision-id>/drafts}. */
    install(new ChangeRevisionsDraftsRestApiModule());
    /** Module for {@code /changes/<change-id>/revisions/<revision-id>/files}. */
    install(new ChangeRevisionsFilesRestApiModule());
    /** Module for {@code /changes/<change-id>/revisions/<revision-id>/fixes}. */
    install(new ChangeRevisionsFixesRestApiModule());
    /** Module for {@code /changes/<change-id>/revisions/<revision-id>/reviewers}. */
    install(new ChangeRevisionsReviewersRestApiModule());
    /** Module for {@code /changes/<change-id>/revisions/<revision-id>/robotcomments}. */
    install(new ChangeRevisionsRobotCommentsRestApiModule());
  }
}
