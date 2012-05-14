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

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class VisibleProjectDetails extends Handler<List<ProjectDetail>> {

  interface Factory {
    VisibleProjectDetails create();
  }

  private final ProjectCache projectCache;
  private final ProjectDetailFactory.Factory projectDetailFactory;

  @Inject
  VisibleProjectDetails(final ProjectCache projectCache,
      final ProjectDetailFactory.Factory projectDetailFactory) {
    this.projectCache = projectCache;
    this.projectDetailFactory = projectDetailFactory;
  }

  @Override
  public List<ProjectDetail> call() {
    List<ProjectDetail> result = new ArrayList<ProjectDetail>();
    for (Project.NameKey projectName : projectCache.all()) {
      try {
        result.add(projectDetailFactory.create(projectName).call());
      } catch (NoSuchProjectException e) {
      } catch (IOException e) {
      }
    }
    Collections.sort(result, new Comparator<ProjectDetail>() {
      public int compare(final ProjectDetail a, final ProjectDetail b) {
        return a.project.getName().compareTo(b.project.getName());
      }
    });
    return result;
  }
}
