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

package com.google.gerrit.server.account.externalids;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.externalids.AllExternalIds.Serializer;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.SortedSet;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class ChromiumExternalIdTest {
  private static final String REPO_DIR = "/usr/local/google/home/dborowitz/w/chromium-all-users";
  private static final AllUsersName ALL_USERS = new AllUsersName("All-Users");

  @Test
  public void serializedSize() throws Exception {
    AllExternalIds allExtIds = readExternalIds();
    byte[] serialized = Serializer.INSTANCE.serialize(allExtIds);
    long size = serialized.length;
    System.out.format("Serialized size of current tip: %s\n", toMib(size));

    try (Repository repo = openRepo()) {
      Ref ref = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
      assertThat(ref).named("%s in %s", RefNames.REFS_EXTERNAL_IDS, REPO_DIR).isNotNull();
      int days = 30;
      int gcLimitDays = 60;
      LocalDateTime startLdt = LocalDateTime.now().minus(Duration.ofDays(days));
      Date start = Date.from(startLdt.atZone(ZoneId.systemDefault()).toInstant());
      int totalCommits =
          Iterables.size(new Git(repo).log().setRevFilter(CommitTimeRevFilter.after(start)).call());
      double commitsPerDay = ((double) totalCommits) / days;
      System.out.format("%.2f commits per day (%d per %dd)\n", commitsPerDay, totalCommits, days);
      double sizePerDay = commitsPerDay * size;
      System.out.format("%s per day\n", toMib(sizePerDay));
      System.out.format("%s per %sd\n", toMib(sizePerDay * gcLimitDays), gcLimitDays);
    }
  }

  private AllExternalIds readExternalIds() throws Exception {
    ExternalIdReader reader =
        new ExternalIdReader(new TestRepoManager(), ALL_USERS, new DisabledMetricMaker());
    return AllExternalIds.create(reader.all());
  }

  @Test
  public void deserializationTime() throws Exception {
    AllExternalIds allExtIds = readExternalIds();
    byte[] serialized = Serializer.INSTANCE.serialize(allExtIds);
    int runs = 200;
    int warmup = 100;
    long totalNanos = 0;
    for (int i = 0; i < runs + warmup; i++) {
      long startNanos = System.nanoTime();
      Serializer.INSTANCE.deserialize(serialized);
      if (i < warmup) {
        continue;
      }
      totalNanos += System.nanoTime() - startNanos;
    }
    System.out.format("%.2fms per run\n", ((double) totalNanos) / runs / 1e6);
  }

  private static class TestRepoManager implements GitRepositoryManager {
    @Override
    public Repository openRepository(Project.NameKey name) throws IOException {
      if (!name.equals(ALL_USERS)) {
        throw new RepositoryNotFoundException(name.get());
      }
      return openRepo();
    }

    @Override
    public Repository createRepository(Project.NameKey name) {
      throw new UnsupportedOperationException();
    }

    @Override
    public SortedSet<Project.NameKey> list() {
      return ImmutableSortedSet.of(ALL_USERS);
    }
  }

  private static Repository openRepo() throws IOException {
    File repoDir = FileKey.resolve(new File(REPO_DIR), FS.detect());
    if (repoDir == null) {
      throw new RepositoryNotFoundException(REPO_DIR);
    }
    return new FileRepository(repoDir);
  }

  private static String toMib(double bytes) {
    return String.format("%.2f MiB", bytes / 1024 / 1024);
  }
}
