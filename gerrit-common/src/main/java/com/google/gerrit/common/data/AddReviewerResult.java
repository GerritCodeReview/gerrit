// Copyright (C) 2009 The Android Open Source Project
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

/** Result from adding a reviewer to a change. */
public class AddReviewerResult {
  protected List<Error> errors;
  protected ChangeDetail change;

  public AddReviewerResult() {
    errors = new ArrayList<Error>();
  }

  public void addError(final Error e) {
    errors.add(e);
  }

  public List<Error> getErrors() {
    return errors;
  }

  public ChangeDetail getChange() {
    return change;
  }

  public void setChange(final ChangeDetail d) {
    change = d;
  }

  public static class Error {
    public static enum Type {
      /** Name supplied does not match to a registered account. */
      ACCOUNT_NOT_FOUND,

      /** The account is not permitted to see the change. */
      CHANGE_NOT_VISIBLE
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
