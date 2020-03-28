// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;


import com.google.gerrit.acceptance.GitUtil;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Before;

public abstract class AbstractHttpPushTag extends AbstractPushTag {

  @Before
  public void cloneProjectOverHttp() throws Exception {
    // clone with user to avoid inherited tag permissions of admin user
    CredentialsProvider.setDefault(
        new UsernamePasswordCredentialsProvider(user.username, user.httpPassword));
    testRepo = GitUtil.cloneProject(project, user.getHttpUrl(server) + "/a/" + project.get());
  }
}
