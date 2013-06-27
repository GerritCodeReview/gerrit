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
import com.google.gerrit.git.util.TestGitRepoFactory;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ChangeControl.Factory;

import org.eclipse.jgit.lib.Repository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

public class IncludedInDetailTest {

  private static Repository repository;

  private IncludedInDetailFactory cut;
  private PatchSet patch;
  private List<String> expTags = new ArrayList<String>();
  private List<String> expBranches = new ArrayList<String>();

  /**
   * Do cloning of a read-only repository only once.
   */
  @BeforeClass
  public static void setUpClass() throws Exception {
    repository =
        TestGitRepoFactory.getRepositoryInstanceBy("simple_git_repo")
            .getRepository();
  }

  @Before
  public void setUp() throws Exception {

    // Preparation to be able to return test repository
    ChangeControl control = Mockito.mock(ChangeControl.class);
    Project project = new Project(new Project.NameKey("testProject"));
    Mockito.when(control.getProject()).thenReturn(project);
    GitRepositoryManager repoManager = Mockito.mock(GitRepositoryManager.class);
    Mockito.when(repoManager.openRepository(project.getNameKey())).thenReturn(
        repository);

    // Preparation to be able to return relevant change
    Id changeId = new Id(1);
    Change change = new Change(null, changeId, null, null);
    PatchSet.Id patchSetId = new PatchSet.Id(change.getId(), 1);
    patch = new PatchSet(patchSetId);
    change.setCurrentPatchSet(new PatchSetInfo(patch.getId()));
    Mockito.when(control.getChange()).thenReturn(change);
    PatchSetAccess patchSetAccess = Mockito.mock(PatchSetAccess.class);
    Mockito.when(patchSetAccess.get(patchSetId)).thenReturn(patch);
    ReviewDb db = Mockito.mock(ReviewDb.class);
    Mockito.when(db.patchSets()).thenReturn(patchSetAccess);
    Factory changeControlFactory = Mockito.mock(ChangeControl.Factory.class);

    Mockito.when(changeControlFactory.validateFor(changeId))
        .thenReturn(control);
    cut =
        new IncludedInDetailFactory(db, changeControlFactory, repoManager,
            changeId);
  }

  @Test
  public void resolveLatestCommit() throws Exception {

    // Prepare: Set tip commit id as relevant change
    patch.setRevision(new RevId("95cb400271d60fe31224127aae49c2d2af400179"));

    // Execute
    IncludedInDetail detail = cut.call();

    // Check that only tags and branches which refer the tip are returned
    expTags.add("2.5");
    expTags.add("2.5-annotated");
    expTags.add("2.5-annotated-twice");
    Assert.assertEquals(expTags, detail.getTags());
    expBranches.add("rel-2.5");
    Assert.assertEquals(expBranches, detail.getBranches());

  }

  @Test
  public void resolveFirstCommit() throws Exception {

    // Prepare: Set first commit id as relevant change
    patch.setRevision(new RevId("e593359e6bc8fe7cb6a015bf71cf9744b4382400"));

    // Execute
    IncludedInDetail detail = cut.call();

    // Check whether all tags and branches are returned
    expTags.add("1.0");
    expTags.add("1.0.1");
    expTags.add("1.1");
    expTags.add("1.2");
    expTags.add("1.3");
    expTags.add("1.3.1");
    expTags.add("2.0");
    expTags.add("2.0.1");
    expTags.add("2.5");
    expTags.add("2.5-annotated");
    expTags.add("2.5-annotated-twice");
    Assert.assertEquals(expTags, detail.getTags());

    expBranches.add("rel-2.5");
    Assert.assertEquals(expBranches, detail.getBranches());

  }

  @Test
  public void resolveBetwixtCommit() throws Exception {

    // Prepare: Set a commit somewhere in the middle as relevant
    patch.setRevision(new RevId("3b32c5cbce0cc3027eb597457d0c99ae484778fd"));

    // Execute
    IncludedInDetail detail = cut.call();

    // Check whether all succeeding tags and branches are returned
    expTags.add("1.3");
    expTags.add("1.3.1");
    expTags.add("2.5");
    expTags.add("2.5-annotated");
    expTags.add("2.5-annotated-twice");
    Assert.assertEquals(expTags, detail.getTags());

    expBranches.add("rel-2.5");
    Assert.assertEquals(expBranches, detail.getBranches());

  }

  @Test(expected = InvalidRevisionException.class)
  public void invalidCommitError() throws Exception {
    patch.setRevision(new RevId("1111111111111111111111111111111111111111"));
    cut.call();
  }

}
