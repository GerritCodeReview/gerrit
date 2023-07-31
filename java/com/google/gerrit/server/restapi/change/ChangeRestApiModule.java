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

import static com.google.gerrit.server.change.AttentionSetEntryResource.ATTENTION_SET_ENTRY_KIND;
import static com.google.gerrit.server.change.ChangeEditResource.CHANGE_EDIT_KIND;
import static com.google.gerrit.server.change.ChangeMessageResource.CHANGE_MESSAGE_KIND;
import static com.google.gerrit.server.change.ChangeResource.CHANGE_KIND;
import static com.google.gerrit.server.change.DraftCommentResource.DRAFT_COMMENT_KIND;
import static com.google.gerrit.server.change.FileResource.FILE_KIND;
import static com.google.gerrit.server.change.FixResource.FIX_KIND;
import static com.google.gerrit.server.change.HumanCommentResource.COMMENT_KIND;
import static com.google.gerrit.server.change.ReviewerResource.REVIEWER_KIND;
import static com.google.gerrit.server.change.RevisionResource.REVISION_KIND;
import static com.google.gerrit.server.change.RobotCommentResource.ROBOT_COMMENT_KIND;
import static com.google.gerrit.server.change.VoteResource.VOTE_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.change.Reviewed.DeleteReviewed;
import com.google.gerrit.server.restapi.change.Reviewed.PutReviewed;

public class ChangeRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(ChangesCollection.class);
    bind(Comments.class);
    bind(DraftComments.class);
    bind(Files.class);
    bind(Fixes.class);
    bind(Reviewers.class);
    bind(RevisionReviewers.class);
    bind(Revisions.class);
    bind(RobotComments.class);
    bind(Votes.class);

    DynamicMap.mapOf(binder(), ATTENTION_SET_ENTRY_KIND);
    DynamicMap.mapOf(binder(), CHANGE_KIND);
    DynamicMap.mapOf(binder(), CHANGE_EDIT_KIND);
    DynamicMap.mapOf(binder(), CHANGE_MESSAGE_KIND);
    DynamicMap.mapOf(binder(), COMMENT_KIND);
    DynamicMap.mapOf(binder(), DRAFT_COMMENT_KIND);
    DynamicMap.mapOf(binder(), FILE_KIND);
    DynamicMap.mapOf(binder(), FIX_KIND);
    DynamicMap.mapOf(binder(), ROBOT_COMMENT_KIND);
    DynamicMap.mapOf(binder(), REVIEWER_KIND);
    DynamicMap.mapOf(binder(), REVISION_KIND);
    DynamicMap.mapOf(binder(), VOTE_KIND);

    postOnCollection(CHANGE_KIND).to(CreateChange.class);
    delete(CHANGE_KIND).to(DeleteChange.class);
    get(CHANGE_KIND).to(GetChange.class);
    post(CHANGE_KIND, "abandon").to(Abandon.class);

    child(CHANGE_KIND, "attention").to(AttentionSet.class);
    postOnCollection(ATTENTION_SET_ENTRY_KIND).to(AddToAttentionSet.class);
    delete(ATTENTION_SET_ENTRY_KIND).to(RemoveFromAttentionSet.class);
    post(ATTENTION_SET_ENTRY_KIND, "delete").to(RemoveFromAttentionSet.class);

    get(CHANGE_KIND, "check").to(Check.class);
    post(CHANGE_KIND, "check").to(Check.class);
    post(CHANGE_KIND, "check.submit_requirement").to(CheckSubmitRequirement.class);
    get(CHANGE_KIND, "comments").to(ListChangeComments.class);
    get(CHANGE_KIND, "custom_keyed_values").to(GetCustomKeyedValues.class);
    post(CHANGE_KIND, "custom_keyed_values").to(PostCustomKeyedValues.class);
    get(CHANGE_KIND, "detail").to(GetDetail.class);
    get(CHANGE_KIND, "drafts").to(ListChangeDrafts.class);

    child(CHANGE_KIND, "edit").to(ChangeEdits.class);
    create(CHANGE_EDIT_KIND).to(ChangeEdits.Create.class);
    delete(CHANGE_EDIT_KIND).to(ChangeEdits.DeleteContent.class);
    deleteOnCollection(CHANGE_EDIT_KIND).to(DeleteChangeEdit.class);
    deleteMissing(CHANGE_EDIT_KIND).to(ChangeEdits.DeleteFile.class);
    postOnCollection(CHANGE_EDIT_KIND).to(ChangeEdits.Post.class);
    get(CHANGE_EDIT_KIND, "/").to(ChangeEdits.Get.class);
    put(CHANGE_EDIT_KIND, "/").to(ChangeEdits.Put.class);
    get(CHANGE_EDIT_KIND, "meta").to(ChangeEdits.GetMeta.class);

    put(CHANGE_KIND, "edit:message").to(ChangeEdits.EditMessage.class);
    get(CHANGE_KIND, "edit:message").to(ChangeEdits.GetMessage.class);
    post(CHANGE_KIND, "edit:publish").to(PublishChangeEdit.class);
    post(CHANGE_KIND, "edit:rebase").to(RebaseChangeEdit.class);
    get(CHANGE_KIND, "hashtags").to(GetHashtags.class);
    post(CHANGE_KIND, "hashtags").to(PostHashtags.class);
    get(CHANGE_KIND, "in").to(ChangeIncludedIn.class);
    post(CHANGE_KIND, "index").to(Index.class);
    get(CHANGE_KIND, "meta_diff").to(GetMetaDiff.class);
    post(CHANGE_KIND, "merge").to(CreateMergePatchSet.class);
    put(CHANGE_KIND, "message").to(PutMessage.class);

    child(CHANGE_KIND, "messages").to(ChangeMessages.class);
    delete(CHANGE_MESSAGE_KIND).to(DeleteChangeMessage.DefaultDeleteChangeMessage.class);
    get(CHANGE_MESSAGE_KIND).to(GetChangeMessage.class);
    post(CHANGE_MESSAGE_KIND, "delete").to(DeleteChangeMessage.class);

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

    child(CHANGE_KIND, "reviewers").to(Reviewers.class);
    postOnCollection(REVIEWER_KIND).to(PostReviewers.class);
    delete(REVIEWER_KIND).to(DeleteReviewer.class);
    get(REVIEWER_KIND).to(GetReviewer.class);
    post(REVIEWER_KIND, "delete").to(DeleteReviewer.class);
    child(REVIEWER_KIND, "votes").to(Votes.class);

    child(CHANGE_KIND, "revisions").to(Revisions.class);
    get(REVISION_KIND, "actions").to(GetRevisionActions.class);
    get(REVISION_KIND, "archive").to(GetArchive.class);
    post(REVISION_KIND, "cherrypick").to(CherryPick.class);

    child(REVISION_KIND, "comments").to(Comments.class);
    delete(COMMENT_KIND).to(DeleteComment.class);
    get(COMMENT_KIND).to(GetComment.class);
    post(COMMENT_KIND, "delete").to(DeleteComment.class);

    get(REVISION_KIND, "commit").to(GetCommit.class);
    get(REVISION_KIND, "description").to(GetDescription.class);
    put(REVISION_KIND, "description").to(PutDescription.class);

    child(REVISION_KIND, "drafts").to(DraftComments.class);
    put(REVISION_KIND, "drafts").to(CreateDraftComment.class);
    delete(DRAFT_COMMENT_KIND).to(DeleteDraftComment.class);
    get(DRAFT_COMMENT_KIND).to(GetDraftComment.class);
    put(DRAFT_COMMENT_KIND).to(PutDraftComment.class);

    child(REVISION_KIND, "files").to(Files.class);
    get(FILE_KIND, "blame").to(GetBlame.class);
    get(FILE_KIND, "content").to(GetContent.class);
    get(FILE_KIND, "diff").to(GetDiff.class);
    get(FILE_KIND, "download").to(DownloadContent.class);
    put(FILE_KIND, "reviewed").to(PutReviewed.class);
    delete(FILE_KIND, "reviewed").to(DeleteReviewed.class);

    child(REVISION_KIND, "fixes").to(Fixes.class);
    post(FIX_KIND, "apply").to(ApplyStoredFix.class);
    get(FIX_KIND, "preview").to(PreviewFix.Stored.class);

    post(REVISION_KIND, "fix:apply").to(ApplyProvidedFix.class);
    post(REVISION_KIND, "fix:preview").to(PreviewFix.Provided.class);
    get(REVISION_KIND, "mergeable").to(Mergeable.class);
    get(REVISION_KIND, "mergelist").to(GetMergeList.class);
    get(REVISION_KIND, "patch").to(GetPatch.class);
    get(REVISION_KIND, "ported_comments").to(ListPortedComments.class);
    get(REVISION_KIND, "ported_drafts").to(ListPortedDrafts.class);
    post(REVISION_KIND, "rebase").to(Rebase.class);
    get(REVISION_KIND, "related").to(GetRelated.class);
    get(REVISION_KIND, "review").to(GetReview.class);
    post(REVISION_KIND, "review").to(PostReview.class);
    child(REVISION_KIND, "reviewers").to(RevisionReviewers.class);

    child(REVISION_KIND, "robotcomments").to(RobotComments.class);
    get(ROBOT_COMMENT_KIND).to(GetRobotComment.class);

    post(REVISION_KIND, "submit").to(Submit.class);
    get(REVISION_KIND, "submit_type").to(TestSubmitType.Get.class);
    post(REVISION_KIND, "test.submit_rule").to(TestSubmitRule.class);
    post(REVISION_KIND, "test.submit_type").to(TestSubmitType.class);

    get(CHANGE_KIND, "robotcomments").to(ListChangeRobotComments.class);
    delete(CHANGE_KIND, "topic").to(PutTopic.class);
    get(CHANGE_KIND, "topic").to(GetTopic.class);
    put(CHANGE_KIND, "topic").to(PutTopic.class);
    post(CHANGE_KIND, "submit").to(Submit.CurrentRevision.class);
    get(CHANGE_KIND, "submitted_together").to(SubmittedTogether.class);
    get(CHANGE_KIND, "suggest_reviewers").to(SuggestChangeReviewers.class);

    delete(VOTE_KIND).to(DeleteVote.class);
    post(VOTE_KIND, "delete").to(DeleteVote.class);

    post(CHANGE_KIND, "wip").to(SetWorkInProgress.class);
  }
}
