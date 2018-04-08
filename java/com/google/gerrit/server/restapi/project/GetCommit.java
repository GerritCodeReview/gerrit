// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.git.CommitUtil;
import com.google.gerrit.server.project.CommitResource;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class GetCommit implements RestReadView<CommitResource> {

  @Override
  public CommitInfo apply(CommitResource rsrc) throws IOException {
    return CommitUtil.toCommitInfo(rsrc.getCommit());
  }
}
