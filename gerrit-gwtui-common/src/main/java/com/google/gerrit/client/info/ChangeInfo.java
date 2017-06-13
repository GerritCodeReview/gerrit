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

package com.google.gerrit.client.info;

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ChangeInfo extends JavaScriptObject {
  public final void init() {
    if (allLabels() != null) {
      allLabels().copyKeysIntoChildren("_name");
    }
  }

  public final Project.NameKey projectNameKey() {
    return new Project.NameKey(project());
  }

  public final Change.Id legacyId() {
    return new Change.Id(_number());
  }

  public final Timestamp created() {
    Timestamp ts = _getCts();
    if (ts == null) {
      ts = JavaSqlTimestamp_JsonSerializer.parseTimestamp(createdRaw());
      _setCts(ts);
    }
    return ts;
  }

  public final boolean hasEditBasedOnCurrentPatchSet() {
    JsArray<RevisionInfo> revList = revisions().values();
    RevisionInfo.sortRevisionInfoByNumber(revList);
    return revList.get(revList.length() - 1).isEdit();
  }

  private native Timestamp _getCts() /*-{ return this._cts; }-*/;

  private native void _setCts(Timestamp ts) /*-{ this._cts = ts; }-*/;

  public final Timestamp updated() {
    return JavaSqlTimestamp_JsonSerializer.parseTimestamp(updatedRaw());
  }

  public final Timestamp submitted() {
    return JavaSqlTimestamp_JsonSerializer.parseTimestamp(submittedRaw());
  }

  public final String idAbbreviated() {
    return new Change.Key(changeId()).abbreviate();
  }

  public final Change.Status status() {
    return Change.Status.valueOf(statusRaw());
  }

  public final Set<String> labels() {
    return allLabels().keySet();
  }

  public final Set<Integer> removableReviewerIds() {
    Set<Integer> removable = new HashSet<>();
    if (removableReviewers() != null) {
      for (AccountInfo a : Natives.asList(removableReviewers())) {
        removable.add(a._accountId());
      }
    }
    return removable;
  }

  public final native String id() /*-{ return this.id; }-*/;

  public final native String project() /*-{ return this.project; }-*/;

  public final native String branch() /*-{ return this.branch; }-*/;

  public final native String topic() /*-{ return this.topic; }-*/;

  public final native String changeId() /*-{ return this.change_id; }-*/;

  public final native boolean mergeable() /*-{ return this.mergeable ? true : false; }-*/;

  public final native int insertions() /*-{ return this.insertions; }-*/;

  public final native int deletions() /*-{ return this.deletions; }-*/;

  private native String statusRaw() /*-{ return this.status; }-*/;

  public final native String subject() /*-{ return this.subject; }-*/;

  public final native AccountInfo owner() /*-{ return this.owner; }-*/;

  public final native AccountInfo assignee() /*-{ return this.assignee; }-*/;

  private native String createdRaw() /*-{ return this.created; }-*/;

  private native String updatedRaw() /*-{ return this.updated; }-*/;

  private native String submittedRaw() /*-{ return this.submitted; }-*/;

  public final native boolean starred() /*-{ return this.starred ? true : false; }-*/;

  public final native boolean reviewed() /*-{ return this.reviewed ? true : false; }-*/;

  public final native NativeMap<LabelInfo> allLabels() /*-{ return this.labels; }-*/;

  public final native LabelInfo label(String n) /*-{ return this.labels[n]; }-*/;

  public final native String currentRevision() /*-{ return this.current_revision; }-*/;

  public final native void setCurrentRevision(String r) /*-{ this.current_revision = r; }-*/;

  public final native NativeMap<RevisionInfo> revisions() /*-{ return this.revisions; }-*/;

  public final native RevisionInfo revision(String n) /*-{ return this.revisions[n]; }-*/;

  public final native JsArray<MessageInfo> messages() /*-{ return this.messages; }-*/;

  public final native void setEdit(EditInfo edit) /*-{ this.edit = edit; }-*/;

  public final native EditInfo edit() /*-{ return this.edit; }-*/;

  public final native boolean hasEdit() /*-{ return this.hasOwnProperty('edit') }-*/;

  public final native JsArrayString hashtags() /*-{ return this.hashtags; }-*/;

  public final native boolean hasPermittedLabels()
      /*-{ return this.hasOwnProperty('permitted_labels') }-*/ ;

  public final native NativeMap<JsArrayString> permittedLabels()
      /*-{ return this.permitted_labels; }-*/ ;

  public final native JsArrayString permittedValues(String n)
      /*-{ return this.permitted_labels[n]; }-*/ ;

  public final native JsArray<AccountInfo> removableReviewers()
      /*-{ return this.removable_reviewers; }-*/ ;

  private native NativeMap<JsArray<AccountInfo>> _reviewers() /*-{ return this.reviewers; }-*/;

  public final Map<ReviewerState, List<AccountInfo>> reviewers() {
    NativeMap<JsArray<AccountInfo>> reviewers = _reviewers();
    Map<ReviewerState, List<AccountInfo>> result = new HashMap<>();
    for (String k : reviewers.keySet()) {
      ReviewerState state = ReviewerState.valueOf(k.toUpperCase());
      List<AccountInfo> accounts = result.get(state);
      if (accounts == null) {
        accounts = new ArrayList<>();
        result.put(state, accounts);
      }
      accounts.addAll(Natives.asList(reviewers.get(k)));
    }
    return result;
  }

  public final native boolean hasActions() /*-{ return this.hasOwnProperty('actions') }-*/;

  public final native NativeMap<ActionInfo> actions() /*-{ return this.actions; }-*/;

  public final native int _number() /*-{ return this._number; }-*/;

  public final native boolean _more_changes() /*-{ return this._more_changes ? true : false; }-*/;

  public final SubmitType submitType() {
    String submitType = _submitType();
    if (submitType == null) {
      return null;
    }
    return SubmitType.valueOf(submitType);
  }

  private native String _submitType() /*-{ return this.submit_type; }-*/;

  public final boolean submittable() {
    init();
    return _submittable();
  }

  private native boolean _submittable() /*-{ return this.submittable ? true : false; }-*/;

  /**
   * @return the index of the missing label or -1 if no label is missing, or if more than one label
   *     is missing.
   */
  public final int getMissingLabelIndex() {
    int i = -1;
    int ret = -1;
    List<LabelInfo> labels = Natives.asList(allLabels().values());
    for (LabelInfo label : labels) {
      i++;
      if (!permittedLabels().containsKey(label.name())) {
        continue;
      }

      JsArrayString values = permittedValues(label.name());
      if (values.length() == 0) {
        continue;
      }

      switch (label.status()) {
        case NEED: // Label is required for submit.
          if (ret != -1) {
            // more than one label is missing, so it's unclear which to quick
            // approve, return -1
            return -1;
          }
          ret = i;
          continue;

        case OK: // Label already applied.
        case MAY: // Label is not required.
          continue;

        case REJECT: // Submit cannot happen, do not quick approve.
        case IMPOSSIBLE:
          return -1;
      }
    }
    return ret;
  }

  protected ChangeInfo() {}

  public static class LabelInfo extends JavaScriptObject {
    public final SubmitRecord.Label.Status status() {
      if (approved() != null) {
        return SubmitRecord.Label.Status.OK;
      } else if (rejected() != null) {
        return SubmitRecord.Label.Status.REJECT;
      } else if (optional()) {
        return SubmitRecord.Label.Status.MAY;
      } else {
        return SubmitRecord.Label.Status.NEED;
      }
    }

    public final native String name() /*-{ return this._name; }-*/;

    public final native AccountInfo approved() /*-{ return this.approved; }-*/;

    public final native AccountInfo rejected() /*-{ return this.rejected; }-*/;

    public final native AccountInfo recommended() /*-{ return this.recommended; }-*/;

    public final native AccountInfo disliked() /*-{ return this.disliked; }-*/;

    public final native JsArray<ApprovalInfo> all() /*-{ return this.all; }-*/;

    public final ApprovalInfo forUser(int user) {
      JsArray<ApprovalInfo> all = all();
      for (int i = 0; all != null && i < all.length(); i++) {
        if (all.get(i)._accountId() == user) {
          return all.get(i);
        }
      }
      return null;
    }

    private native NativeMap<NativeString> _values() /*-{ return this.values; }-*/;

    public final Set<String> values() {
      return Natives.keys(_values());
    }

    public final native String valueText(String n) /*-{ return this.values[n]; }-*/;

    public final native boolean optional() /*-{ return this.optional ? true : false; }-*/;

    public final native boolean blocking() /*-{ return this.blocking ? true : false; }-*/;

    public final native short defaultValue() /*-{ return this.default_value; }-*/;

    public final native short _value() /*-{
      if (this.value) return this.value;
      if (this.disliked) return -1;
      if (this.recommended) return 1;
      return 0;
    }-*/;

    public final String maxValue() {
      return LabelValue.formatValue(valueSet().last());
    }

    public final SortedSet<Short> valueSet() {
      SortedSet<Short> values = new TreeSet<>();
      for (String v : values()) {
        values.add(parseValue(v));
      }
      return values;
    }

    public static final short parseValue(String formatted) {
      if (formatted.startsWith("+")) {
        formatted = formatted.substring(1);
      } else if (formatted.startsWith(" ")) {
        formatted = formatted.trim();
      }
      return Short.parseShort(formatted);
    }

    protected LabelInfo() {}
  }

  public static class ApprovalInfo extends AccountInfo {
    public final native boolean hasValue() /*-{ return this.hasOwnProperty('value'); }-*/;

    public final native short value() /*-{ return this.value || 0; }-*/;

    protected ApprovalInfo() {}
  }

  public static class EditInfo extends JavaScriptObject {
    public final native String name() /*-{ return this.name; }-*/;

    public final native String setName(String n) /*-{ this.name = n; }-*/;

    public final native String baseRevision() /*-{ return this.base_revision; }-*/;

    public final native CommitInfo commit() /*-{ return this.commit; }-*/;

    public final native boolean hasActions() /*-{ return this.hasOwnProperty('actions') }-*/;

    public final native NativeMap<ActionInfo> actions() /*-{ return this.actions; }-*/;

    public final native boolean hasFetch() /*-{ return this.hasOwnProperty('fetch') }-*/;

    public final native NativeMap<FetchInfo> fetch() /*-{ return this.fetch; }-*/;

    public final native boolean hasFiles() /*-{ return this.hasOwnProperty('files') }-*/;

    public final native NativeMap<FileInfo> files() /*-{ return this.files; }-*/;

    protected EditInfo() {}
  }

  public static class RevisionInfo extends JavaScriptObject {
    public static RevisionInfo fromEdit(EditInfo edit) {
      RevisionInfo revisionInfo = createObject().cast();
      revisionInfo.takeFromEdit(edit);
      return revisionInfo;
    }

    public static RevisionInfo forParent(int number, CommitInfo commit) {
      RevisionInfo revisionInfo = createObject().cast();
      revisionInfo.takeFromParent(number, commit);
      return revisionInfo;
    }

    private native void takeFromEdit(EditInfo edit) /*-{
      this._number = 0;
      this.name = edit.name;
      this.commit = edit.commit;
      this.edit_base = edit.base_revision;
    }-*/;

    private native void takeFromParent(int number, CommitInfo commit) /*-{
      this._number = number;
      this.commit = commit;
      this.name = this._number;
    }-*/;

    public final native int _number() /*-{ return this._number; }-*/;

    public final native String name() /*-{ return this.name; }-*/;

    public final native boolean draft() /*-{ return this.draft || false; }-*/;

    public final native AccountInfo uploader() /*-{ return this.uploader; }-*/;

    public final native boolean isEdit() /*-{ return this._number == 0; }-*/;

    public final native CommitInfo commit() /*-{ return this.commit; }-*/;

    public final native void setCommit(CommitInfo c) /*-{ this.commit = c; }-*/;

    public final native String editBase() /*-{ return this.edit_base; }-*/;

    public final native boolean hasFiles() /*-{ return this.hasOwnProperty('files') }-*/;

    public final native NativeMap<FileInfo> files() /*-{ return this.files; }-*/;

    public final native boolean hasActions() /*-{ return this.hasOwnProperty('actions') }-*/;

    public final native NativeMap<ActionInfo> actions() /*-{ return this.actions; }-*/;

    public final native boolean hasFetch() /*-{ return this.hasOwnProperty('fetch') }-*/;

    public final native NativeMap<FetchInfo> fetch() /*-{ return this.fetch; }-*/;

    public final native boolean
        hasPushCertificate() /*-{ return this.hasOwnProperty('push_certificate'); }-*/;

    public final native PushCertificateInfo
        pushCertificate() /*-{ return this.push_certificate; }-*/;

    public static void sortRevisionInfoByNumber(JsArray<RevisionInfo> list) {
      final int editParent = findEditParent(list);
      Collections.sort(
          Natives.asList(list),
          new Comparator<RevisionInfo>() {
            @Override
            public int compare(RevisionInfo a, RevisionInfo b) {
              return num(a) - num(b);
            }

            private int num(RevisionInfo r) {
              return !r.isEdit() ? 2 * (r._number() - 1) + 1 : 2 * editParent;
            }
          });
    }

    public static int findEditParent(JsArray<RevisionInfo> list) {
      RevisionInfo r = findEditParentRevision(list);
      return r == null ? -1 : r._number();
    }

    public static RevisionInfo findEditParentRevision(JsArray<RevisionInfo> list) {
      for (int i = 0; i < list.length(); i++) {
        // edit under revisions?
        RevisionInfo editInfo = list.get(i);
        if (editInfo.isEdit()) {
          String parentRevision = editInfo.editBase();
          // find parent
          for (int j = 0; j < list.length(); j++) {
            RevisionInfo parentInfo = list.get(j);
            String name = parentInfo.name();
            if (name.equals(parentRevision)) {
              // found parent pacth set number
              return parentInfo;
            }
          }
        }
      }
      return null;
    }

    public final String id() {
      return PatchSet.Id.toId(_number());
    }

    public final boolean isMerge() {
      return commit().parents().length() > 1;
    }

    protected RevisionInfo() {}
  }

  public static class FetchInfo extends JavaScriptObject {
    public final native String url() /*-{ return this.url }-*/;

    public final native String ref() /*-{ return this.ref }-*/;

    public final native NativeMap<NativeString> commands() /*-{ return this.commands }-*/;

    public final native String command(String n) /*-{ return this.commands[n]; }-*/;

    protected FetchInfo() {}
  }

  public static class CommitInfo extends JavaScriptObject {
    public final native String commit() /*-{ return this.commit; }-*/;

    public final native JsArray<CommitInfo> parents() /*-{ return this.parents; }-*/;

    public final native GitPerson author() /*-{ return this.author; }-*/;

    public final native GitPerson committer() /*-{ return this.committer; }-*/;

    public final native String subject() /*-{ return this.subject; }-*/;

    public final native String message() /*-{ return this.message; }-*/;

    public final native JsArray<WebLinkInfo> webLinks() /*-{ return this.web_links; }-*/;

    protected CommitInfo() {}
  }

  public static class GitPerson extends JavaScriptObject {
    public final native String name() /*-{ return this.name; }-*/;

    public final native String email() /*-{ return this.email; }-*/;

    private native String dateRaw() /*-{ return this.date; }-*/;

    public final Timestamp date() {
      return JavaSqlTimestamp_JsonSerializer.parseTimestamp(dateRaw());
    }

    protected GitPerson() {}
  }

  public static class MessageInfo extends JavaScriptObject {
    public final native AccountInfo author() /*-{ return this.author; }-*/;

    public final native String message() /*-{ return this.message; }-*/;

    public final native int _revisionNumber() /*-{ return this._revision_number || 0; }-*/;

    public final native String tag() /*-{ return this.tag; }-*/;

    private native String dateRaw() /*-{ return this.date; }-*/;

    public final Timestamp date() {
      return JavaSqlTimestamp_JsonSerializer.parseTimestamp(dateRaw());
    }

    protected MessageInfo() {}
  }

  public static class MergeableInfo extends JavaScriptObject {
    public final native String submitType() /*-{ return this.submit_type }-*/;

    public final native boolean mergeable() /*-{ return this.mergeable }-*/;

    protected MergeableInfo() {}
  }

  public static class IncludedInInfo extends JavaScriptObject {
    public final Set<String> externalNames() {
      return Natives.keys(external());
    }

    public final native JsArrayString branches() /*-{ return this.branches; }-*/;

    public final native JsArrayString tags() /*-{ return this.tags; }-*/;

    public final native JsArrayString external(String n) /*-{ return this.external[n]; }-*/;

    private native NativeMap<JsArrayString> external() /*-{ return this.external; }-*/;

    protected IncludedInInfo() {}
  }
}
