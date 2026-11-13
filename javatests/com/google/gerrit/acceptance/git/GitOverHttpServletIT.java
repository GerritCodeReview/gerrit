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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.UploadPackInitializer;
import com.google.gerrit.server.git.validators.UploadValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;
import org.junit.Test;

public class GitOverHttpServletIT extends AbstractGitOverHttpServlet {
  @Inject private DynamicSet<UploadPackInitializer> uploadPackInitializers;
  @Inject private ProjectOperations projectOperations;

  @Test
  @Sandboxed
  @GerritConfig(name = "pack.outputCacheDirectory", value = "cache/pack-output")
  public void cachedClonesShareAcrossAuthorizedUsersAndRetainValidation() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(ANONYMOUS_USERS))
        .update();
    ObjectId expected;
    try (TestRepository<Repository> remote =
        new TestRepository<>(repoManager.openRepository(project))) {
      expected = remote.branch("master").commit().message("Cache fixture").create().copy();
    }
    List<UploadPack> uploads = new CopyOnWriteArrayList<>();
    uploadPackInitializers.add("cache-test", (p, up) -> uploads.add(up));
    String authenticatedUrl = admin.getHttpUrl(server) + "/a/" + project.get();

    TestMetricMaker metrics = TestMetricMaker.getInstance();
    long hitsBefore = cacheMetric(metrics, "hits");
    long missesBefore = cacheMetric(metrics, "misses");
    long bypassesBefore = cacheMetric(metrics, "bypasses");
    long generatedBefore = cacheMetric(metrics, "generated_bytes");
    long servedBefore = cacheMetric(metrics, "served_bytes");

    // Default admission bypasses the first request; the second generates a cached pack.
    try (TestRepository<?> first = GitUtil.cloneProject(project, authenticatedUrl);
        TestRepository<?> second = GitUtil.cloneProject(project, authenticatedUrl);
        TestRepository<?> anonymous =
            GitUtil.cloneProject(project, server.getGitUrl() + "/" + project.get())) {
      for (TestRepository<?> clone : List.of(first, second, anonymous)) {
        assertThat(clone.getRepository().resolve("refs/remotes/origin/master")).isEqualTo(expected);
      }
    }
    List<UploadPack> completed = uploads.stream().filter(up -> up.getStatistics() != null).toList();
    assertThat(completed).hasSize(3);
    assertThat(completed.get(0).isPackOutputCacheHit()).isFalse();
    assertThat(completed.get(1).isPackOutputCacheHit()).isFalse();
    assertThat(completed.get(2).isPackOutputCacheHit()).isTrue();
    assertThat(cacheMetric(metrics, "bypasses")).isEqualTo(bypassesBefore + 1);
    assertThat(cacheMetric(metrics, "misses")).isEqualTo(missesBefore + 1);
    assertThat(cacheMetric(metrics, "hits")).isEqualTo(hitsBefore + 1);
    long generated =
        completed.get(0).getStatistics().getTotalBytes()
            + completed.get(1).getStatistics().getTotalBytes();
    assertThat(generated).isGreaterThan(0);
    assertThat(cacheMetric(metrics, "generated_bytes") - generatedBefore).isEqualTo(generated);
    assertThat(cacheMetric(metrics, "served_bytes") - servedBefore)
        .isEqualTo(generated + completed.get(2).getStatistics().getTotalBytes());

    // A warm cache must not bypass a newly installed validation rule.
    uploadValidationListenersSet.add(
        "cache-test-denial",
        new UploadValidationListener() {
          @Override
          public void onPreUpload(
              Repository repository,
              Project project,
              String remoteHost,
              UploadPack up,
              Collection<? extends ObjectId> wants,
              Collection<? extends ObjectId> haves)
              throws ValidationException {
            throw new ValidationException("Fetch denied by validation rule");
          }
        });
    TransportException error =
        assertThrows(
            TransportException.class, () -> GitUtil.cloneProject(project, authenticatedUrl));
    assertThat(error).hasMessageThat().contains("Fetch denied by validation rule");
    assertThat(cacheMetric(metrics, "hits")).isEqualTo(hitsBefore + 1);
  }

  private static long cacheMetric(TestMetricMaker metrics, String name) {
    return (long) metrics.getCallbackMetricValue("git/upload-pack/output_cache/" + name);
  }
}
