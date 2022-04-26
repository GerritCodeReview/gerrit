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
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.InvalidConfigFileException;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestTimeUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEditor.DeletePath;
import org.eclipse.jgit.dircache.DirCacheEditor.PathEdit;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VersionedMetaDataTest {
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
    project = Project.nameKey("repo");
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

  @Test
  public void batchRefUpdateWithRewrites() throws Exception {
    MyMetaData d1 = load("refs/meta/1", 0);

    try (BatchMetaDataUpdate batchUpdate = d1.openUpdate(newMetaDataUpdate(null))) {
      d1.setIncrement(1);
      batchUpdate.write(d1, newCommitBuilder());
      batchUpdate.commit();
    }

    assertMyMetaData(d1.getRefName(), 1, "Increment conf.value by 1");

    try (BatchMetaDataUpdate batchUpdate = d1.openUpdate(newMetaDataUpdate(null))) {
      d1.setIncrement(3);
      batchUpdate.write(d1, newCommitBuilder());
      batchUpdate.commit();
    }

    assertMyMetaData(d1.getRefName(), 4, "Increment conf.value by 1", "Increment conf.value by 3");

    BatchRefUpdate bru1 = repo.getRefDatabase().newBatchUpdate();
    MetaDataUpdate update = newMetaDataUpdate(bru1);
    try (BatchMetaDataUpdate batchUpdate = d1.openUpdate(update)) {
      d1.setIncrement(1);
      batchUpdate.write(d1, newCommitBuilder());

      batchUpdate.rewrite(new IncrementingRewriter(project, d1));

      d1.setIncrement(3);
      batchUpdate.write(d1, newCommitBuilder());

      assertThat(bru1.isAllowNonFastForwards()).isFalse();
      batchUpdate.commit();
    }

    assertThat(bru1.isAllowNonFastForwards()).isTrue();
    assertMyMetaData(d1.getRefName(), 4, "Increment conf.value by 1", "Increment conf.value by 3");
    assertThat(bru1.getCommands().stream().map(ReceiveCommand::getRefName))
        .containsExactly("refs/meta/1");
    RefUpdateUtil.executeChecked(bru1, repo);

    assertMyMetaData(
        d1.getRefName(),
        10,
        "Increment conf.value by 1",
        "Increment conf.value by 4",
        "Increment conf.value by 2",
        "Increment conf.value by 3");

    BatchRefUpdate bru2 = repo.getRefDatabase().newBatchUpdate();
    try (BatchMetaDataUpdate batch = d1.openUpdate(newMetaDataUpdate(bru2))) {
      d1.setIncrement(1);
      batch.write(d1, newCommitBuilder());

      d1.setIncrement(3);
      batch.write(d1, newCommitBuilder());

      batch.commit();
    }

    assertThat(bru2.isAllowNonFastForwards()).isFalse();
    assertMyMetaData(
        d1.getRefName(),
        10,
        "Increment conf.value by 1",
        "Increment conf.value by 4",
        "Increment conf.value by 2",
        "Increment conf.value by 3");
    assertThat(bru2.getCommands().stream().map(ReceiveCommand::getRefName))
        .containsExactly("refs/meta/1");
    RefUpdateUtil.executeChecked(bru2, repo);

    assertMyMetaData(
        d1.getRefName(),
        14,
        "Increment conf.value by 1",
        "Increment conf.value by 4",
        "Increment conf.value by 2",
        "Increment conf.value by 3",
        "Increment conf.value by 1",
        "Increment conf.value by 3");
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

  // TODO(issue-15517): Fix the JdkObsolete issue with Date once JGit's PersonIdent class supports
  // Instants
  @SuppressWarnings("JdkObsolete")
  private CommitBuilder newCommitBuilder() {
    CommitBuilder cb = new CommitBuilder();
    PersonIdent author =
        new PersonIdent("J. Author", "author@example.com", Date.from(TimeUtil.now()), TZ);
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
    static final String CONFIG_FILE = "my.config";
    static final String SECTION = "conf";
    static final String NAME = "value";

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
    protected void onLoad() throws IOException, ConfigInvalidException {
      Config cfg = readConfig(CONFIG_FILE);
      curr = cfg.getInt(SECTION, null, NAME, 0);
    }

    @Override
    protected void onRewrite(RevCommit newRevision) throws IOException, ConfigInvalidException {
      RevCommit backupRevision = revision;
      revision = newRevision;
      onLoad();
      revision = backupRevision;
    }

    int getValue() {
      return curr;
    }

    void setIncrement(int increment) {
      checkArgument(increment >= 0, "increment must be positive: %s", increment);
      this.increment = Optional.of(increment);
    }

    @Override
    protected boolean onSave(CommitBuilder cb) throws IOException, ConfigInvalidException {
      // Two ways to produce a no-op: don't call setIncrement, and call setIncrement(0);
      if (!increment.isPresent()) {
        return false;
      }
      Config cfg = readConfig(CONFIG_FILE);
      int incrementedValue = curr + increment.get();
      cfg.setInt(SECTION, null, NAME, incrementedValue);
      cb.setMessage(String.format("Increment %s.%s by %d", SECTION, NAME, increment.get()));
      saveConfig(CONFIG_FILE, cfg);
      increment = Optional.empty();
      curr = incrementedValue;
      return true;
    }
  }

  static class IncrementingRewriter implements VersionedMetaDataRewriter {

    private MyMetaData metaData;
    private NameKey project;

    public IncrementingRewriter(Project.NameKey project, MyMetaData metaData) {
      this.project = project;
      this.metaData = metaData;
    }

    private final Pattern PATTERN = Pattern.compile("Increment .*\\..* by (?<increment>\\d+).*");

    private ObjectReader reader;
    private ObjectInserter inserter;
    int increment = 1;

    @Override
    public ObjectId rewriteCommitHistory(
        RevWalk revWalk, ObjectInserter inserter, ObjectId currentTip)
        throws MissingObjectException, IncorrectObjectTypeException, IOException,
            ConfigInvalidException {
      this.inserter = inserter;
      checkArgument(!currentTip.equals(ObjectId.zeroId()));

      // Walk from the first commit of the branch.
      revWalk.reset();
      revWalk.markStart(revWalk.parseCommit(currentTip));
      revWalk.sort(RevSort.REVERSE);

      reader = revWalk.getObjectReader();

      RevCommit newTipCommit = revWalk.next(); // Don't rewrite the first commit.
      RevCommit originalCommit;
      while ((originalCommit = revWalk.next()) != null) {

        Config cfg;
        try {
          cfg = readConfig(originalCommit, MyMetaData.CONFIG_FILE);
        } catch (ConfigInvalidException e) {
          throw new IOException(e);
        }
        int curr = cfg.getInt(MyMetaData.SECTION, null, MyMetaData.NAME, 0);
        curr += increment;
        increment++;
        cfg.setInt(MyMetaData.SECTION, null, MyMetaData.NAME, curr);
        newTipCommit =
            revWalk.parseCommit(rewriteCommit(originalCommit, newTipCommit, inserter, cfg));
      }

      return newTipCommit;
    }

    private AnyObjectId rewriteCommit(
        RevCommit originalCommit, RevCommit parentCommit, ObjectInserter inserter, Config cfg)
        throws IOException {
      DirCache newTree = metaData.readTree(originalCommit.getTree());
      saveConfig(newTree, MyMetaData.CONFIG_FILE, cfg);

      CommitBuilder cb = new CommitBuilder();
      cb.setParentId(parentCommit);
      cb.setTreeId(newTree.writeTree(inserter));
      Matcher matcher = PATTERN.matcher(originalCommit.getFullMessage());
      matcher.matches();
      cb.setMessage(
          String.format(
              "Increment %s.%s by %d",
              MyMetaData.SECTION,
              MyMetaData.NAME,
              Integer.valueOf(matcher.group("increment")) + 1));
      cb.setCommitter(originalCommit.getCommitterIdent());
      cb.setAuthor(originalCommit.getAuthorIdent());
      cb.setEncoding(originalCommit.getEncoding());

      return inserter.insert(cb);
    }

    protected Config readConfig(RevCommit revision, String fileName)
        throws IOException, ConfigInvalidException {
      return readConfig(revision, fileName, Optional.empty());
    }

    protected Config readConfig(
        RevCommit revision, String fileName, Optional<? extends Config> baseConfig)
        throws IOException, ConfigInvalidException {
      Config rc = new Config(baseConfig.isPresent() ? baseConfig.get() : null);
      String text = readUTF8(revision, fileName);
      if (!text.isEmpty()) {
        try {
          rc.fromText(text);
        } catch (ConfigInvalidException err) {
          throw new InvalidConfigFileException(
              project, metaData.getRefName(), revision, fileName, err);
        }
      }
      return rc;
    }

    protected String readUTF8(RevCommit revision, String fileName) throws IOException {
      byte[] raw = readFile(revision, fileName);
      return raw.length != 0 ? RawParseUtils.decode(raw) : "";
    }

    protected byte[] readFile(RevCommit revision, String fileName) throws IOException {
      if (revision == null) {
        return new byte[] {};
      }

      try (TraceTimer timer =
              TraceContext.newTimer(
                  "Read file",
                  Metadata.builder()
                      .projectName(project.get())
                      .noteDbRefName(metaData.getRefName())
                      .revision(revision.name())
                      .noteDbFilePath(fileName)
                      .build());
          TreeWalk tw = TreeWalk.forPath(reader, fileName, revision.getTree())) {
        if (tw != null) {
          ObjectLoader obj = reader.open(tw.getObjectId(0), Constants.OBJ_BLOB);
          return obj.getCachedBytes(Integer.MAX_VALUE);
        }
      }
      return new byte[] {};
    }

    protected void saveConfig(DirCache newTree, String fileName, Config cfg) throws IOException {
      saveUTF8(newTree, fileName, cfg.toText());
    }

    protected void saveUTF8(DirCache newTree, String fileName, String text) throws IOException {
      saveFile(newTree, fileName, text != null ? Constants.encode(text) : null);
    }

    protected void saveFile(DirCache newTree, String fileName, byte[] raw) throws IOException {
      try (TraceTimer timer =
          TraceContext.newTimer(
              "Save file",
              Metadata.builder()
                  .projectName(project.get())
                  .noteDbRefName(metaData.getRefName())
                  .noteDbFilePath(fileName)
                  .build())) {
        DirCacheEditor editor = newTree.editor();
        if (raw != null && 0 < raw.length) {
          final ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, raw);
          editor.add(
              new PathEdit(fileName) {
                @Override
                public void apply(DirCacheEntry ent) {
                  ent.setFileMode(FileMode.REGULAR_FILE);
                  ent.setObjectId(blobId);
                }
              });
        } else {
          editor.add(new DeletePath(fileName));
        }
        editor.finish();
      }
    }
  }
}
