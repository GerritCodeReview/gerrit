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
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.GroupInfo;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.Set;

/** A collection of static methods which work on the Gerrit REST API for specific groups. */
public class GroupApi {
  /** Create a new group */
  public static void createGroup(String groupName, AsyncCallback<GroupInfo> cb) {
    JavaScriptObject in = JavaScriptObject.createObject();
    new RestApi("/groups/").id(groupName).ifNoneMatch().put(in, cb);
  }

  public static void getGroupDetail(String group, AsyncCallback<GroupInfo> cb) {
    group(group).view("detail").get(cb);
  }

  /** Get the name of a group */
  public static void getGroupName(AccountGroup.UUID group, AsyncCallback<NativeString> cb) {
    group(group).view("name").get(cb);
  }

  /** Check if the current user is owner of a group */
  public static void isGroupOwner(String groupName, AsyncCallback<Boolean> cb) {
    GroupMap.myOwned(
        groupName,
        new AsyncCallback<GroupMap>() {
          @Override
          public void onSuccess(GroupMap result) {
            cb.onSuccess(!result.isEmpty());
          }

          @Override
          public void onFailure(Throwable caught) {
            cb.onFailure(caught);
          }
        });
  }

  /** Rename a group */
  public static void renameGroup(
      AccountGroup.UUID group, String newName, AsyncCallback<VoidResult> cb) {
    GroupInput in = GroupInput.create();
    in.name(newName);
    group(group).view("name").put(in, cb);
  }

  /** Set description for a group */
  public static void setGroupDescription(
      AccountGroup.UUID group, String description, AsyncCallback<VoidResult> cb) {
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
  public static void setGroupOwner(
      AccountGroup.UUID group, String owner, AsyncCallback<GroupInfo> cb) {
    GroupInput in = GroupInput.create();
    in.owner(owner);
    group(group).view("owner").put(in, cb);
  }

  /** Set the options for a group */
  public static void setGroupOptions(
      AccountGroup.UUID group, boolean isVisibleToAll, AsyncCallback<VoidResult> cb) {
    GroupOptionsInput in = GroupOptionsInput.create();
    in.visibleToAll(isVisibleToAll);
    group(group).view("options").put(in, cb);
  }

  /** Add member to a group. */
  public static void addMember(
      AccountGroup.UUID group, String member, AsyncCallback<AccountInfo> cb) {
    members(group).id(member).put(cb);
  }

  /** Add members to a group. */
  public static void addMembers(
      AccountGroup.UUID group, Set<String> members, AsyncCallback<JsArray<AccountInfo>> cb) {
    if (members.size() == 1) {
      addMember(
          group,
          members.iterator().next(),
          new AsyncCallback<AccountInfo>() {
            @Override
            public void onSuccess(AccountInfo result) {
              cb.onSuccess(Natives.arrayOf(result));
            }

            @Override
            public void onFailure(Throwable caught) {
              cb.onFailure(caught);
            }
          });
    } else {
      MemberInput input = MemberInput.create();
      for (String member : members) {
        input.addMember(member);
      }
      members(group).post(input, cb);
    }
  }

  /** Remove members from a group. */
  public static void removeMembers(
      AccountGroup.UUID group, Set<Integer> ids, AsyncCallback<VoidResult> cb) {
    if (ids.size() == 1) {
      members(group).id(ids.iterator().next().toString()).delete(cb);
    } else {
      MemberInput in = MemberInput.create();
      for (Integer id : ids) {
        in.addMember(id.toString());
      }
      group(group).view("members.delete").post(in, cb);
    }
  }

  /** Include a group into a group. */
  public static void addIncludedGroup(
      AccountGroup.UUID group, String include, AsyncCallback<GroupInfo> cb) {
    groups(group).id(include).put(cb);
  }

  /** Include groups into a group. */
  public static void addIncludedGroups(
      AccountGroup.UUID group,
      Set<String> includedGroups,
      final AsyncCallback<JsArray<GroupInfo>> cb) {
    if (includedGroups.size() == 1) {
      addIncludedGroup(
          group,
          includedGroups.iterator().next(),
          new AsyncCallback<GroupInfo>() {
            @Override
            public void onSuccess(GroupInfo result) {
              cb.onSuccess(Natives.arrayOf(result));
            }

            @Override
            public void onFailure(Throwable caught) {
              cb.onFailure(caught);
            }
          });
    } else {
      IncludedGroupInput input = IncludedGroupInput.create();
      for (String includedGroup : includedGroups) {
        input.addGroup(includedGroup);
      }
      groups(group).post(input, cb);
    }
  }

  /** Remove included groups from a group. */
  public static void removeIncludedGroups(
      AccountGroup.UUID group, Set<AccountGroup.UUID> ids, AsyncCallback<VoidResult> cb) {
    if (ids.size() == 1) {
      AccountGroup.UUID g = ids.iterator().next();
      groups(group).id(g.get()).delete(cb);
    } else {
      IncludedGroupInput in = IncludedGroupInput.create();
      for (AccountGroup.UUID g : ids) {
        in.addGroup(g.get());
      }
      group(group).view("groups.delete").post(in, cb);
    }
  }

  /** Get audit log of a group. */
  public static void getAuditLog(
      AccountGroup.UUID group, AsyncCallback<JsArray<GroupAuditEventInfo>> cb) {
    group(group).view("log.audit").get(cb);
  }

  private static RestApi members(AccountGroup.UUID group) {
    return group(group).view("members");
  }

  private static RestApi groups(AccountGroup.UUID group) {
    return group(group).view("groups");
  }

  private static RestApi group(AccountGroup.UUID group) {
    return group(group.get());
  }

  private static RestApi group(String group) {
    return new RestApi("/groups/").id(group);
  }

  private static class GroupInput extends JavaScriptObject {
    final native void description(String d) /*-{ if(d)this.description=d; }-*/;

    final native void name(String n) /*-{ if(n)this.name=n; }-*/;

    final native void owner(String o) /*-{ if(o)this.owner=o; }-*/;

    static GroupInput create() {
      return (GroupInput) createObject();
    }

    protected GroupInput() {}
  }

  private static class GroupOptionsInput extends JavaScriptObject {
    final native void visibleToAll(boolean v) /*-{ if(v)this.visible_to_all=v; }-*/;

    static GroupOptionsInput create() {
      return (GroupOptionsInput) createObject();
    }

    protected GroupOptionsInput() {}
  }

  private static class MemberInput extends JavaScriptObject {
    final native void init() /*-{ this.members = []; }-*/;

    final native void addMember(String n) /*-{ this.members.push(n); }-*/;

    static MemberInput create() {
      MemberInput m = (MemberInput) createObject();
      m.init();
      return m;
    }

    protected MemberInput() {}
  }

  private static class IncludedGroupInput extends JavaScriptObject {
    final native void init() /*-{ this.groups = []; }-*/;

    final native void addGroup(String n) /*-{ this.groups.push(n); }-*/;

    static IncludedGroupInput create() {
      IncludedGroupInput g = (IncludedGroupInput) createObject();
      g.init();
      return g;
    }

    protected IncludedGroupInput() {}
  }
}
