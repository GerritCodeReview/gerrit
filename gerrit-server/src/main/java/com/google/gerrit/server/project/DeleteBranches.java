// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.project.DeleteBranches.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.List;

@Singleton
class DeleteBranches implements RestModifyView<ProjectResource, Input> {
  public static class Input {
    List<String> branches;

    public static Input fromMembers(List<String> branches) {
      Input in = new Input();
      in.branches = branches;
      return in;
    }

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.branches == null) {
        in.branches = Lists.newArrayListWithCapacity(1);
      }
      return in;
    }
  }

  private final Provider<BranchesCollection> branchesCollection;
  private final Provider<DeleteBranch> deleteBranch;

  @Inject
  DeleteBranches(Provider<BranchesCollection> branchesCollection,
      Provider<DeleteBranch> deleteBranch) {
    this.branchesCollection = branchesCollection;
    this.deleteBranch = deleteBranch;
  }

  @Override
  public Response<?> apply(ProjectResource project, Input input)
      throws OrmException, ResourceNotFoundException, BadRequestException,
      IOException, AuthException, ResourceConflictException {

    input = Input.init(input);

    for (String branch : input.branches) {
      BranchResource rsrc =
          branchesCollection.get().parse(project, IdString.fromDecoded(branch));
      deleteBranch.get().apply(rsrc, new DeleteBranch.Input());
    }
    return Response.none();
  }
}
