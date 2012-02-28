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

package com.google.gerrit.server.changedetail;

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ReviewParams;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.concurrent.Callable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Review implements Callable<ReviewResult> {

  public interface Factory {
    Review create(ReviewParams params);
  }

  private final ApprovalTypes approvalTypes;
  private final ReviewDb db;
  private final DeleteDraftPatchSet.Factory deleteDraftPatchSetFactory;
  private final AbandonChange.Factory abandonChangeFactory;
  private final PublishComments.Factory publishCommentsFactory;
  private final PublishDraft.Factory publishDraftFactory;
  private final RestoreChange.Factory restoreChangeFactory;
  private final Submit.Factory submitFactory;

  private final ReviewParams params;

  private ProjectControl projectControl;
  private ReviewResult result;

  @Inject
  Review(final ApprovalTypes approvalTypes,
      final ReviewDb db,
      final DeleteDraftPatchSet.Factory deleteDraftPatchSetFactory,
      final AbandonChange.Factory abandonChangeFactory,
      final PublishComments.Factory publishCommentsFactory,
      final PublishDraft.Factory publishDraftFactory,
      final RestoreChange.Factory restoreChangeFactory,
      final Submit.Factory submitFactory,
      @Assisted final ReviewParams params) {
    this.approvalTypes = approvalTypes;
    this.db = db;
    this.deleteDraftPatchSetFactory = deleteDraftPatchSetFactory;
    this.abandonChangeFactory = abandonChangeFactory;
    this.publishCommentsFactory = publishCommentsFactory;
    this.publishDraftFactory = publishDraftFactory;
    this.restoreChangeFactory = restoreChangeFactory;
    this.submitFactory = submitFactory;

    this.params = params;

    this.projectControl = null;
  }

  @Override
  public ReviewResult call()
      throws EmailException, IllegalStateException,
             InvalidChangeOperationException, NoSuchChangeException,
             OrmException {
    result = new ReviewResult();
    try {
      params.validate();
    } catch (ReviewParams.ValidationException e) {
      error(ReviewResult.Error.Type.PARAM_ERROR, e.getMessage());
      return result;
    }

    final JsonArray reviews = params.getObject().getAsJsonArray("reviews");
    for (final JsonElement review : reviews) {
      handleReview(review.getAsJsonObject());
    }
    return result;
  }

  private void handleReview(final JsonObject review)
      throws EmailException, IllegalStateException,
             InvalidChangeOperationException, NoSuchChangeException,
             OrmException {
    // TODO: We should actually be performing a query, but until we have
    //       the ability to get patchsets from a query, we just look up
    //       each token as though it's a patchset identifier.
    final String query = review.get("query").toString();
    final String[] tokens = query.split(" ");
    final String patchSetIdStr;
    if (tokens.length < 1 || tokens.length > 2) {
      error(ReviewResult.Error.Type.PARAM_ERROR,
            "Currently, \"query\" can only handle a single "
            + "project specifier and a patchset identifier.");
    } else if (tokens.length > 1) {
      for (final String token : query.split(" ")) {
        if (token.startsWith("project:")) {
          if (projectControl != null) {
            error(ReviewResult.Error.Type.PARAM_ERROR,
                  "Currently, \"query\" can only handle a single "
                  + "project specifier and a patchset identifier.");
          }
        }
      }
    }
    for (final String token : query.split(" ")) {
      if (!token.startsWith("project:")) {
        for (final PatchSet.Id patchSetId : parsePatchSetId(token)) {
          handlePatchSet(patchSetId, review);
        }
      }
    }
  }

  private void handlePatchSet(final PatchSet.Id patchSetId,
      final JsonObject review)
      throws IllegalStateException, InvalidChangeOperationException,
             NoSuchChangeException, OrmException, EmailException {
    final Change.Id changeId = patchSetId.getParentKey();

    final String action = review.get("action").toString();

    String message = "";
    if (review.has("message")) {
      message = review.get("message").toString();
    }

    final JsonObject labels = review.getAsJsonObject("labels");
    final Set<ApprovalCategoryValue.Id> aps =
        new HashSet<ApprovalCategoryValue.Id>();
    if (labels != null) {
      for (final Map.Entry<String, JsonElement> entry : labels.entrySet()) {
        Short v = Short.valueOf(entry.getValue().toString());
        if (v == null) {
          error(ReviewResult.Error.Type.PARAM_ERROR,
                entry.getValue().toString() + " is not a label value");
          return;
        }
        final ApprovalCategory.Id cid =
            approvalTypes.byLabel(entry.getKey()).getCategory().getId();
        aps.add(new ApprovalCategoryValue.Id(cid, v));
      }
    }

    final boolean forceMessage =
        review.getAsJsonPrimitive("force_message").getAsBoolean();
    publishCommentsFactory.create(patchSetId, message, aps, forceMessage).call();
    if (action.equals("abandon")) {
      final ReviewResult otherResult = abandonChangeFactory.create(
          patchSetId, message).call();
      mergeErrors(otherResult);
    } else if (action.equals("restore")) {
      final ReviewResult otherResult = restoreChangeFactory.create(
          patchSetId, message).call();
      mergeErrors(otherResult);
    } else if (action.equals("submit")) {
      final ReviewResult otherResult = submitFactory.create(patchSetId).call();
      mergeErrors(otherResult);
    } else if (action.equals("publish")) {
      final ReviewResult otherResult = publishDraftFactory.create(patchSetId).call();
      mergeErrors(otherResult);
    } else if (action.equals("delete")) {
      final ReviewResult otherResult =
          deleteDraftPatchSetFactory.create(patchSetId).call();
      mergeErrors(otherResult);
    }
  }

  private Set<PatchSet.Id> parsePatchSetId(final String patchIdentity)
      throws OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      final RevId id = new RevId(patchIdentity);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      final Set<PatchSet.Id> matches = new HashSet<PatchSet.Id>();
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(ps.getId());
        }
      }

      switch (matches.size()) {
        case 1:
          return matches;
        case 0:
          error(ReviewResult.Error.Type.PATCHSET_MATCH_ERROR,
                "\"" + patchIdentity + "\" no such patch set");
          return Collections.emptySet();
        default:
          error(ReviewResult.Error.Type.PATCHSET_MATCH_ERROR,
                "\"" + patchIdentity + "\" matches multiple patch sets");
          return Collections.emptySet();
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      final PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        error(ReviewResult.Error.Type.PATCHSET_MATCH_ERROR,
              "\"" + patchIdentity + "\" is not a valid patch set");
        return Collections.emptySet();
      }
      if (db.patchSets().get(patchSetId) == null) {
        error(ReviewResult.Error.Type.PATCHSET_MATCH_ERROR,
              "\"" + patchIdentity + "\" no such patch set");
        return Collections.emptySet();
      }
      if (projectControl != null) {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          error(ReviewResult.Error.Type.PATCHSET_MATCH_ERROR,
                "change " + change.getId() + " not in project "
                + projectControl.getProject().getName());
          return Collections.emptySet();
        }
      }
      return Collections.singleton(patchSetId);
    }

    error(ReviewResult.Error.Type.PATCHSET_MATCH_ERROR,
          "\"" + patchIdentity + "\" is not a valid patch set");
    return Collections.emptySet();
  }

  private boolean inProject(final Change change) {
    if (projectControl == null) {
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
  }

  private void error(final ReviewResult.Error.Type errType,
                          final String msg) {
    result.addError(new ReviewResult.Error(errType, msg));
  }

  private void mergeErrors(final ReviewResult other) {
    for (final ReviewResult.Error err : other.getErrors()) {
      result.addError(err);
    }
  }

}
