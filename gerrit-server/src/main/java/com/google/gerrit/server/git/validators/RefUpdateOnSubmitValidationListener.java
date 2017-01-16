// Copyright (C) 2016 The Android Open Source Project
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
package com.google.gerrit.server.git.validators;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.BatchUpdate.RepoContext;
import com.google.gerrit.server.validators.ValidationException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import java.io.IOException;


/**
 * Listener to provide validation on operation that is going to be by submit
 * strategy merging a change into a given ref.
 */
@ExtensionPoint
public interface RefUpdateOnSubmitValidationListener {
  public class BranchUpdateArguments {
    RepoContext ctx;
    ObjectId currentTip;
    ObjectId newTip;
    Branch.NameKey branch;

    public BranchUpdateArguments(RepoContext ctx, ObjectId currentTip, ObjectId newTip,
        Branch.NameKey destRef) {
      this.ctx = ctx;
      this.branch = destRef;
      this.currentTip = currentTip;
      this.newTip = newTip;
    }

    public Project.NameKey getProject() {
      return ctx.getProject();
    }

    public Repository getRepository() throws IOException {
      return ctx.getRepository();
    }

    public RevWalk getRevWalk() throws IOException {
      return ctx.getRevWalk();
    }

    /** Returns currentTip if branch exists already, else <code>null</code> */
    public ObjectId getCurrentTip(){
      return currentTip;
    }

    public ObjectId getNewTip(){
      return newTip;
    }

    public Branch.NameKey getBranchName(){
      return branch;
    }
  }

  /**
   * Validate a ref operation before it is performed by submit strategy.
   *
   * Note that
   */
  void preRefUpdate(BranchUpdateArguments args)
      throws ValidationException;
}
