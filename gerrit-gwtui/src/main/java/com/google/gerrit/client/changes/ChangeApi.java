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

import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwtjsonrpc.common.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * changes.
 */
public class ChangeApi {
  private static final String URI = "/changes/";

  private static class Message extends JavaScriptObject {
    public final native void setMessage(String value) /*-{ this.message = value; }-*/;
  }

  /**
   * Sends a REST call to abandon a change and notify a callback. TODO: switch
   * to use the new id triplet (project~branch~change) once that data is
   * available to the UI.
   */
  public static void abandon(int changeId, String message,
      AsyncCallback<ChangeInfo> callback) {
    Message msg = new Message();
    msg.setMessage(message);
    new RestApi(URI + changeId + "/abandon").data(msg).post(callback);
  }
}
