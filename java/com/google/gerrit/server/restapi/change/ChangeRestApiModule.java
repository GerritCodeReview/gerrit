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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.attentionset.ChangeAttentionSetRestApiModule;
import com.google.gerrit.server.restapi.change.edit.ChangeEditRestApiModule;
import com.google.gerrit.server.restapi.change.flow.ChangeFlowsRestApiModule;
import com.google.gerrit.server.restapi.change.messages.ChangeMessagesRestApiModule;
import com.google.gerrit.server.restapi.change.reviewers.ChangeReviewersRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.ChangeRevisionsRestApiModule;
import com.google.gerrit.server.restapi.change.revisions.Rebase;
import com.google.gerrit.server.restapi.change.revisions.Submit;

/** Guice module that binds all REST endpoints for {@code /changes/}. */
public class ChangeRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CHANGE_KIND);

    /** List/query changes {@code GET /changes/}. */
    bind(ChangesCollection.class);
    /** Create change {@code POST /changes/}. */
    postOnCollection(CHANGE_KIND).to(CreateChange.class);

    /** Delete change {@code DELETE /changes/<change-id>}. */
    delete(CHANGE_KIND).to(DeleteChange.class);
    /** Get change {@code GET /changes/<change-id>}. */
    get(CHANGE_KIND).to(GetChange.class);

    /** Abandon change {@code POST /changes/<change-id>/abandon}. */
    post(CHANGE_KIND, "abandon").to(Abandon.class);

    /** Check consistency of change {@code GET /changes/<change-id>/check}. */
    get(CHANGE_KIND, "check").to(Check.class);
    /** Fix change {@code POST /changes/<change-id>/check}. */
    post(CHANGE_KIND, "check").to(Check.class);

    /**
     * Test submit requirement on change {@code POST /changes/<change-id>/check.submit_requirement}.
     */
    post(CHANGE_KIND, "check.submit_requirement").to(CheckSubmitRequirement.class);

    /** List change comments {@code GET /changes/<change-id>/comments}. */
    get(CHANGE_KIND, "comments").to(ListChangeComments.class);

    /** Get custom keyed values of change {@code GET /changes/<change-id>/custom_keyed_values}. */
    get(CHANGE_KIND, "custom_keyed_values").to(GetCustomKeyedValues.class);
    /**
     * Add/Remove custom keyed values of change {@code POST
     * /changes/<change-id>/custom_keyed_values}.
     */
    post(CHANGE_KIND, "custom_keyed_values").to(PostCustomKeyedValues.class);

    /** Get change details {@code GET /changes/<change-id>/detail}. */
    get(CHANGE_KIND, "detail").to(GetDetail.class);

    /** List draft comments of user on change {@code GET /changes/<change-id>/drafts}. */
    get(CHANGE_KIND, "drafts").to(ListChangeDrafts.class);

    /** List hashtags of change {@code GET /changes/<change-id>/hashtags}. */
    get(CHANGE_KIND, "hashtags").to(GetHashtags.class);
    /** Add/Remove hashtags of change {@code POST /changes/<change-id>/hashtags}. */
    post(CHANGE_KIND, "hashtags").to(PostHashtags.class);

    /** Get branches containing change {@code GET /changes/<change-id>/in}. */
    get(CHANGE_KIND, "in").to(ChangeIncludedIn.class);

    /** Add/Update change in index {@code POST /changes/<change-id>/index}. */
    post(CHANGE_KIND, "index").to(Index.class);

    /** Create MergePatchSet for change {@code POST /changes/<change-id>/merge}. */
    post(CHANGE_KIND, "merge").to(CreateMergePatchSet.class);

    /** Get commit message {@code GET /changes/<change-id>/message}. */
    get(CHANGE_KIND, "message").to(GetMessage.class);
    /** Update commit message {@code PUT /changes/<change-id>/message}. */
    put(CHANGE_KIND, "message").to(PutMessage.class);

    /**
     * Get diff in metadata between revisions of a change {@code GET
     * /changes/<change-id>/meta_diff}.
     */
    get(CHANGE_KIND, "meta_diff").to(GetMetaDiff.class);

    /** Move change {@code POST /changes/<change-id>/move}. */
    post(CHANGE_KIND, "move").to(Move.class);

    /** Create PatchSet from patch {@code POST /changes/<change-id>/patch:apply}. */
    post(CHANGE_KIND, "patch:apply").to(ApplyPatch.class);

    /** Make change private {@code POST /changes/<change-id>/private}. */
    post(CHANGE_KIND, "private").to(PostPrivate.class);
    /** Make change public {@code POST /changes/<change-id>/private.delete}. */
    post(CHANGE_KIND, "private.delete").to(DeletePrivateByPost.class);
    /** Make change public {@code DELETE /changes/<change-id>/private}. */
    delete(CHANGE_KIND, "private").to(DeletePrivate.class);

    /** Check if a change is a pure revert {@code GET /changes/<change-id>/pure_revert}. */
    get(CHANGE_KIND, "pure_revert").to(GetPureRevert.class);

    /** Mark change ready for review {@code POST /changes/<change-id>/ready}. */
    post(CHANGE_KIND, "ready").to(SetReadyForReview.class);

    /** Rebase change {@code POST /changes/<change-id>/rebase}. */
    post(CHANGE_KIND, "rebase").to(Rebase.CurrentRevision.class);
    /** Rebase chain of changes {@code POST /changes/<change-id>/rebase:chain}. */
    post(CHANGE_KIND, "rebase:chain").to(RebaseChain.class);

    /** Restore change {@code POST /changes/<change-id>/restore}. */
    post(CHANGE_KIND, "restore").to(Restore.class);

    /** Create change reverting change {@code POST /changes/<change-id>/revert}. */
    post(CHANGE_KIND, "revert").to(Revert.class);

    /**
     * Create changes reverting changes in submission {@code POST
     * /changes/<change-id>/revert_submission}.
     */
    post(CHANGE_KIND, "revert_submission").to(RevertSubmission.class);

    /** Get robot commments of change {@code GET /changes/<change-id>/robotcomments}. */
    get(CHANGE_KIND, "robotcomments").to(ListChangeRobotComments.class);

    /** Submit change {@code POST /changes/<change-id>/submit}. */
    post(CHANGE_KIND, "submit").to(Submit.CurrentRevision.class);

    /**
     * Get changes submitted together with change {@code GET
     * /changes/<change-id>/submitted_together}.
     */
    get(CHANGE_KIND, "submitted_together").to(SubmittedTogether.class);

    /** Get suggested reviewers for change {@code GET /changes/<change-id>/suggest_reviewers}. */
    get(CHANGE_KIND, "suggest_reviewers").to(SuggestChangeReviewers.class);

    /** Delete change topic {@code DELETE /changes/<change-id>/topic}. */
    delete(CHANGE_KIND, "topic").to(PutTopic.class);
    /** Get change topic {@code GET /changes/<change-id>/topic}. */
    get(CHANGE_KIND, "topic").to(GetTopic.class);
    /** Set change topic {@code PUT /changes/<change-id>/topic}. */
    put(CHANGE_KIND, "topic").to(PutTopic.class);

    /** Get validation options for change {@code GET /changes/<change-id>/validation-options}. */
    get(CHANGE_KIND, "validation-options").to(GetValidationOptions.class);

    /** Apply WIP Status to change {@code POST /changes/<change-id>/wip}. */
    post(CHANGE_KIND, "wip").to(SetWorkInProgress.class);

    /** Module for {@code/changes/<change-id>/attention}. */
    install(new ChangeAttentionSetRestApiModule());
    /** Module for {@code /changes/<change-id>/edit}. */
    install(new ChangeEditRestApiModule());
    /** Module for {@code /changes/<change-id>/flows}. */
    install(new ChangeFlowsRestApiModule());
    /** Module for {@code /changes/<change-id>/messages}. */
    install(new ChangeMessagesRestApiModule());
    /** Module for {@code /changes/<change-id>/reviewers}. */
    install(new ChangeReviewersRestApiModule());
    /** Module for {@code /changes/<change-id>/revisions}. */
    install(new ChangeRevisionsRestApiModule());
  }
}
