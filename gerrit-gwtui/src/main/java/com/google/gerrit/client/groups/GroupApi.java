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

  /** Add member to a group. */
  public static void addMember(AccountGroup.UUID groupUUID,
      String member, AsyncCallback<MemberInfo> cb) {
    new RestApi(membersBase(groupUUID) + "/" + member).put(cb);
  }

  /** Add members to a group. */
  public static void addMembers(AccountGroup.UUID groupUUID,
      Set<String> members,
      final AsyncCallback<NativeList<MemberInfo>> cb) {
    if (members.size() == 1) {
      addMember(groupUUID,
          members.iterator().next(),
          new AsyncCallback<MemberInfo>() {
            @Override
            public void onSuccess(MemberInfo result) {
              cb.onSuccess(NativeList.of(result));
            }

            @Override
            public void onFailure(Throwable caught) {
              cb.onFailure(caught);
            }
          });
    } else {
      RestApi call = new RestApi(membersBase(groupUUID));
      MemberInput input = MemberInput.create();
      for (String member : members) {
        input.add_member(member);
      }
      call.data(input).put(cb);
    }
  }

  /** Remove members from a group. */
  public static void removeMembers(AccountGroup.UUID groupUUID,
      Set<Account.Id> ids, final AsyncCallback<VoidResult> cb) {
    final int cnt = ids.size();
    if (cnt == 0) {
      cb.onSuccess(VoidResult.create());
      return;
    }

    final AsyncCallback<VoidResult> state = new AsyncCallback<VoidResult>() {
      private int remaining = cnt;
      private boolean error;

      @Override
      public synchronized void onSuccess(VoidResult result) {
        if (--remaining == 0 && !error) {
          cb.onSuccess(result);
        }
      }

      @Override
      public synchronized void onFailure(Throwable caught) {
        if (!error) {
          error = true;
          cb.onFailure(caught);
        }
      }
    };
    for (Account.Id u : ids) {
      new RestApi(membersBase(groupUUID) + "/" + u).delete(state);
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
