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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.actions.ActionInfo;
import com.google.gerrit.client.diff.FileInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwtjsonrpc.client.impl.ser.JavaSqlTimestamp_JsonSerializer;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ChangeInfo extends JavaScriptObject {
  public final void init() {
    if (all_labels() != null) {
      all_labels().copyKeysIntoChildren("_name");
    }
  }

  public final Project.NameKey project_name_key() {
    return new Project.NameKey(project());
  }

  public final Change.Id legacy_id() {
    return new Change.Id(_number());
  }

  public final Timestamp created() {
    Timestamp ts = _get_cts();
    if (ts == null) {
      ts = JavaSqlTimestamp_JsonSerializer.parseTimestamp(createdRaw());
      _set_cts(ts);
    }
    return ts;
  }

  private final native Timestamp _get_cts() /*-{ return this._cts; }-*/;
  private final native void _set_cts(Timestamp ts) /*-{ this._cts = ts; }-*/;

  public final Timestamp updated() {
    return JavaSqlTimestamp_JsonSerializer.parseTimestamp(updatedRaw());
  }

  public final String id_abbreviated() {
    return new Change.Key(change_id()).abbreviate();
  }

  public final Change.Status status() {
    return Change.Status.valueOf(statusRaw());
  }

  public final Set<String> labels() {
    return all_labels().keySet();
  }

  public final native String id() /*-{ return this.id; }-*/;
  public final native String project() /*-{ return this.project; }-*/;
  public final native String branch() /*-{ return this.branch; }-*/;
  public final native String topic() /*-{ return this.topic; }-*/;
  public final native String change_id() /*-{ return this.change_id; }-*/;
  public final native boolean mergeable() /*-{ return this.mergeable; }-*/;
  private final native String statusRaw() /*-{ return this.status; }-*/;
  public final native String subject() /*-{ return this.subject; }-*/;
  public final native AccountInfo owner() /*-{ return this.owner; }-*/;
  private final native String createdRaw() /*-{ return this.created; }-*/;
  private final native String updatedRaw() /*-{ return this.updated; }-*/;
  public final native boolean starred() /*-{ return this.starred ? true : false; }-*/;
  public final native boolean reviewed() /*-{ return this.reviewed ? true : false; }-*/;
  public final native String _sortkey() /*-{ return this._sortkey; }-*/;
  public final native NativeMap<LabelInfo> all_labels() /*-{ return this.labels; }-*/;
  public final native LabelInfo label(String n) /*-{ return this.labels[n]; }-*/;
  public final native String current_revision() /*-{ return this.current_revision; }-*/;
  public final native NativeMap<RevisionInfo> revisions() /*-{ return this.revisions; }-*/;
  public final native RevisionInfo revision(String n) /*-{ return this.revisions[n]; }-*/;
  public final native JsArray<MessageInfo> messages() /*-{ return this.messages; }-*/;

  public final native boolean has_permitted_labels()
  /*-{ return this.hasOwnProperty('permitted_labels') }-*/;
  public final native NativeMap<JsArrayString> permitted_labels()
  /*-{ return this.permitted_labels; }-*/;
  public final native JsArrayString permitted_values(String n)
  /*-{ return this.permitted_labels[n]; }-*/;

  public final native JsArray<AccountInfo> removable_reviewers()
  /*-{ return this.removable_reviewers; }-*/;

  public final native boolean has_actions() /*-{ return this.hasOwnProperty('actions') }-*/;
  public final native NativeMap<ActionInfo> actions() /*-{ return this.actions; }-*/;

  final native int _number() /*-{ return this._number; }-*/;
  final native boolean _more_changes()
  /*-{ return this._more_changes ? true : false; }-*/;

  protected ChangeInfo() {
  }

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
    public final ApprovalInfo for_user(int user) {
      JsArray<ApprovalInfo> all = all();
      for (int i = 0; all != null && i < all.length(); i++) {
        if (all.get(i)._account_id() == user) {
          return all.get(i);
        }
      }
      return null;
    }

    private final native NativeMap<NativeString> _values() /*-{ return this.values; }-*/;
    public final Set<String> values() {
      return Natives.keys(_values());
    }
    public final native String value_text(String n) /*-{ return this.values[n]; }-*/;

    public final native boolean optional() /*-{ return this.optional ? true : false; }-*/;
    final native short _value()
    /*-{
      if (this.value) return this.value;
      if (this.disliked) return -1;
      if (this.recommended) return 1;
      return 0;
    }-*/;

    public final String max_value() {
      return LabelValue.formatValue(value_set().last());
    }

    public final SortedSet<Short> value_set() {
      SortedSet<Short> values = new TreeSet<Short>();
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

    protected LabelInfo() {
    }
  }

  public static class ApprovalInfo extends AccountInfo {
    public final native boolean has_value() /*-{ return this.hasOwnProperty('value'); }-*/;
    public final native short value() /*-{ return this.value || 0; }-*/;

    protected ApprovalInfo() {
    }
  }

  public static class RevisionInfo extends JavaScriptObject {
    public final native int _number() /*-{ return this._number; }-*/;
    public final native String name() /*-{ return this.name; }-*/;
    public final native boolean draft() /*-{ return this.draft || false; }-*/;
    public final native boolean has_draft_comments() /*-{ return this.has_draft_comments || false; }-*/;
    public final native CommitInfo commit() /*-{ return this.commit; }-*/;
    public final native void set_commit(CommitInfo c) /*-{ this.commit = c; }-*/;

    public final native boolean has_files() /*-{ return this.hasOwnProperty('files') }-*/;
    public final native NativeMap<FileInfo> files() /*-{ return this.files; }-*/;

    public final native boolean has_actions() /*-{ return this.hasOwnProperty('actions') }-*/;
    public final native NativeMap<ActionInfo> actions() /*-{ return this.actions; }-*/;

    public final native boolean has_fetch() /*-{ return this.hasOwnProperty('fetch') }-*/;
    public final native NativeMap<FetchInfo> fetch() /*-{ return this.fetch; }-*/;

    public static void sortRevisionInfoByNumber(JsArray<RevisionInfo> list) {
      Collections.sort(Natives.asList(list), new Comparator<RevisionInfo>() {
        @Override
        public int compare(RevisionInfo a, RevisionInfo b) {
          return a._number() - b._number();
        }
      });
    }

    protected RevisionInfo () {
    }
  }

  public static class FetchInfo extends JavaScriptObject {
    public final native String url() /*-{ return this.url }-*/;
    public final native String ref() /*-{ return this.ref }-*/;

    protected FetchInfo () {
    }
  }

  public static class CommitInfo extends JavaScriptObject {
    public final native String commit() /*-{ return this.commit; }-*/;
    public final native JsArray<CommitInfo> parents() /*-{ return this.parents; }-*/;
    public final native GitPerson author() /*-{ return this.author; }-*/;
    public final native GitPerson committer() /*-{ return this.committer; }-*/;
    public final native String subject() /*-{ return this.subject; }-*/;
    public final native String message() /*-{ return this.message; }-*/;

    protected CommitInfo() {
    }
  }

  public static class GitPerson extends JavaScriptObject {
    public final native String name() /*-{ return this.name; }-*/;
    public final native String email() /*-{ return this.email; }-*/;
    private final native String dateRaw() /*-{ return this.date; }-*/;

    public final Timestamp date() {
      return JavaSqlTimestamp_JsonSerializer.parseTimestamp(dateRaw());
    }

    protected GitPerson() {
    }
  }

  public static class MessageInfo extends JavaScriptObject {
    public final native AccountInfo author() /*-{ return this.author; }-*/;
    public final native String message() /*-{ return this.message; }-*/;
    private final native String dateRaw() /*-{ return this.date; }-*/;

    public final Timestamp date() {
      return JavaSqlTimestamp_JsonSerializer.parseTimestamp(dateRaw());
    }

    protected MessageInfo() {
    }
  }

  public static class MergeableInfo extends JavaScriptObject {
    public final native String submit_type() /*-{ return this.submit_type }-*/;
    public final native boolean mergeable() /*-{ return this.mergeable }-*/;

    protected MergeableInfo() {
    }
  }
}
