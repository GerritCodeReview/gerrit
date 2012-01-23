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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Branch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeleteBranchesResult {

  protected Set<Branch.NameKey> deletedBranches;
  protected List<Error> errors;

  public DeleteBranchesResult() {
    errors = new ArrayList<Error>();
  }

  public Set<Branch.NameKey> getDeletedBranches() {
    return deletedBranches;
  }

  public void setDeletedBranches(final Set<Branch.NameKey> deletedBranches) {
    this.deletedBranches = deletedBranches;
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  public List<Error> getErrors() {
    return errors;
  }

  public void addError(final Error error) {
    errors.add(error);
  }

  public static class Error {
    public static enum Type {
      /**
       * The branch cannot be deleted because there are open changes for this
       * branch.
       */
      OPEN_CHANGES,

      /**
       * The branch cannot be deleted because some of the open changes could not
       * be abandoned.
       */
      ABANDON_FAILED
    }

    protected Type type;
    protected Branch.NameKey branchKey;

    protected Error() {
    }

    public Error(final Type type, final Branch.NameKey branchKey) {
      this.type = type;
      this.branchKey = branchKey;
    }

    public Type getType() {
      return type;
    }

    public Branch.NameKey getBranchKey() {
      return branchKey;
    }

    @Override
    public String toString() {
      return type + " " + branchKey.get();
    }
  }
}
