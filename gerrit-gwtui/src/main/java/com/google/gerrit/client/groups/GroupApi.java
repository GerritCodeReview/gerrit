// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.groups;

import static com.google.gerrit.client.changes.ChangeApi.emptyToNull;

import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * groups.
 */
public class GroupApi {

  /** Add a member to a group. */
  public static void addMember(AccountGroup.UUID groupUUID,
      String nameOrEmail, AsyncCallback<MemberInfo> cb) {
    RestApi call = new RestApi(membersBase(groupUUID));
    nameOrEmail = emptyToNull(nameOrEmail);
    if (nameOrEmail != null) {
      MemberInput input = MemberInput.create();
      input.nameOrEmail(nameOrEmail);
      call.data(input).put(cb);
    }
  }

  private static String membersBase(AccountGroup.UUID groupUUID) {
    return base(groupUUID) + "members";
  }

  private static String base(AccountGroup.UUID groupUUID) {
    String id = URL.encodePathSegment(groupUUID.get());
    return "/groups/" + id + "/";
  }

  private static class MemberInput extends JavaScriptObject {
    final native void nameOrEmail(String n) /*-{ this.name_or_email=n; }-*/;

    static MemberInput create() {
      return (MemberInput) createObject();
    }

    protected MemberInput() {
    }
  }
}
