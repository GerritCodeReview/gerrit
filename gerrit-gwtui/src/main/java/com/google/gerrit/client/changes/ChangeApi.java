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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.CommitInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.IncludedInInfo;
import com.google.gerrit.client.rpc.CallbackGroup.Callback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.rpc.AsyncCallback;

/** A collection of static methods which work on the Gerrit REST API for specific changes. */
public class ChangeApi {
  /** Abandon the change, ending its review. */
  public static void abandon(
      @Nullable String project, int id, String msg, AsyncCallback<ChangeInfo> cb) {
    MessageInput input = MessageInput.create();
    input.message(emptyToNull(msg));
    call(project, id, "abandon").post(input, cb);
  }

  /**
   * Create a new change.
   *
   * <p>The new change is created as DRAFT unless the draft workflow is disabled by
   * `change.allowDrafts = false` in the configuration, in which case the new change is created as
   * NEW.
   */
  public static void createChange(
      String project,
      String branch,
      String topic,
      String subject,
      String base,
      AsyncCallback<ChangeInfo> cb) {
    CreateChangeInput input = CreateChangeInput.create();
    input.project(emptyToNull(project));
    input.branch(emptyToNull(branch));
    input.topic(emptyToNull(topic));
    input.subject(emptyToNull(subject));
    input.baseChange(emptyToNull(base));

    if (Gerrit.info().change().allowDrafts()) {
      input.status(Change.Status.DRAFT.toString());
    }

    new RestApi("/changes/").post(input, cb);
  }

  /** Restore a previously abandoned change to be open again. */
  public static void restore(
      @Nullable String project, int id, String msg, AsyncCallback<ChangeInfo> cb) {
    MessageInput input = MessageInput.create();
    input.message(emptyToNull(msg));
    call(project, id, "restore").post(input, cb);
  }

  /** Create a new change that reverts the delta caused by this change. */
  public static void revert(
      @Nullable String project, int id, String msg, AsyncCallback<ChangeInfo> cb) {
    MessageInput input = MessageInput.create();
    input.message(emptyToNull(msg));
    call(project, id, "revert").post(input, cb);
  }

  /** Update the topic of a change. */
  public static void topic(
      @Nullable String project, int id, String topic, AsyncCallback<String> cb) {
    RestApi call = call(project, id, "topic");
    topic = emptyToNull(topic);
    if (topic != null) {
      TopicInput input = TopicInput.create();
      input.topic(topic);
      call.put(input, NativeString.unwrap(cb));
    } else {
      call.delete(NativeString.unwrap(cb));
    }
  }

  public static void detail(@Nullable String project, int id, AsyncCallback<ChangeInfo> cb) {
    detail(project, id).get(cb);
  }

  public static RestApi detail(@Nullable String project, int id) {
    return call(project, id, "detail");
  }

  public static RestApi blame(@Nullable String project, PatchSet.Id id, String path, boolean base) {
    return revision(project, id).view("files").id(path).view("blame").addParameter("base", base);
  }

  public static RestApi actions(@Nullable String project, int id, String revision) {
    if (revision == null || revision.equals("")) {
      revision = "current";
    }
    return call(project, id, revision, "actions");
  }

  public static void deleteAssignee(
      @Nullable String project, int id, AsyncCallback<AccountInfo> cb) {
    change(project, id).view("assignee").delete(cb);
  }

  public static void setAssignee(
      @Nullable String project, int id, String user, AsyncCallback<AccountInfo> cb) {
    AssigneeInput input = AssigneeInput.create();
    input.assignee(user);
    change(project, id).view("assignee").put(input, cb);
  }

  public static void markPrivate(
      @Nullable String project, int id, AsyncCallback<JavaScriptObject> cb) {
    change(project, id).view("private").post(PrivateInput.create(), cb);
  }

  public static void unmarkPrivate(
      @Nullable String project, int id, AsyncCallback<JavaScriptObject> cb) {
    change(project, id).view("private.delete").post(PrivateInput.create(), cb);
  }

  public static RestApi comments(@Nullable String project, int id) {
    return call(project, id, "comments");
  }

  public static RestApi drafts(@Nullable String project, int id) {
    return call(project, id, "drafts");
  }

  public static void edit(@Nullable String project, int id, AsyncCallback<EditInfo> cb) {
    edit(project, id).get(cb);
  }

  public static void editWithFiles(@Nullable String project, int id, AsyncCallback<EditInfo> cb) {
    edit(project, id).addParameterTrue("list").get(cb);
  }

  public static RestApi edit(@Nullable String project, int id) {
    return change(project, id).view("edit");
  }

  public static RestApi editWithCommands(@Nullable String project, int id) {
    return edit(project, id).addParameterTrue("download-commands");
  }

  public static void includedIn(
      @Nullable String project, int id, AsyncCallback<IncludedInInfo> cb) {
    call(project, id, "in").get(cb);
  }

  public static RestApi revision(@Nullable String project, int id, String revision) {
    return change(project, id).view("revisions").id(revision);
  }

  public static RestApi revision(@Nullable String project, PatchSet.Id id) {
    int cn = id.getParentKey().get();
    String revision = RevisionInfoCache.get(id);
    if (revision != null) {
      return revision(project, cn, revision);
    }
    return change(project, cn).view("revisions").id(id.get());
  }

  public static RestApi reviewers(@Nullable String project, int id) {
    return change(project, id).view("reviewers");
  }

  public static RestApi suggestReviewers(
      @Nullable String project, int id, String q, int n, boolean e) {
    RestApi api =
        change(project, id).view("suggest_reviewers").addParameter("n", n).addParameter("e", e);
    if (q != null) {
      api.addParameter("q", q);
    }
    return api;
  }

  public static RestApi vote(@Nullable String project, int id, int reviewer, String vote) {
    return reviewer(project, id, reviewer).view("votes").id(vote);
  }

  public static RestApi reviewer(@Nullable String project, int id, int reviewer) {
    return change(project, id).view("reviewers").id(reviewer);
  }

  public static RestApi reviewer(@Nullable String project, int id, String reviewer) {
    return change(project, id).view("reviewers").id(reviewer);
  }

  public static RestApi hashtags(@Nullable String project, int changeId) {
    return change(project, changeId).view("hashtags");
  }

  public static RestApi hashtag(@Nullable String project, int changeId, String hashtag) {
    return change(project, changeId).view("hashtags").id(hashtag);
  }

  /** Submit a specific revision of a change. */
  public static void cherrypick(
      int id, String commit, String destination, String message, AsyncCallback<ChangeInfo> cb) {
    CherryPickInput cherryPickInput = CherryPickInput.create();
    cherryPickInput.setMessage(message);
    cherryPickInput.setDestination(destination);
    call(commit, id, "cherrypick").post(cherryPickInput, cb);
  }

  /** Edit commit message for specific revision of a change. */
  public static void message(
      @Nullable String project,
      int id,
      String commit,
      String message,
      AsyncCallback<JavaScriptObject> cb) {
    CherryPickInput input = CherryPickInput.create();
    input.setMessage(message);
    call(project, id, commit, "message").post(input, cb);
  }

  /** Submit a specific revision of a change. */
  public static void submit(
      @Nullable String project, int id, String commit, AsyncCallback<SubmitInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    call(project, id, commit, "submit").post(in, cb);
  }

  /** Publish a specific revision of a draft change. */
  public static void publish(
      @Nullable String project, int id, String commit, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    call(project, id, commit, "publish").post(in, cb);
  }

  /** Delete a specific draft change. */
  public static void deleteChange(
      @Nullable String project, int id, AsyncCallback<JavaScriptObject> cb) {
    change(project, id).delete(cb);
  }

  /** Delete a specific draft patch set. */
  public static void deleteRevision(
      @Nullable String project, int id, String commit, AsyncCallback<JavaScriptObject> cb) {
    revision(project, id, commit).delete(cb);
  }

  /** Delete change edit. */
  public static void deleteEdit(
      @Nullable String project, int id, AsyncCallback<JavaScriptObject> cb) {
    edit(project, id).delete(cb);
  }

  /** Publish change edit. */
  public static void publishEdit(
      @Nullable String project, int id, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    change(project, id).view("edit:publish").post(in, cb);
  }

  /** Rebase change edit on latest patch set. */
  public static void rebaseEdit(
      @Nullable String project, int id, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    change(project, id).view("edit:rebase").post(in, cb);
  }

  /** Rebase a revision onto the branch tip or another change. */
  public static void rebase(
      @Nullable String project, int id, String commit, String base, AsyncCallback<ChangeInfo> cb) {
    RebaseInput rebaseInput = RebaseInput.create();
    rebaseInput.setBase(base);
    call(project, id, commit, "rebase").post(rebaseInput, cb);
  }

  private static class MessageInput extends JavaScriptObject {
    final native void message(String m) /*-{ if(m)this.message=m; }-*/;

    static MessageInput create() {
      return (MessageInput) createObject();
    }

    protected MessageInput() {}
  }

  private static class AssigneeInput extends JavaScriptObject {
    final native void assignee(String a) /*-{ if(a)this.assignee=a; }-*/;

    static AssigneeInput create() {
      return (AssigneeInput) createObject();
    }

    protected AssigneeInput() {}
  }

  private static class TopicInput extends JavaScriptObject {
    final native void topic(String t) /*-{ if(t)this.topic=t; }-*/;

    static TopicInput create() {
      return (TopicInput) createObject();
    }

    protected TopicInput() {}
  }

  private static class CreateChangeInput extends JavaScriptObject {
    static CreateChangeInput create() {
      return (CreateChangeInput) createObject();
    }

    public final native void branch(String b) /*-{ if(b)this.branch=b; }-*/;

    public final native void topic(String t) /*-{ if(t)this.topic=t; }-*/;

    public final native void project(String p) /*-{ if(p)this.project=p; }-*/;

    public final native void subject(String s) /*-{ if(s)this.subject=s; }-*/;

    public final native void status(String s) /*-{ if(s)this.status=s; }-*/;

    public final native void baseChange(String b) /*-{ if(b)this.base_change=b; }-*/;

    protected CreateChangeInput() {}
  }

  private static class CherryPickInput extends JavaScriptObject {
    static CherryPickInput create() {
      return (CherryPickInput) createObject();
    }

    final native void setDestination(String d) /*-{ this.destination = d; }-*/;

    final native void setMessage(String m) /*-{ this.message = m; }-*/;

    protected CherryPickInput() {}
  }

  private static class PrivateInput extends JavaScriptObject {
    static PrivateInput create() {
      return (PrivateInput) createObject();
    }

    final native void setMessage(String m) /*-{ this.message = m; }-*/;

    protected PrivateInput() {}
  }

  private static class RebaseInput extends JavaScriptObject {
    final native void setBase(String b) /*-{ this.base = b; }-*/;

    static RebaseInput create() {
      return (RebaseInput) createObject();
    }

    protected RebaseInput() {}
  }

  private static RestApi call(@Nullable String project, int id, String action) {
    return change(project, id).view(action);
  }

  private static RestApi call(@Nullable String project, int id, String commit, String action) {
    return change(project, id).view("revisions").id(commit).view(action);
  }

  public static RestApi change(@Nullable String project, int id) {
    if (project == null) {
      return new RestApi("/changes/").id(String.valueOf(id));
    } else {
      return new RestApi("/changes/").id(project, id);
    }
  }

  public static String emptyToNull(String str) {
    return str == null || str.isEmpty() ? null : str;
  }

  public static void commitWithLinks(
      @Nullable String project, int changeId, String revision, Callback<CommitInfo> callback) {
    revision(project, changeId, revision).view("commit").addParameterTrue("links").get(callback);
  }
}
