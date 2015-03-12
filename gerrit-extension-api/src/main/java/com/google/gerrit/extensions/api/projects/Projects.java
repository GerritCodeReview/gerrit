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

public interface Projects {
  /**
   * Look up a project by name.
   * <p>
   * <strong>Note:</strong> This method eagerly reads the project. Methods that
   * mutate the project do not necessarily re-read the project. Therefore,
   * calling a getter method on an instance after calling a mutation method on
   * that same instance is not guaranteed to reflect the mutation. It is not
   * recommended to store references to {@code ProjectApi} instances.
   *
   * @param name project name.
   * @return API for accessing the project.
   * @throws RestApiException if an error occurred.
   */
  ProjectApi name(String name) throws RestApiException;

  /**
   * Create a project using the default configuration.
   *
   * @param name project name.
   * @return API for accessing the newly-created project.
   * @throws RestApiException if an error occurred.
   */
  ProjectApi create(String name) throws RestApiException;

  /**
   * Create a project.
   *
   * @param in project creation input; name must be set.
   * @return API for accessing the newly-created project.
   * @throws RestApiException if an error occurred.
   */
  ProjectApi create(ProjectInput in) throws RestApiException;

  ListRequest list();

  public abstract class ListRequest {
    private boolean description;
    private String prefix;
    private int limit;
    private int start;

    public abstract List<ProjectInfo> get() throws RestApiException;

    public ListRequest withDescription(boolean description) {
      this.description = description;
      return this;
    }

    public ListRequest withPrefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public ListRequest withLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public ListRequest withStart(int start) {
      this.start = start;
      return this;
    }

    public boolean getDescription() {
      return description;
    }

    public String getPrefix() {
      return prefix;
    }

    public int getLimit() {
      return limit;
    }

    public int getStart() {
      return start;
    }
  }

  /**
   * A default implementation which allows source compatibility
   * when adding new methods to the interface.
   **/
  public class NotImplemented implements Projects {
    @Override
    public ProjectApi name(String name) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ProjectApi create(ProjectInput in) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ProjectApi create(String name) throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public ListRequest list() {
      throw new NotImplementedException();
    }
  }
}
