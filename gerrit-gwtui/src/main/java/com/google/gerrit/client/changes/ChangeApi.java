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

import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
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
    api(id, "abandon").data(input).post(cb);
  }

  /** Restore a previously abandoned change to be open again. */
  public static void restore(int id, String msg, AsyncCallback<ChangeInfo> cb) {
    Input input = Input.create();
    input.message(emptyToNull(msg));
    api(id, "restore").data(input).post(cb);
  }

  /** Create a new change that reverts the delta caused by this change. */
  public static void revert(int id, String msg, AsyncCallback<ChangeInfo> cb) {
    Input input = Input.create();
    input.message(emptyToNull(msg));
    api(id, "revert").data(input).post(cb);
  }

  /** Update the topic of a change. */
  public static void topic(int id, String topic, String msg, AsyncCallback<String> cb) {
    RestApi call = api(id, "topic");
    topic = emptyToNull(topic);
    msg = emptyToNull(msg);
    if (topic != null || msg != null) {
      Input input = Input.create();
      input.topic(topic);
      input.message(msg);
      call.data(input).put(NativeString.unwrap(cb));
    } else {
      call.delete(NativeString.unwrap(cb));
    }
  }

  /** Submit a specific revision of a change. */
  public static void cherrypick(int id, String commit, String destination, String message, AsyncCallback<ChangeInfo> cb) {
    CherryPickInput cherryPickInput = CherryPickInput.create();
    cherryPickInput.setMessage(message);
    cherryPickInput.setDestination(destination);
    api(id, commit, "cherrypick").data(cherryPickInput).post(cb);
  }

  /** Submit a specific revision of a change. */
  public static void submit(int id, String commit, AsyncCallback<SubmitInfo> cb) {
    SubmitInput in = SubmitInput.create();
    in.wait_for_merge(true);
    api(id, commit, "submit").data(in).post(cb);
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

  private static class CherryPickInput extends JavaScriptObject {
    static CherryPickInput create() {
      return (CherryPickInput) createObject();
    }
    final native void setDestination(String d) /*-{ this.destination = d; }-*/;
    final native void setMessage(String m) /*-{ this.message = m; }-*/;

    protected CherryPickInput() {
    }
  };

  private static class SubmitInput extends JavaScriptObject {
    final native void wait_for_merge(boolean b) /*-{ this.wait_for_merge=b; }-*/;

    static SubmitInput create() {
      return (SubmitInput) createObject();
    }

    protected SubmitInput() {
    }
  }

  private static RestApi api(int id, String action) {
    // TODO Switch to triplet project~branch~id format in URI.
    return new RestApi("/changes/" + id + "/" + action);
  }

  private static RestApi api(int id, String commit, String action) {
    // TODO Switch to triplet project~branch~id format in URI.
    return new RestApi("/changes/" + id + "/revisions/" + commit + "/" + action);
  }

  public static String emptyToNull(String str) {
    return str == null || str.isEmpty() ? null : str;
  }
}
