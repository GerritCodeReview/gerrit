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

package com.google.gerrit.acceptance.rest.project;

import static com.google.gerrit.acceptance.rest.project.BranchAssert.assertBranches;
import static com.google.gerrit.acceptance.rest.project.BranchAssert.assertRefNames;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.ProjectApi.ListRefsRequest;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.RefNames;
import org.junit.Test;

@NoHttpd
public class ListBranchesIT extends AbstractDaemonTest {
  @Test
  public void listBranchesOfNonExistingProject_NotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name("non-existing").branches().get();
  }

  @Test
  public void listBranchesOfNonVisibleProject_NotFound() throws Exception {
    blockRead("refs/*");
    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    gApi.projects().name(project.get()).branches().get();
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void listBranchesOfEmptyProject() throws Exception {
    assertBranches(
        ImmutableList.of(branch("HEAD", null, false), branch(RefNames.REFS_CONFIG, null, false)),
        list().get());
  }

  @Test
  public void listBranches() throws Exception {
    String master = pushTo("refs/heads/master").getCommit().name();
    String dev = pushTo("refs/heads/dev").getCommit().name();
    assertBranches(
        ImmutableList.of(
            branch("HEAD", "master", false),
            branch(RefNames.REFS_CONFIG, null, false),
            branch("refs/heads/dev", dev, true),
            branch("refs/heads/master", master, false)),
        list().get());
  }

  @Test
  public void listBranchesSomeHidden() throws Exception {
    blockRead("refs/heads/dev");
    String master = pushTo("refs/heads/master").getCommit().name();
    pushTo("refs/heads/dev");
    setApiUser(user);
    // refs/meta/config is hidden since user is no project owner
    assertBranches(
        ImmutableList.of(
            branch("HEAD", "master", false), branch("refs/heads/master", master, false)),
        list().get());
  }

  @Test
  public void listBranchesHeadHidden() throws Exception {
    blockRead("refs/heads/master");
    pushTo("refs/heads/master");
    String dev = pushTo("refs/heads/dev").getCommit().name();
    setApiUser(user);
    // refs/meta/config is hidden since user is no project owner
    assertBranches(ImmutableList.of(branch("refs/heads/dev", dev, false)), list().get());
  }

  @Test
  public void listBranchesUsingPagination() throws Exception {
    pushTo("refs/heads/master");
    pushTo("refs/heads/someBranch1");
    pushTo("refs/heads/someBranch2");
    pushTo("refs/heads/someBranch3");

    // Using only limit.
    assertRefNames(
        ImmutableList.of(
            "HEAD", RefNames.REFS_CONFIG, "refs/heads/master", "refs/heads/someBranch1"),
        list().withLimit(4).get());

    // Limit higher than total number of branches.
    assertRefNames(
        ImmutableList.of(
            "HEAD",
            RefNames.REFS_CONFIG,
            "refs/heads/master",
            "refs/heads/someBranch1",
            "refs/heads/someBranch2",
            "refs/heads/someBranch3"),
        list().withLimit(25).get());

    // Using start only.
    assertRefNames(
        ImmutableList.of(
            "refs/heads/master",
            "refs/heads/someBranch1",
            "refs/heads/someBranch2",
            "refs/heads/someBranch3"),
        list().withStart(2).get());

    // Skip more branches than the number of available branches.
    assertRefNames(ImmutableList.<String>of(), list().withStart(7).get());

    // Ssing start and limit.
    assertRefNames(
        ImmutableList.of("refs/heads/master", "refs/heads/someBranch1"),
        list().withStart(2).withLimit(2).get());
  }

  @Test
  public void listBranchesUsingFilter() throws Exception {
    pushTo("refs/heads/master");
    pushTo("refs/heads/someBranch1");
    pushTo("refs/heads/someBranch2");
    pushTo("refs/heads/someBranch3");

    // Using substring.
    assertRefNames(
        ImmutableList.of(
            "refs/heads/someBranch1", "refs/heads/someBranch2", "refs/heads/someBranch3"),
        list().withSubstring("some").get());

    assertRefNames(
        ImmutableList.of(
            "refs/heads/someBranch1", "refs/heads/someBranch2", "refs/heads/someBranch3"),
        list().withSubstring("Branch").get());

    // Using regex.
    assertRefNames(ImmutableList.of("refs/heads/master"), list().withRegex(".*ast.*r").get());
  }

  private ListRefsRequest<BranchInfo> list() throws Exception {
    return gApi.projects().name(project.get()).branches();
  }

  private static BranchInfo branch(String ref, String revision, boolean canDelete) {
    BranchInfo info = new BranchInfo();
    info.ref = ref;
    info.revision = revision;
    info.canDelete = canDelete ? true : null;
    return info;
  }
}
