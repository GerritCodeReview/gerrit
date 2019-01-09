// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.git.ChangeRefCache;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.query.change.ChangeData;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.inject.Inject;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the ChangeRefCache by running ls-remote calls and conditionally disabling the index and
 * NoteDb. The cache is enabled by default.
 *
 * <p>Why are we not just testing ChangeRefCache directly? Our ref filtering code is rather complex
 * and it is easy to get something wrong there. We want our assumptions about the performance of the
 * cache to be validated against the entire component rather than just the cache.
 */
@NoHttpd
public class ChangeRefCacheIT extends AbstractDaemonTest {

  @Inject private PermissionBackend permissionBackend;
  @Inject private ChangeRefCache changeRefCache;

  @Before
  public void setUp() throws Exception {
    // We want full ref evaluation so that we hit the cache every time.
    baseConfig.setBoolean("auth", null, "skipFullRefEvaluationIfAllRefsAreVisible", false);
  }

  /**
   * Ensure we use only the change index for getting initial data populated and don't touch NoteDb.
   */
  @Test
  public void useIndexForBootstrapping() throws Exception {
    ChangeData change = createChange().getChange();
    // TODO(hiesel) Rework as AutoClosable. Here and below.
    changeRefCache.resetBootstrappedProjects();
    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change.getId()),
          change.currentPatchSet().getId().toRefName());
    } finally {
      enableDb(ctx);
    }
  }

  /**
   * Ensure we use only the change index for getting initial data populated and don't require any
   * storage backend after that as long the data didn't change.
   */
  @Test
  public void serveResultsFromCacheAfterInitialBootstrap() throws Exception {
    ChangeData change = createChange().getChange();
    changeRefCache.resetBootstrappedProjects();
    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change.getId()),
          change.currentPatchSet().getId().toRefName());
    } finally {
      enableDb(ctx);
    }

    // No change since our first call, so this time we don't bootstrap or touch the NoteDb
    AcceptanceTestRequestScope.Context ctx2 = disableDb();
    try {
      try (AutoCloseable ignored = disableChangeIndex()) {
        assertUploadPackRefs(
            "HEAD",
            "refs/heads/master",
            RefNames.changeMetaRef(change.getId()),
            change.currentPatchSet().getId().toRefName());
      }
    } finally {
      enableDb(ctx2);
    }
  }

  /**
   * Ensure we use only the change index for getting initial data populated and NoteDb for reloading
   * data that changed since.
   */
  @Test
  public void useIndexForBootstrappingAndDbForDeltaReload() throws Exception {
    ChangeData change1 = createChange().getChange();
    AcceptanceTestRequestScope.Context ctx = disableDb();
    // Bootstrap: No NoteDb access as we expect it to use the index.
    changeRefCache.resetBootstrappedProjects();
    try {
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change1.getId()),
          change1.currentPatchSet().getId().toRefName());
    } finally {
      enableDb(ctx);
    }
    // Delta reload: No index access as we expect it to use the NoteDb.
    ChangeData change2 = createChange().getChange();
    try (AutoCloseable ignored = disableChangeIndex()) {
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change1.getId()),
          change1.currentPatchSet().getId().toRefName(),
          RefNames.changeMetaRef(change2.getId()),
          change2.currentPatchSet().getId().toRefName());
    }
  }

  /**
   * Ensure we use only the change index for getting initial data populated and NoteDb for reloading
   * data that changed since.
   */
  @Test
  public void useDbForDeltaReloadOnNewPatchSet() throws Exception {
    ChangeData change1 =
        pushFactory
            .create(admin.getIdent(), testRepo, "original subject", "a", "a1")
            .to("refs/for/master")
            .getChange();

    // Bootstrap: No NoteDb access as we expect it to use the index.
    changeRefCache.resetBootstrappedProjects();
    assertUploadPackRefs(
        "HEAD",
        "refs/heads/master",
        RefNames.changeMetaRef(change1.getId()),
        change1.currentPatchSet().getId().toRefName());

    // Delta reload: No index access as we expect it to use the NoteDb.
    ChangeData change2 =
        pushFactory
            .create(
                admin.getIdent(), testRepo, "subject2", "a", "a2", change1.change().getKey().get())
            .to("refs/for/master")
            .getChange();
    List<PatchSet> patchSets = ImmutableList.copyOf(change2.patchSets());
    assertThat(patchSets).hasSize(2);
    try (AutoCloseable ctx2 = disableChangeIndex()) {
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change1.getId()),
          patchSets.get(0).getId().toRefName(),
          patchSets.get(1).getId().toRefName());
    }
  }

  /**
   * Ensure we use only the change index for getting initial data populated and NoteDb for reloading
   * data that changed since.
   */
  @Test
  public void useDbForIterativeFetchingOnMetadataChange() throws Exception {
    ChangeData change1 =
        pushFactory
            .create(admin.getIdent(), testRepo, "original subject", "a", "a1")
            .to("refs/for/master")
            .getChange();
    // Bootstrap: No NoteDb access as we expect it to use the index.
    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      changeRefCache.resetBootstrappedProjects();
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change1.getId()),
          change1.currentPatchSet().getId().toRefName());
    } finally {
      enableDb(ctx);
    }

    try (AutoCloseable ignored = disableChangeIndex()) {
      // user can see public change
      setApiUser(user);
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change1.getId()),
          change1.currentPatchSet().getId().toRefName());
    }

    // Delta reload: No index access as we expect it to use the NoteDb.
    setApiUser(admin);
    gApi.changes().id(change1.getId().id).setPrivate(true);

    try (AutoCloseable ignored = disableChangeIndex()) {
      // user can't see private change from admin
      setApiUser(user);
      assertUploadPackRefs("HEAD", "refs/heads/master");
    }

    // admin adds the user as reviewer
    setApiUser(admin);
    gApi.changes().id(change1.getId().id).addReviewer(user.email);

    try (AutoCloseable ignored = disableChangeIndex()) {
      // Use can see private change
      setApiUser(user);
      assertUploadPackRefs(
          "HEAD",
          "refs/heads/master",
          RefNames.changeMetaRef(change1.getId()),
          change1.currentPatchSet().getId().toRefName());
    }
  }

  private void assertUploadPackRefs(String... expectedRefs) throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      assertRefs(repo, permissionBackend.user(user(user)).project(project), expectedRefs);
    }
  }

  private void assertRefs(
      Repository repo, PermissionBackend.ForProject forProject, String... expectedRefs)
      throws Exception {
    Map<String, Ref> all = getAllRefs(repo);
    assertThat(forProject.filter(all, repo, RefFilterOptions.defaults()).keySet())
        .containsExactlyElementsIn(expectedRefs);
  }

  private static Map<String, Ref> getAllRefs(Repository repo) throws IOException {
    return repo.getRefDatabase()
        .getRefs()
        .stream()
        .collect(toMap(Ref::getName, Function.identity()));
  }
}
