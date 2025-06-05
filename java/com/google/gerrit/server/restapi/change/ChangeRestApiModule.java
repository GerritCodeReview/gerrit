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

public class ChangeRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(ChangesCollection.class);

    DynamicMap.mapOf(binder(), CHANGE_KIND);

    postOnCollection(CHANGE_KIND).to(CreateChange.class);
    delete(CHANGE_KIND).to(DeleteChange.class);
    get(CHANGE_KIND).to(GetChange.class);
    post(CHANGE_KIND, "abandon").to(Abandon.class);

    get(CHANGE_KIND, "check").to(Check.class);
    post(CHANGE_KIND, "check").to(Check.class);
    post(CHANGE_KIND, "check.submit_requirement").to(CheckSubmitRequirement.class);
    get(CHANGE_KIND, "comments").to(ListChangeComments.class);
    get(CHANGE_KIND, "custom_keyed_values").to(GetCustomKeyedValues.class);
    post(CHANGE_KIND, "custom_keyed_values").to(PostCustomKeyedValues.class);
    get(CHANGE_KIND, "detail").to(GetDetail.class);
    get(CHANGE_KIND, "drafts").to(ListChangeDrafts.class);

    get(CHANGE_KIND, "hashtags").to(GetHashtags.class);
    post(CHANGE_KIND, "hashtags").to(PostHashtags.class);
    get(CHANGE_KIND, "in").to(ChangeIncludedIn.class);
    post(CHANGE_KIND, "index").to(Index.class);
    get(CHANGE_KIND, "meta_diff").to(GetMetaDiff.class);
    post(CHANGE_KIND, "merge").to(CreateMergePatchSet.class);
    get(CHANGE_KIND, "validation-options").to(GetValidationOptions.class);
    get(CHANGE_KIND, "message").to(GetMessage.class);
    put(CHANGE_KIND, "message").to(PutMessage.class);

    post(CHANGE_KIND, "move").to(Move.class);
    post(CHANGE_KIND, "patch:apply").to(ApplyPatch.class);
    post(CHANGE_KIND, "private").to(PostPrivate.class);
    post(CHANGE_KIND, "private.delete").to(DeletePrivateByPost.class);
    delete(CHANGE_KIND, "private").to(DeletePrivate.class);
    get(CHANGE_KIND, "pure_revert").to(GetPureRevert.class);
    post(CHANGE_KIND, "ready").to(SetReadyForReview.class);
    post(CHANGE_KIND, "rebase").to(Rebase.CurrentRevision.class);
    post(CHANGE_KIND, "rebase:chain").to(RebaseChain.class);
    post(CHANGE_KIND, "restore").to(Restore.class);
    post(CHANGE_KIND, "revert").to(Revert.class);
    post(CHANGE_KIND, "revert_submission").to(RevertSubmission.class);

    get(CHANGE_KIND, "robotcomments").to(ListChangeRobotComments.class);
    delete(CHANGE_KIND, "topic").to(PutTopic.class);
    get(CHANGE_KIND, "topic").to(GetTopic.class);
    put(CHANGE_KIND, "topic").to(PutTopic.class);
    post(CHANGE_KIND, "submit").to(Submit.CurrentRevision.class);
    get(CHANGE_KIND, "submitted_together").to(SubmittedTogether.class);
    get(CHANGE_KIND, "suggest_reviewers").to(SuggestChangeReviewers.class);

    post(CHANGE_KIND, "wip").to(SetWorkInProgress.class);

    install(new ChangeAttentionSetRestApiModule());
    install(new ChangeEditRestApiModule());
    install(new ChangeFlowsRestApiModule());
    install(new ChangeMessagesRestApiModule());
    install(new ChangeReviewersRestApiModule());
    install(new ChangeRevisionsRestApiModule());
  }
}
