// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.ChangeSet;
import com.google.gerrit.server.git.MergeSuperSet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class SubmittedTogether implements RestReadView<RevisionResource> {
  private final Provider<ReviewDb> dbProvider;
  private final MergeSuperSet mergeSuperSet;

  @Inject
  SubmittedTogether(Provider<ReviewDb> dbProvider,
      MergeSuperSet mergeSuperSet) {
    this.dbProvider = dbProvider;
    this.mergeSuperSet = mergeSuperSet;
  }

  @Override
  public Object apply(RevisionResource resource)
      throws AuthException, BadRequestException,
      ResourceConflictException, Exception {

    SubmittedTogetherInfo submittedTogether = new SubmittedTogetherInfo();
    try {
      // TODO: think of return type, should be easily displayable on client side,
      // i.e. contain submittable, commit short message, project,branch
      ChangeSet cs = mergeSuperSet.completeChangeSet(dbProvider.get(),
          ChangeSet.create(resource.getChange()));

      List<ChangeAndCommit> result = new ArrayList<>(cs.ids().size());

      for (Change.Id id : cs.ids()) {
        result.add(getChangeAndCommit(dbProvider.get(), id, resource));
      }


      submittedTogether.changes = result;
    } catch (OrmException | IOException e) {

    }
    return submittedTogether;
  }

  private ChangeAndCommit getChangeAndCommit(ReviewDb db, Change.Id id,
      RevisionResource resource)
      throws OrmException {
    db.patchSets().byChange(id);
    Change c = db.changes().get(id);
    ChangeAndCommit ret = new ChangeAndCommit(c);
        ret.project = c.getDest().getParentKey().get();
    ret.branch = c.getDest().getShortName();

    //"id":"gerrit~master~I089a5cf49a7b866ab825a8009f81114fefbeae62",
    //"project":"gerrit",
    //"branch":"master",
    //"labels"
    //"mergeable"
    //JsArray<ChangeInfo>
    // CURRENT_REVISION,
    // CURRENT_COMMIT,
    // DETAILED_LABELS,
    // LABELS

    return ret;
  }

  /*
   *   private Map<String, Collection<String>> permittedLabels(ChangeControl ctl, ChangeData cd)
      throws OrmException {
    if (ctl == null) {
      return null;
    }

    LabelTypes labelTypes = ctl.getLabelTypes();
    SetMultimap<String, String> permitted = LinkedHashMultimap.create();
    for (SubmitRecord rec : submitRecords(cd)) {
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label r : rec.labels) {
        LabelType type = labelTypes.byLabel(r.label);
        if (type == null) {
          continue;
        }
        PermissionRange range = ctl.getRange(Permission.forLabel(r.label));
        for (LabelValue v : type.getValues()) {
          if (range.contains(v.getValue())) {
            permitted.put(r.label, v.formatValue());
          }
        }
      }
    }
    List<String> toClear =
      Lists.newArrayListWithCapacity(permitted.keySet().size());
    for (Map.Entry<String, Collection<String>> e
        : permitted.asMap().entrySet()) {
      if (isOnlyZero(e.getValue())) {
        toClear.add(e.getKey());
      }
    }
    for (String label : toClear) {
      permitted.removeAll(label);
    }
    return permitted.asMap();
  }*/

  public static class ChangeAndCommit {
    public String changeId;
    public CommitInfo commit;
    public Integer _changeNumber;
    public Integer _revisionNumber;
    public Integer _currentRevisionNumber;
    public String status;
    public String project;
    public String branch;

    ChangeAndCommit(Change change) {
      if (change != null) {
        changeId = change.getKey().get();
        _changeNumber = change.getChangeId();
        _revisionNumber = ps != null ? ps.getPatchSetId() : null;
        PatchSet.Id curr = change.currentPatchSetId();
        _currentRevisionNumber = curr != null ? curr.get() : null;
        status = change.getStatus().asChangeStatus().toString();
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("changeId", changeId)
          .add("commit", toString(commit))
          .add("_changeNumber", _changeNumber)
          .add("_revisionNumber", _revisionNumber)
          .add("_currentRevisionNumber", _currentRevisionNumber)
          .add("status", status)
          .toString();
    }
    private static String toString(CommitInfo commit) {
      return MoreObjects.toStringHelper(commit)
        .add("commit", commit.commit)
        .add("parent", commit.parents)
        .add("author", commit.author)
        .add("committer", commit.committer)
        .add("subject", commit.subject)
        .add("message", commit.message)
        .add("webLinks", commit.webLinks)
        .toString();
    }
  }

  public static class SubmittedTogetherInfo {
    public List<ChangeAndCommit> changes;
  }
}
