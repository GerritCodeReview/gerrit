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
import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.Set;

/**
 * A collection of static methods which work on the Gerrit REST API for specific
 * groups.
 */
public class GroupApi {
  /** Create a new group */
  public static void createGroup(String groupName, AsyncCallback<GroupInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    new RestApi("/groups/").id(groupName).ifNoneMatch().put(in, cb);
  }

  /** Set description for a group */
  public static void setGroupDescription(AccountGroup.UUID group,
      String description, AsyncCallback<VoidResult> cb) {
    RestApi call = group(group).view("description");
    if (description != null && !description.isEmpty()) {
      GroupInput in = GroupInput.create();
      in.description(description);
      call.put(in, cb);
    } else {
      call.delete(cb);
    }
  }

  /** Set owner for a group */
  public static void setGroupOwner(AccountGroup.UUID group,
      String owner, AsyncCallback<VoidResult> cb) {
    GroupInput in = GroupInput.create();
    in.owner(owner);
    group(group).view("owner").put(in, cb);
  }

  /** Add member to a group. */
  public static void addMember(AccountGroup.UUID group, String member,
      AsyncCallback<MemberInfo> cb) {
    members(group).id(member).put(cb);
  }

  /** Add members to a group. */
  public static void addMembers(AccountGroup.UUID group,
      Set<String> members,
      final AsyncCallback<NativeList<MemberInfo>> cb) {
    if (members.size() == 1) {
      addMember(group,
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
      members(group).post(input, cb);
    }
  }

  /** Remove members from a group. */
  public static void removeMembers(AccountGroup.UUID group,
      Set<Account.Id> ids, final AsyncCallback<VoidResult> cb) {
    if (ids.size() == 1) {
      Account.Id u = ids.iterator().next();
      members(group).id(u.toString()).delete(cb);
    } else {
      MemberInput in = MemberInput.create();
      for (Account.Id u : ids) {
        in.add_member(u.toString());
      }
      group(group).view("members.delete").post(in, cb);
    }
  }

  /** Include a group into a group. */
  public static void addIncludedGroup(AccountGroup.UUID group, String include,
      AsyncCallback<GroupInfo> cb) {
    groups(group).id(include).put(cb);
  }

  /** Include groups into a group. */
  public static void addIncludedGroups(AccountGroup.UUID group,
      Set<String> includedGroups,
      final AsyncCallback<NativeList<GroupInfo>> cb) {
    if (includedGroups.size() == 1) {
      addIncludedGroup(group,
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
      groups(group).post(input, cb);
    }
  }

  private static RestApi members(AccountGroup.UUID group) {
    return group(group).view("members");
  }

  private static RestApi groups(AccountGroup.UUID group) {
    return group(group).view("groups");
  }

  private static RestApi group(AccountGroup.UUID group) {
    return new RestApi("/groups/").id(group.get());
  }

  private static class GroupInput extends JavaScriptObject {
    final native void description(String d) /*-{ if(d)this.description=d; }-*/;
    final native void owner(String o) /*-{ if(o)this.owner=o; }-*/;

    static GroupInput create() {
      return (GroupInput) createObject();
    }

    protected GroupInput() {
    }
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
