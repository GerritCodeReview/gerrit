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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import java.util.Iterator;

public class SubmoduleSubscriptionsIT extends AbstractDaemonTest {

  // check if subscribing works
  @Test
  public void testSimpleSubscription() throws Exception {

    // create the super project
    Project.NameKey superProject = new Project.NameKey("super-project");
    createProject(superProject.get());
    TestRepository<?> superRepo = cloneProject(superProject, sshSession);
    grant(Permission.PUSH, superProject, "refs/heads/*");

    // create the project whose changes should be propagated into the super project
    Project.NameKey subscribedProject = new Project.NameKey("subscribed-to-project");
    createProject(subscribedProject.get());
    TestRepository<?> subRepo = cloneProject(subscribedProject, sshSession);
    grant(Permission.PUSH, subscribedProject, "refs/heads/*");

    // create a change in the subrepo (this may not be neeeded)
    subRepo.branch("HEAD").commit().insertChangeId()
      .message("first change")
      .add("a.txt", "a contents")
      .create();
    subRepo.git().push().setRemote("origin").setRefSpecs(new RefSpec("HEAD:refs/heads/master")).call();

    // create the subscription
    String name = "subscribed-to-project";
    String path = "subscribed-to-project";
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/subscribed-to-project";
    String branch = ".";
    String content = buildSubmoduleSection(name, path, url, branch).toString();
    superRepo.branch("HEAD").commit().insertChangeId()
      .message("subject: adding new subscription")
      .add(".gitmodules", content)
      .create();

    Iterator<PushResult> r = superRepo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/master")).call().iterator();

    // create a change in the subrepo which will propagate to the superproject
    subRepo.branch("HEAD").commit().insertChangeId()
      .message("second change")
      .add("b.txt", "a contents")
      .create();
    subRepo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/master")).call();

    // check if the propagation went fine
    ObjectId oid = superRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    RevWalk superRW = superRepo.getRevWalk();
    RevCommit c = superRW.parseCommit(oid);
    assertThat(c.getShortMessage()).isEqualTo("Updated super-project");
    // TODO(sbeller): check diff for updating submodule with superRepo.file("testsub", RevBlob)
  }



  // check if unsubscribing works

  // check if subscribing to different branches works

  // check if circular subscriptions are detected

  // many changes at once

  private static final String newLine = System.getProperty("line.separator");
  private static StringBuilder buildSubmoduleSection(String name,
      String path, String url, String branch) {
    final StringBuilder sb = new StringBuilder();

    sb.append("[submodule \"");
    sb.append(name);
    sb.append("\"]");
    sb.append(newLine);

    sb.append("\tpath = ");
    sb.append(path);
    sb.append(newLine);

    sb.append("\turl = ");
    sb.append(url);
    sb.append(newLine);

    sb.append("\tbranch = ");
    sb.append(branch);
    sb.append(newLine);

    return sb;
  }
}
