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

package com.google.gerrit.extensions.api.projects;

import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

import java.util.List;

public interface ProjectApi {
  ProjectApi create() throws RestApiException;
  ProjectApi create(ProjectInput in) throws RestApiException;
  ProjectInfo get() throws RestApiException;

  List<ProjectInfo> children() throws RestApiException;
  List<ProjectInfo> children(boolean recursive) throws RestApiException;
  ChildProjectApi child(String name) throws RestApiException;

  /**
   * Look up a branch by refname.
   * <p>
   * <strong>Note:</strong> This method eagerly reads the branch. Methods that
   * mutate the branch do not necessarily re-read the branch. Therefore, calling
   * a getter method on an instance after calling a mutation method on that same
   * instance is not guaranteed to reflect the mutation. It is not recommended
   * to store references to {@code BranchApi} instances.
   *
   * @param ref branch name, with or without "refs/heads/" prefix.
   * @throws RestApiException if a problem occurred reading the project.
   * @return API for accessing the branch.
   */
  BranchApi branch(String ref) throws RestApiException;

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements ProjectApi {
    @Override
    public ProjectApi create() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ProjectApi create(ProjectInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ProjectInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public List<ProjectInfo> children() {
      throw new NotImplementedException();
    }

    @Override
    public List<ProjectInfo> children(boolean recursive) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ChildProjectApi child(String name) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public BranchApi branch(String ref) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
