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

  /** Create a new group */
  public static void createGroup(String groupName, AsyncCallback<GroupInfo> cb) {
    String n = URL.encodePathSegment(groupName);
    new RestApi("/groups/" + n).ifNoneMatch().put(cb);
  }

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
      MemberInput input = MemberInput.create();
      for (String member : members) {
        input.add_member(member);
      }
      new RestApi(membersBase(groupUUID) + ".add").data(input).post(cb);
    }
  }

  /** Remove members from a group. */
  public static void removeMembers(AccountGroup.UUID groupUUID,
      Set<Account.Id> ids, final AsyncCallback<VoidResult> cb) {
    if (ids.size() == 1) {
      Account.Id u = ids.iterator().next();
      new RestApi(membersBase(groupUUID) + "/" + u).delete(cb);
    } else {
      MemberInput in = MemberInput.create();
      for (Account.Id u : ids) {
        in.add_member(u.toString());
      }
      new RestApi(membersBase(groupUUID) + ".delete").data(in).post(cb);
    }
  }

  /** Include a group into a group. */
  public static void addIncludedGroup(AccountGroup.UUID groupUUID,
      String includedGroup, AsyncCallback<GroupInfo> cb) {
    new RestApi(includedGroupsBase(groupUUID) + "/" + includedGroup).put(cb);
  }

  /** Include groups into a group. */
  public static void addIncludedGroups(AccountGroup.UUID groupUUID,
      Set<String> includedGroups,
      final AsyncCallback<NativeList<GroupInfo>> cb) {
    if (includedGroups.size() == 1) {
      addIncludedGroup(groupUUID,
          includedGroups.iterator().next(),
          new AsyncCallback<GroupInfo>() {
            @Override
            public void onSuccess(GroupInfo result) {
              cb.onSuccess(NativeList.of(result));
            }

            @Override
            public void onFailure(Throwable caught) {
              cb.onFailure(caught);
            }
          });
    } else {
      IncludedGroupInput input = IncludedGroupInput.create();
      for (String includedGroup : includedGroups) {
        input.add_group(includedGroup);
      }
      new RestApi(includedGroupsBase(groupUUID) + ".add").data(input).post(cb);
    }
  }

  private static String membersBase(AccountGroup.UUID groupUUID) {
    return base(groupUUID) + "members";
  }

  private static String includedGroupsBase(AccountGroup.UUID groupUUID) {
    return base(groupUUID) + "groups";
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

  private static class IncludedGroupInput extends JavaScriptObject {
    final native void init() /*-{ this.groups = []; }-*/;
    final native void add_group(String n) /*-{ this.groups.push(n); }-*/;

    static IncludedGroupInput create() {
      IncludedGroupInput g = (IncludedGroupInput) createObject();
      g.init();
      return g;
    }

    protected IncludedGroupInput() {
    }
  }
}
