// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git.meta;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TestTimeUtil;
import java.util.Arrays;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VersionedMetaDataTest extends GerritBaseTests {
  // If you're considering fleshing out this test and making it more comprehensive, please consider
  // instead coming up with a replacement interface for
  // VersionedMetaData/BatchMetaDataUpdate/MetaDataUpdate that is easier to use correctly.

  private static final TimeZone TZ = TimeZone.getTimeZone("America/Los_Angeles");
  private static final String DEFAULT_REF = "refs/meta/config";

  private Project.NameKey project;
  private Repository repo;

  @Before
  public void setUp() {
    TestTimeUtil.resetWithClockStep(1, TimeUnit.SECONDS);
    project = new Project.NameKey("repo");
    repo = new InMemoryRepository(new DfsRepositoryDescription(project.get()));
  }

  @After
  public void tearDown() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void singleUpdate() throws Exception {
    MyMetaData d = load(0);
    d.setIncrement(3);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(3, "Increment conf.value by 3");
  }

  @Test
  public void noOpNoSetter() throws Exception {
    MyMetaData d = load(0);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(0);
  }

  @Test
  public void noOpWithSetter() throws Exception {
    MyMetaData d = load(0);
    d.setIncrement(0);
    d.commit(newMetaDataUpdate());
    // First commit is actually not a no-op because it creates an empty config file.
    assertMyMetaData(0, "Increment conf.value by 0");

    d = load(0);
    d.setIncrement(0);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(0, "Increment conf.value by 0");
  }

  @Test
  public void multipleSeparateUpdatesWithSameObject() throws Exception {
    MyMetaData d = load(0);
    d.setIncrement(1);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(1, "Increment conf.value by 1");
    d.setIncrement(2);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(3, "Increment conf.value by 1", "Increment conf.value by 2");
  }

  @Test
  public void multipleSeparateUpdatesWithDifferentObject() throws Exception {
    MyMetaData d = load(0);
    d.setIncrement(1);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(1, "Increment conf.value by 1");

    d = load(1);
    d.setIncrement(2);
    d.commit(newMetaDataUpdate());
    assertMyMetaData(3, "Increment conf.value by 1", "Increment conf.value by 2");
  }

  @Test
  public void multipleUpdatesInBatchWithSameObject() throws Exception {
    MyMetaData d = load(0);
    d.setIncrement(1);
    try (BatchMetaDataUpdate batch = d.openUpdate(newMetaDataUpdate())) {
      batch.write(d, newCommitBuilder());
      assertMyMetaData(0); // Batch not yet committed.

      d.setIncrement(2);
      batch.write(d, newCommitBuilder());
      batch.commit();
    }

    assertMyMetaData(3, "Increment conf.value by 1", "Increment conf.value by 2");
  }

  @Test
  public void multipleUpdatesSomeNoOps() throws Exception {
    MyMetaData d = load(0);
    d.setIncrement(1);
    try (BatchMetaDataUpdate batch = d.openUpdate(newMetaDataUpdate())) {
      batch.write(d, newCommitBuilder());
      assertMyMetaData(0); // Batch not yet committed.

      d.setIncrement(0);
      batch.write(d, newCommitBuilder());
      assertMyMetaData(0); // Batch not yet committed.

      d.setIncrement(3);
      batch.write(d, newCommitBuilder());
      batch.commit();
    }

    assertMyMetaData(4, "Increment conf.value by 1", "Increment conf.value by 3");
  }

  @Test
  public void sharedBatchRefUpdate() throws Exception {
    MyMetaData d1 = load("refs/meta/1", 0);
    MyMetaData d2 = load("refs/meta/2", 0);

    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
    try (BatchMetaDataUpdate batch1 = d1.openUpdate(newMetaDataUpdate(bru));
        BatchMetaDataUpdate batch2 = d2.openUpdate(newMetaDataUpdate(bru))) {
      d1.setIncrement(1);
      batch1.write(d1, newCommitBuilder());

      d2.setIncrement(2000);
      batch2.write(d2, newCommitBuilder());

      d1.setIncrement(3);
      batch1.write(d1, newCommitBuilder());

      d2.setIncrement(4000);
      batch2.write(d2, newCommitBuilder());

      batch1.commit();
      batch2.commit();
    }

    assertMyMetaData(d1.getRefName(), 0);
    assertMyMetaData(d2.getRefName(), 0);
    assertThat(bru.getCommands().stream().map(ReceiveCommand::getRefName))
        .containsExactly("refs/meta/1", "refs/meta/2");
    RefUpdateUtil.executeChecked(bru, repo);

    assertMyMetaData(d1.getRefName(), 4, "Increment conf.value by 1", "Increment conf.value by 3");
    assertMyMetaData(
        d2.getRefName(), 6000, "Increment conf.value by 2000", "Increment conf.value by 4000");
  }

  private MyMetaData load(int expectedValue) throws Exception {
    return load(DEFAULT_REF, expectedValue);
  }

  private MyMetaData load(String ref, int expectedValue) throws Exception {
    MyMetaData d = new MyMetaData(ref);
    d.load(project, repo);
    assertThat(d.getValue()).isEqualTo(expectedValue);
    return d;
  }

  private MetaDataUpdate newMetaDataUpdate() {
    return newMetaDataUpdate(null);
  }

  private MetaDataUpdate newMetaDataUpdate(@Nullable BatchRefUpdate bru) {
    MetaDataUpdate u = new MetaDataUpdate(GitReferenceUpdated.DISABLED, project, repo, bru);
    CommitBuilder cb = newCommitBuilder();
    u.getCommitBuilder().setAuthor(cb.getAuthor());
    u.getCommitBuilder().setCommitter(cb.getCommitter());
    return u;
  }

  private CommitBuilder newCommitBuilder() {
    CommitBuilder cb = new CommitBuilder();
    PersonIdent author = new PersonIdent("J. Author", "author@example.com", TimeUtil.nowTs(), TZ);
    cb.setAuthor(author);
    cb.setCommitter(
        new PersonIdent(
            "M. Committer", "committer@example.com", author.getWhen(), author.getTimeZone()));
    return cb;
  }

  private void assertMyMetaData(String ref, int expectedValue, String... expectedLog)
      throws Exception {
    MyMetaData d = load(ref, expectedValue);
    assertThat(log(d)).containsExactlyElementsIn(Arrays.asList(expectedLog)).inOrder();
  }

  private void assertMyMetaData(int expectedValue, String... expectedLog) throws Exception {
    assertMyMetaData(DEFAULT_REF, expectedValue, expectedLog);
  }

  private ImmutableList<String> log(MyMetaData d) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      Ref ref = repo.exactRef(d.getRefName());
      if (ref == null) {
        return ImmutableList.of();
      }
      rw.sort(RevSort.REVERSE);
      rw.setRetainBody(true);
      rw.markStart(rw.parseCommit(ref.getObjectId()));
      return Streams.stream(rw).map(RevCommit::getFullMessage).collect(toImmutableList());
    }
  }

  private static class MyMetaData extends VersionedMetaData {
    private static final String CONFIG_FILE = "my.config";
    private static final String SECTION = "conf";
    private static final String NAME = "value";

    private final String ref;

    MyMetaData(String ref) {
      this.ref = ref;
    }

    @Override
    protected String getRefName() {
      return ref;
    }

    private int curr;
    private Optional<Integer> increment = Optional.empty();

    @Override
    protected void onLoad() throws ConfigInvalidException {
      Config cfg = readConfig(CONFIG_FILE);
      curr = cfg.getInt(SECTION, null, NAME, 0);
    }

    int getValue() {
      return curr;
    }

    void setIncrement(int increment) {
      checkArgument(increment >= 0, "increment must be positive: %s", increment);
      this.increment = Optional.of(increment);
    }

    @Override
    protected boolean onSave(CommitBuilder cb) throws ConfigInvalidException {
      // Two ways to produce a no-op: don't call setIncrement, and call setIncrement(0);
      if (!increment.isPresent()) {
        return false;
      }
      Config cfg = readConfig(CONFIG_FILE);
      cfg.setInt(SECTION, null, NAME, cfg.getInt(SECTION, null, NAME, 0) + increment.get());
      cb.setMessage(String.format("Increment %s.%s by %d", SECTION, NAME, increment.get()));
      saveConfig(CONFIG_FILE, cfg);
      increment = Optional.empty();
      return true;
    }
  }
}
