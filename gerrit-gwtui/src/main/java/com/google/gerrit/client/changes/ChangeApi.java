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
      int id, @Nullable String project, String msg, AsyncCallback<ChangeInfo> cb) {
    MessageInput input = MessageInput.create();
    input.message(emptyToNull(msg));
    call(id, project, "abandon").post(input, cb);
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
      int id, @Nullable String project, String msg, AsyncCallback<ChangeInfo> cb) {
    MessageInput input = MessageInput.create();
    input.message(emptyToNull(msg));
    call(id, project, "restore").post(input, cb);
  }

  /** Create a new change that reverts the delta caused by this change. */
  public static void revert(
      int id, @Nullable String project, String msg, AsyncCallback<ChangeInfo> cb) {
    MessageInput input = MessageInput.create();
    input.message(emptyToNull(msg));
    call(id, project, "revert").post(input, cb);
  }

  /** Update the topic of a change. */
  public static void topic(
      int id, @Nullable String project, String topic, AsyncCallback<String> cb) {
    RestApi call = call(id, project, "topic");
    topic = emptyToNull(topic);
    if (topic != null) {
      TopicInput input = TopicInput.create();
      input.topic(topic);
      call.put(input, NativeString.unwrap(cb));
    } else {
      call.delete(NativeString.unwrap(cb));
    }
  }

  public static void detail(int id, @Nullable String project, AsyncCallback<ChangeInfo> cb) {
    detail(id, project).get(cb);
  }

  public static RestApi detail(int id, @Nullable String project) {
    return call(id, project, "detail");
  }

  public static RestApi blame(PatchSet.Id id, @Nullable String project, String path, boolean base) {
    return revision(id, project).view("files").id(path).view("blame").addParameter("base", base);
  }

  public static RestApi actions(int id, @Nullable String project, String revision) {
    if (revision == null || revision.equals("")) {
      revision = "current";
    }
    return call(id, project, revision, "actions");
  }

  public static void deleteAssignee(
      int id, @Nullable String project, AsyncCallback<AccountInfo> cb) {
    change(id, project).view("assignee").delete(cb);
  }

  public static void setAssignee(
      int id, @Nullable String project, String user, AsyncCallback<AccountInfo> cb) {
    AssigneeInput input = AssigneeInput.create();
    input.assignee(user);
    change(id, project).view("assignee").put(input, cb);
  }

  public static void markPrivate(
      int id, @Nullable String project, AsyncCallback<JavaScriptObject> cb) {
    change(id, project).view("private").post(PrivateInput.create(), cb);
  }

  public static void unmarkPrivate(
      int id, @Nullable String project, AsyncCallback<JavaScriptObject> cb) {
    change(id, project).view("private.delete").post(PrivateInput.create(), cb);
  }

  public static RestApi comments(int id, @Nullable String project) {
    return call(id, project, "comments");
  }

  public static RestApi drafts(int id, @Nullable String project) {
    return call(id, project, "drafts");
  }

  public static void edit(int id, @Nullable String project, AsyncCallback<EditInfo> cb) {
    edit(id, project).get(cb);
  }

  public static void editWithFiles(int id, @Nullable String project, AsyncCallback<EditInfo> cb) {
    edit(id, project).addParameterTrue("list").get(cb);
  }

  public static RestApi edit(int id, @Nullable String project) {
    return change(id, project).view("edit");
  }

  public static RestApi editWithCommands(int id, @Nullable String project) {
    return edit(id, project).addParameterTrue("download-commands");
  }

  public static void includedIn(
      int id, @Nullable String project, AsyncCallback<IncludedInInfo> cb) {
    call(id, project, "in").get(cb);
  }

  public static RestApi revision(int id, @Nullable String project, String revision) {
    return change(id, project).view("revisions").id(revision);
  }

  public static RestApi revision(PatchSet.Id id, @Nullable String project) {
    int cn = id.getParentKey().get();
    String revision = RevisionInfoCache.get(id);
    if (revision != null) {
      return revision(cn, project, revision);
    }
    return change(cn, project).view("revisions").id(id.get());
  }

  public static RestApi reviewers(int id, @Nullable String project) {
    return change(id, project).view("reviewers");
  }

  public static RestApi suggestReviewers(
      int id, @Nullable String project, String q, int n, boolean e) {
    RestApi api =
        change(id, project).view("suggest_reviewers").addParameter("n", n).addParameter("e", e);
    if (q != null) {
      api.addParameter("q", q);
    }
    return api;
  }

  public static RestApi vote(int id, @Nullable String project, int reviewer, String vote) {
    return reviewer(id, project, reviewer).view("votes").id(vote);
  }

  public static RestApi reviewer(int id, @Nullable String project, int reviewer) {
    return change(id, project).view("reviewers").id(reviewer);
  }

  public static RestApi reviewer(int id, @Nullable String project, String reviewer) {
    return change(id, project).view("reviewers").id(reviewer);
  }

  public static RestApi hashtags(int changeId, @Nullable String project) {
    return change(changeId, project).view("hashtags");
  }

  public static RestApi hashtag(int changeId, @Nullable String project, String hashtag) {
    return change(changeId, project).view("hashtags").id(hashtag);
  }

  /** Submit a specific revision of a change. */
  public static void cherrypick(
      int id, String commit, String destination, String message, AsyncCallback<ChangeInfo> cb) {
    CherryPickInput cherryPickInput = CherryPickInput.create();
    cherryPickInput.setMessage(message);
    cherryPickInput.setDestination(destination);
    call(id, commit, "cherrypick").post(cherryPickInput, cb);
  }

  /** Edit commit message for specific revision of a change. */
  public static void message(
      int id,
      @Nullable String project,
      String commit,
      String message,
      AsyncCallback<JavaScriptObject> cb) {
    CherryPickInput input = CherryPickInput.create();
    input.setMessage(message);
    call(id, project, commit, "message").post(input, cb);
  }

  /** Submit a specific revision of a change. */
  public static void submit(
      int id, @Nullable String project, String commit, AsyncCallback<SubmitInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    call(id, project, commit, "submit").post(in, cb);
  }

  /** Publish a specific revision of a draft change. */
  public static void publish(
      int id, @Nullable String project, String commit, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    call(id, project, commit, "publish").post(in, cb);
  }

  /** Delete a specific draft change. */
  public static void deleteChange(
      int id, @Nullable String project, AsyncCallback<JavaScriptObject> cb) {
    change(id, project).delete(cb);
  }

  /** Delete a specific draft patch set. */
  public static void deleteRevision(
      int id, @Nullable String project, String commit, AsyncCallback<JavaScriptObject> cb) {
    revision(id, project, commit).delete(cb);
  }

  /** Delete change edit. */
  public static void deleteEdit(
      int id, @Nullable String project, AsyncCallback<JavaScriptObject> cb) {
    edit(id, project).delete(cb);
  }

  /** Publish change edit. */
  public static void publishEdit(
      int id, @Nullable String project, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    change(id, project).view("edit:publish").post(in, cb);
  }

  /** Rebase change edit on latest patch set. */
  public static void rebaseEdit(
      int id, @Nullable String project, AsyncCallback<JavaScriptObject> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    change(id, project).view("edit:rebase").post(in, cb);
  }

  /** Rebase a revision onto the branch tip or another change. */
  public static void rebase(
      int id, @Nullable String project, String commit, String base, AsyncCallback<ChangeInfo> cb) {
    RebaseInput rebaseInput = RebaseInput.create();
    rebaseInput.setBase(base);
    call(id, project, commit, "rebase").post(rebaseInput, cb);
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

  private static RestApi call(int id, @Nullable String project, String action) {
    return change(id, project).view(action);
  }

  private static RestApi call(int id, @Nullable String project, String commit, String action) {
    return change(id, project).view("revisions").id(commit).view(action);
  }

  public static RestApi change(int id, @Nullable String project) {
    if (project == null) {
      return new RestApi("/changes/").id(String.valueOf(id));
    } else {
      return new RestApi("/changes/").id(id, project);
    }
  }

  public static String emptyToNull(String str) {
    return str == null || str.isEmpty() ? null : str;
  }

  public static void commitWithLinks(
      int changeId, @Nullable String project, String revision, Callback<CommitInfo> callback) {
    revision(changeId, project, revision).view("commit").addParameterTrue("links").get(callback);
  }
}
