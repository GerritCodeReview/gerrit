// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.IndexResource;
import com.google.gerrit.server.index.account.AccountIndexDefinition;
import com.google.gerrit.server.index.change.ChangeIndexDefinition;
import com.google.gerrit.server.index.group.GroupIndexDefinition;
import com.google.gerrit.server.index.project.ProjectIndexDefinition;
import com.google.gerrit.server.restapi.config.SnapshotIndex;
import com.google.gerrit.server.restapi.config.SnapshotIndexes;
import com.google.gerrit.server.restapi.config.SnapshotInfo;
import com.google.gerrit.testing.SystemPropertiesTestRule;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.ClassRule;
import org.junit.Test;

public class IndexSnapshotsIT extends AbstractDaemonTest {

  @ClassRule
  public static SystemPropertiesTestRule systemProperties =
      new SystemPropertiesTestRule(IndexType.SYS_PROP, "lucene");

  @Inject private SnapshotIndex snapshotIndex;
  @Inject private SnapshotIndexes snapshotIndexes;
  @Inject private AccountIndexDefinition accountIndexDefinition;
  @Inject private ChangeIndexDefinition changeIndexDefinition;
  @Inject private GroupIndexDefinition groupIndexDefinition;
  @Inject private ProjectIndexDefinition projectIndexDefinition;

  @Test
  @UseLocalDisk
  public void createAccountsIndexSnapshot() throws Exception {
    Query query = new TermQuery(new Term("is", "active"));
    createAndVerifySnapshot(new IndexResource(accountIndexDefinition), "accounts", query);
  }

  @Test
  @UseLocalDisk
  public void createFullSnapshot() throws Exception {
    File snapshot = createSnapshotOfAllIndexes();
    File[] members = snapshot.listFiles();
    for (File member : members) {
      assertThat(member.isDirectory()).isTrue();
      verifyIndexCanBeOpen(member);
    }
  }

  @Test
  @UseLocalDisk
  public void createChangesIndexSnapshot() throws Exception {
    Query query = new TermQuery(new Term("status", "open"));
    createAndVerifySnapshot(new IndexResource(changeIndexDefinition), "changes", query);
  }

  @Test
  @UseLocalDisk
  public void createGroupsIndexSnapshot() throws Exception {
    Query query = new TermQuery(new Term("is", "active"));
    createAndVerifySnapshot(new IndexResource(groupIndexDefinition), "groups", query);
  }

  @Test
  @UseLocalDisk
  public void createProjectsIndexSnapshot() throws Exception {
    Query query = new TermQuery(new Term("name", "foo"));
    createAndVerifySnapshot(new IndexResource(projectIndexDefinition), "projects", query);
  }

  private File createAndVerifySnapshot(IndexResource rsrc, String prefix, Query query)
      throws IOException {
    File snapshot = createSnapshot(rsrc);

    File[] subdirs = snapshot.listFiles();
    Collection<? extends Index<?, ?>> indexes =
        rsrc.getIndexDefinition().getIndexCollection().getWriteIndexes();
    assertThat(subdirs).hasLength(indexes.size());
    for (Index<?, ?> i : indexes) {
      String indexDirName = String.format("%s_%04d", prefix, i.getSchema().getVersion());
      File[] result = snapshot.listFiles((d, n) -> n.equals(indexDirName));
      assertThat(result).hasLength(1);
      File accountsIndexSnapshot = result[0];
      openIndexAndQuery(accountsIndexSnapshot, query);
    }
    return snapshot;
  }

  private File createSnapshot(IndexResource rsrc) throws IOException {
    Response<?> rsp = snapshotIndex.apply(rsrc, new SnapshotIndex.Input());
    return verifySnapshot(rsp);
  }

  private File createSnapshotOfAllIndexes() throws IOException {
    Response<?> rsp = snapshotIndexes.apply(new ConfigResource(), new SnapshotIndexes.Input());
    return verifySnapshot(rsp);
  }

  private File verifySnapshot(Response<?> rsp) {
    assertThat(rsp.value()).isInstanceOf(SnapshotInfo.class);
    SnapshotInfo snapshotInfo = (SnapshotInfo) rsp.value();
    Path snapshotDir = sitePaths.index_dir.resolve("snapshots").resolve(snapshotInfo.id);
    File snapshot = snapshotDir.toFile();
    assertThat(snapshot.exists()).isTrue();
    assertThat(snapshot.isDirectory()).isTrue();
    return snapshot;
  }

  private void verifyIndexCanBeOpen(File indexDir) throws IOException {
    createIndex(indexDir).tryOpen();
  }

  private void openIndexAndQuery(File indexDir, Query query) throws IOException {
    BaseIndex index = createIndex(indexDir);
    index.openAndQuery(query);
  }

  private BaseIndex createIndex(File indexDir) {
    BaseIndex index;
    if (indexDir.getName().startsWith("changes")) {
      index = new ChangeIndex(indexDir);
    } else {
      index = new SimpleIndex(indexDir);
    }
    return index;
  }

  private abstract static class BaseIndex {
    protected File indexDir;

    BaseIndex(File indexDir) {
      this.indexDir = indexDir;
    }

    abstract void tryOpen() throws IOException;

    abstract void openAndQuery(Query query) throws IOException;
  }

  private static class SimpleIndex extends BaseIndex {
    SimpleIndex(File indexDir) {
      super(indexDir);
    }

    @Override
    void tryOpen() throws IOException {
      Directory index = FSDirectory.open(indexDir.toPath());
      try (IndexReader reader = DirectoryReader.open(index)) {}
    }

    @Override
    void openAndQuery(Query query) throws IOException {
      Directory index = FSDirectory.open(indexDir.toPath());
      try (IndexReader reader = DirectoryReader.open(index)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs result = searcher.search(query, 10);
        System.out.printf("query result length = %d\n", result.scoreDocs.length);
      }
    }
  }

  private static class ChangeIndex extends BaseIndex {
    private SimpleIndex open;
    private SimpleIndex closed;

    ChangeIndex(File indexDir) {
      super(indexDir);
      File[] subDirs = indexDir.listFiles();
      for (File subDir : subDirs) {
        String name = subDir.getName();
        if (name.equals("open")) {
          this.open = new SimpleIndex(subDir);
        } else if (name.equals("closed")) {
          this.closed = new SimpleIndex(subDir);
        } else {
          throw new IllegalStateException("Unexpected subdir in changes index " + name);
        }
      }
    }

    @Override
    void tryOpen() throws IOException {
      open.tryOpen();
      closed.tryOpen();
    }

    @Override
    void openAndQuery(Query query) throws IOException {
      open.openAndQuery(query);
      closed.openAndQuery(query);
    }
  }
}
