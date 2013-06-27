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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.git.util.SimpleDataRepositoryTestCase;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ChangeControl.Factory;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IncludedInDetailTest extends SimpleDataRepositoryTestCase {

  private List<String> expTags = new ArrayList<String>();
  private List<String> expBranches = new ArrayList<String>();

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void resolveLatestCommit() throws Exception {
    // Check tip commit
    IncludedInDetail detail = resolve(commit_v2_5.getName());

    // Check that only tags and branches which refer the tip are returned
    expTags.add(TAG_2_5);
    expTags.add(TAG_2_5_ANNOTATED);
    expTags.add(TAG_2_5_ANNOTATED_TWICE);
    assertEquals(expTags, detail.getTags());
    expBranches.add(BRANCH_2_5);
    assertEquals(expBranches, detail.getBranches());
  }

  @Test
  public void resolveFirstCommit() throws Exception {
    // Check first commit
    IncludedInDetail detail = resolve(commit_initial.getName());

    // Check whether all tags and branches are returned
    expTags.add(TAG_1_0);
    expTags.add(TAG_1_0_1);
    expTags.add(TAG_1_3);
    expTags.add(TAG_2_0);
    expTags.add(TAG_2_0_1);
    expTags.add(TAG_2_5);
    expTags.add(TAG_2_5_ANNOTATED);
    expTags.add(TAG_2_5_ANNOTATED_TWICE);
    assertEquals(expTags, detail.getTags());

    expBranches.add(BRANCH_MASTER);
    expBranches.add(BRANCH_1_0);
    expBranches.add(BRANCH_1_3);
    expBranches.add(BRANCH_2_0);
    expBranches.add(BRANCH_2_5);
    assertEquals(expBranches, detail.getBranches());
  }

  @Test
  public void resolveBetwixtCommit() throws Exception {
    // Check a commit somewhere in the middle
    IncludedInDetail detail = resolve(commit_v1_3.getName());

    // Check whether all succeeding tags and branches are returned
    expTags.add(TAG_1_3);
    expTags.add(TAG_2_5);
    expTags.add(TAG_2_5_ANNOTATED);
    expTags.add(TAG_2_5_ANNOTATED_TWICE);
    assertEquals(expTags, detail.getTags());

    expBranches.add(BRANCH_1_3);
    expBranches.add(BRANCH_2_5);
    assertEquals(expBranches, detail.getBranches());
  }

  @Test(expected = InvalidRevisionException.class)
  public void invalidCommitError() throws Exception {
    // check an invalid commit id
    resolve("1111111111111111111111111111111111111111");
  }

  private IncludedInDetail resolve(String commitId) throws Exception {
    // Preparation to be able to return test repository
    ChangeControl control = Mockito.mock(ChangeControl.class);
    Project project = new Project(new Project.NameKey("testProject"));
    Mockito.when(control.getProject()).thenReturn(project);
    GitRepositoryManager repoManager = Mockito.mock(GitRepositoryManager.class);
    Mockito.when(repoManager.openRepository(project.getNameKey())).thenReturn(
        db);

    // Preparation to be able to return relevant change
    Change.Id changeId = new Change.Id(1);
    Change change = new Change(null, changeId, null, null);
    PatchSet.Id patchSetId = new PatchSet.Id(change.getId(), 1);
    PatchSet patch = new PatchSet(patchSetId);
    patch.setRevision(new RevId(commitId));
    change.setCurrentPatchSet(new PatchSetInfo(patch.getId()));
    Mockito.when(control.getChange()).thenReturn(change);
    PatchSetAccess patchSetAccess = Mockito.mock(PatchSetAccess.class);
    Mockito.when(patchSetAccess.get(patchSetId)).thenReturn(patch);
    ReviewDb db = Mockito.mock(ReviewDb.class);
    Mockito.when(db.patchSets()).thenReturn(patchSetAccess);
    Factory changeControlFactory = Mockito.mock(ChangeControl.Factory.class);

    Mockito.when(changeControlFactory.validateFor(changeId))
        .thenReturn(control);
    return new IncludedInDetailFactory(db, changeControlFactory, repoManager,
        changeId).call();
  }

  private void assertEquals(List<String> l1, List<String> l2) {
    Collections.sort(l1);
    Collections.sort(l2);
    Assert.assertEquals(l1, l2);
  }
}
