// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.project.BranchResource.BRANCH_KIND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.server.project.BranchResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Module;
import org.junit.Test;

public class DeleteOnCollectionIT extends AbstractDaemonTest {
  @Override
  public Module createModule() {
    return new RestApiModule() {
      @Override
      public void configure() {
        deleteOnCollection(BRANCH_KIND)
            .toInstance(
                new RestCollectionModifyView<ProjectResource, BranchResource, Object>() {
                  @Override
                  public Object apply(ProjectResource parentResource, Object input)
                      throws Exception {
                    return Response.none();
                  }
                });
      }
    };
  }

  @Test
  public void deleteOnChildCollection() throws Exception {
    RestResponse response = adminRestSession.delete("/projects/" + project.get() + "/branches");
    assertThat(response.getStatusCode()).isEqualTo(SC_NO_CONTENT);
  }
}
