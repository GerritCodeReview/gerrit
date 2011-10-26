// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result from adding or removing a member from an account group.
 */
public class GroupMemberResult {
  protected List<Error> errors;
  protected GroupDetail group;

  public GroupMemberResult() {
    errors = new ArrayList<Error>();
  }

  public void addError(final Error e) {
    errors.add(e);
  }

  public List<Error> getErrors() {
    return errors;
  }

  public GroupDetail getGroup() {
    return group;
  }

  public void setGroup(final GroupDetail group) {
    this.group = group;
  }

  public static class Error {
    public static enum Type {
      /** The account is inactive. */
      ACCOUNT_INACTIVE,

      /** Not permitted to add this member to the group. */
      ADD_NOT_PERMITTED,

      /** Not permitted to remove this member from the group. */
      REMOVE_NOT_PERMITTED
    }

    protected Type type;
    protected String name;

    protected Error() {
    }

    public Error(final Type type, final String who) {
      this.type = type;
      this.name = who;
    }

    public Type getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return type + " " + name;
    }
  }
}
