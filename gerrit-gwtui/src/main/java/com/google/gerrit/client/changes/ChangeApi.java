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

import com.google.gerrit.client.changes.ChangeInfo.EditInfo;
import com.google.gerrit.client.changes.ChangeInfo.IncludedInInfo;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * changes.
 */
public class ChangeApi {
  /** Abandon the change, ending its review. */
  public static void abandon(int id, String msg, AsyncCallback<ChangeInfo> cb) {
    Input input = Input.create();
    input.message(emptyToNull(msg));
    call(id, "abandon").post(input, cb);
  }

  /** Create a new change. */
  public static void createChange(String project, String branch,
      String subject, String base, AsyncCallback<ChangeInfo> cb) {
    CreateChangeInput input = CreateChangeInput.create();
    input.project(emptyToNull(project));
    input.branch(emptyToNull(branch));
    input.subject(emptyToNull(subject));
    input.base_change(emptyToNull(base));

    new RestApi("/changes/").post(input, cb);
  }

  /** Restore a previously abandoned change to be open again. */
  public static void restore(int id, String msg, AsyncCallback<ChangeInfo> cb) {
    Input input = Input.create();
    input.message(emptyToNull(msg));
    call(id, "restore").post(input, cb);
  }

  /** Create a new change that reverts the delta caused by this change. */
  public static void revert(int id, String msg, AsyncCallback<ChangeInfo> cb) {
    Input input = Input.create();
    input.message(emptyToNull(msg));
    call(id, "revert").post(input, cb);
  }

  /** Update the topic of a change. */
  public static void topic(int id, String topic, AsyncCallback<String> cb) {
    RestApi call = call(id, "topic");
    topic = emptyToNull(topic);
    if (topic != null) {
      Input input = Input.create();
      input.topic(topic);
      call.put(input, NativeString.unwrap(cb));
    } else {
      call.delete(NativeString.unwrap(cb));
    }
  }

  public static void detail(int id, AsyncCallback<ChangeInfo> cb) {
    detail(id).get(cb);
  }

  public static RestApi detail(int id) {
    return call(id, "detail");
  }

  public static void edit(int id, AsyncCallback<EditInfo> cb) {
    edit(id).get(cb);
  }

  public static void editWithFiles(int id, AsyncCallback<EditInfo> cb) {
    edit(id).addParameter("list", true).get(cb);
  }

  public static RestApi edit(int id) {
    return change(id).view("edit");
  }

  public static RestApi editWithCommands(int id) {
    return edit(id).addParameter("download-commands", true);
  }

  public static void includedIn(int id, AsyncCallback<IncludedInInfo> cb) {
    call(id, "in").get(cb);
  }

  public static RestApi revision(int id, String revision) {
    return change(id).view("revisions").id(revision);
  }

  public static RestApi revision(PatchSet.Id id) {
    int cn = id.getParentKey().get();
    String revision = RevisionInfoCache.get(id);
    if (revision != null) {
      return revision(cn, revision);
    }
    return change(cn).view("revisions").id(id.get());
  }

  public static RestApi reviewers(int id) {
    return change(id).view("reviewers");
  }

  public static RestApi suggestReviewers(int id, String q, int n) {
    return change(id).view("suggest_reviewers")
        .addParameter("q", q)
        .addParameter("n", n);
  }

  public static RestApi reviewer(int id, int reviewer) {
    return change(id).view("reviewers").id(reviewer);
  }

  public static RestApi reviewer(int id, String reviewer) {
    return change(id).view("reviewers").id(reviewer);
  }

  /** Submit a specific revision of a change. */
  public static void cherrypick(int id, String commit, String destination, String message, AsyncCallback<ChangeInfo> cb) {
    CherryPickInput cherryPickInput = CherryPickInput.create();
    cherryPickInput.setMessage(message);
    cherryPickInput.setDestination(destination);
    call(id, commit, "cherrypick").post(cherryPickInput, cb);
  }

  /** Edit commit message for specific revision of a change. */
  public static void message(int id, String commit, String message,
      AsyncCallback<JavaScriptObject> cb) {
    CherryPickInput input = CherryPickInput.create();
    input.setMessage(message);
    call(id, commit, "message").post(input, cb);
  }

  /** Submit a specific revision of a change. */
  public static void submit(int id, String commit, AsyncCallback<SubmitInfo> cb) {
    SubmitInput in = SubmitInput.create();
    in.wait_for_merge(true);
    call(id, commit, "submit").post(in, cb);
  }

  /** Publish a specific revision of a draft change. */
  public static void publish(int id, String commit, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    call(id, commit, "publish").post(in, cb);
  }

  /** Delete a specific draft change. */
  public static void deleteChange(int id, AsyncCallback<JavaScriptObject> cb) {
    change(id).delete(cb);
  }

  /** Delete a specific draft patch set. */
  public static void deleteRevision(int id, String commit, AsyncCallback<JavaScriptObject> cb) {
    revision(id, commit).delete(cb);
  }

  /** Delete change edit. */
  public static void deleteEdit(int id, AsyncCallback<JavaScriptObject> cb) {
    edit(id).delete(cb);
  }

  /** Publish change edit. */
  public static void publishEdit(int id, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    change(id).view("publish_edit").post(in, cb);
  }

  /** Rebase change edit on latest patch set. */
  public static void rebaseEdit(int id, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    change(id).view("rebase_edit").post(in, cb);
  }

  /** Rebase a revision onto the branch tip. */
  public static void rebase(int id, String commit, AsyncCallback<ChangeInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    call(id, commit, "rebase").post(in, cb);
  }

  private static class Input extends JavaScriptObject {
    final native void topic(String t) /*-{ if(t)this.topic=t; }-*/;
    final native void message(String m) /*-{ if(m)this.message=m; }-*/;

    static Input create() {
      return (Input) createObject();
    }

    protected Input() {
    }
  }

  private static class CreateChangeInput extends JavaScriptObject {
    static CreateChangeInput create() {
      return (CreateChangeInput) createObject();
    }

    public final native void branch(String b) /*-{ if(b)this.branch=b; }-*/;
    public final native void project(String p) /*-{ if(p)this.project=p; }-*/;
    public final native void subject(String s) /*-{ if(s)this.subject=s; }-*/;
    public final native void base_change(String b) /*-{ if(b)this.base_change=b; }-*/;

    protected CreateChangeInput() {
    }
  }

  private static class CherryPickInput extends JavaScriptObject {
    static CherryPickInput create() {
      return (CherryPickInput) createObject();
    }
    final native void setDestination(String d) /*-{ this.destination = d; }-*/;
    final native void setMessage(String m) /*-{ this.message = m; }-*/;

    protected CherryPickInput() {
    }
  }

  private static class SubmitInput extends JavaScriptObject {
    final native void wait_for_merge(boolean b) /*-{ this.wait_for_merge=b; }-*/;

    static SubmitInput create() {
      return (SubmitInput) createObject();
    }

    protected SubmitInput() {
    }
  }

  private static RestApi call(int id, String action) {
    return change(id).view(action);
  }

  private static RestApi call(int id, String commit, String action) {
    return change(id).view("revisions").id(commit).view(action);
  }

  public static RestApi change(int id) {
    // TODO Switch to triplet project~branch~id format in URI.
    return new RestApi("/changes/").id(String.valueOf(id));
  }

  public static String emptyToNull(String str) {
    return str == null || str.isEmpty() ? null : str;
  }
}
