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

import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.rpc.NativeList;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.Set;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * groups.
 */
public class GroupApi {

  /** Add members to a group. */
  public static void addMembers(AccountGroup.UUID groupUUID,
      Set<String> members, AsyncCallback<NativeList<MemberInfo>> cb) {
    RestApi call = new RestApi(membersBase(groupUUID));
    MemberInput input = MemberInput.create();
    for (String member : members) {
      input.add_member(member);
    }
    call.data(input).put(cb);
  }

  /** Remove members from a group. */
  public static void removeMembers(AccountGroup.UUID groupUUID,
      Set<Account.Id> ids, AsyncCallback<VoidResult> cb) {
    RestApi call = new RestApi(membersBase(groupUUID));
    MemberInput input = MemberInput.create();
    for (Account.Id id : ids) {
      input.add_member(Integer.toString(id.get()));
    }
    call.data(input).delete(cb);
  }

  private static String membersBase(AccountGroup.UUID groupUUID) {
    return base(groupUUID) + "members";
  }

  private static String base(AccountGroup.UUID groupUUID) {
    String id = URL.encodePathSegment(groupUUID.get());
    return "/groups/" + id + "/";
  }

  private static class MemberInput extends JavaScriptObject {
    final native void init() /*-{ this.members = []; }-*/;

    final native void add_member(String n) /*-{ this.members.push(n); }-*/;

    static MemberInput create() {
      MemberInput m = (MemberInput) createObject();
      m.init();
      return m;
    }

    protected MemberInput() {
    }
  }
}
