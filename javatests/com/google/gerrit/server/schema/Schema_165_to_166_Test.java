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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testing.AbstractSchemaUpgradeTest;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class Schema_165_to_166_Test extends AbstractSchemaUpgradeTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    Config cfg = new Config();
    cfg.setBoolean("noteDb", null, "writeJson", false);
    return cfg;
  }

  @Inject private AllUsersName allUsers;
  @Inject private GerritApi gApi;
  @Inject private GitRepositoryManager repoManager;
  @Inject private NotesMigration notesMigration;
  @Inject private Provider<IdentifiedUser> userProvider;
  @Inject private Schema_166 schema166;

  private SitePaths tmpSitePaths;
  private Account.Id accountId;
  private Project.NameKey project;
  private Change.Id changeId;
  private RevId revId;

  @Before
  public void setUp() throws Exception {
    // SchemaUpgradeTestEnvironment always uses InMemoryModule, which improperly uses "." as the
    // site path. We don't want to pollute the local filesystem, so manage our own temp site path
    // instead. Note that the actual config observed by the migration doesn't reflect config files
    // in this path; they can be thought of as simply outputs of the migration process.
    tmpSitePaths = new SitePaths(Files.createTempDirectory("gerrit_site_"));
    Files.createDirectory(tmpSitePaths.etc_dir);
    Files.write(
        tmpSitePaths.gerrit_config, "[gerrit]\n[noteDb]\nwriteJson = false".getBytes(UTF_8));
    Files.write(tmpSitePaths.notedb_config, "[noteDb]\nwriteJson = false".getBytes(UTF_8));

    accountId = userProvider.get().getAccountId();
    project = new Project.NameKey(gApi.projects().create("project").get().name);

    ChangeInput cin = new ChangeInput(project.get(), "master", "A change");
    cin.newBranch = true;

    ChangeInfo info = gApi.changes().create(cin).get(ListChangesOption.CURRENT_REVISION);
    changeId = new Change.Id(info._number);
    revId = new RevId(info.currentRevision);
  }

  @After
  public void tearDown() throws Exception {
    if (tmpSitePaths != null) {
      Path p = tmpSitePaths.site_path;
      tmpSitePaths = null;
      MoreFiles.deleteRecursively(p, RecursiveDeleteOption.ALLOW_INSECURE);
    }
  }

  @Test
  public void noOpWhenNoteDbDisabled() throws Exception {
    assume().that(notesMigration.commitChangeWrites()).isFalse();

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.changeMetaRef(changeId))).isNull();
    }

    String oldGerritConfig = readFile(tmpSitePaths.gerrit_config);
    String oldNoteDbConfig = readFile(tmpSitePaths.notedb_config);

    migrate();

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef(RefNames.changeMetaRef(changeId))).isNull();
    }

    assertThat(readFile(tmpSitePaths.gerrit_config)).isEqualTo(oldGerritConfig);
    assertThat(readFile(tmpSitePaths.notedb_config)).isEqualTo(oldNoteDbConfig);
  }

  @Test
  public void migratePublishedComments() throws Exception {
    assume().that(notesMigration.commitChangeWrites()).isTrue();

    CommentInput cin = new CommentInput();
    populate(cin);
    ReviewInput rin = new ReviewInput();
    rin.comments = ImmutableMap.of(cin.path, ImmutableList.of(cin));
    gApi.changes().id(changeId.get()).current().review(rin);

    assertLegacyFormat(project, RefNames.changeMetaRef(changeId), revId);
    migrate();
    assertJsonFormat(project, RefNames.changeMetaRef(changeId), revId);

    assertThat(readFile(tmpSitePaths.gerrit_config)).isEqualTo("[gerrit]\n[noteDb]\n");
    assertThat(readFile(tmpSitePaths.notedb_config)).isEqualTo("[noteDb]\n");
  }

  @Test
  public void migrateDraftComments() throws Exception {
    assume().that(notesMigration.commitChangeWrites()).isTrue();

    DraftInput din = new DraftInput();
    populate(din);
    gApi.changes().id(changeId.get()).current().createDraft(din);

    assertLegacyFormat(allUsers, RefNames.refsDraftComments(changeId, accountId), revId);
    migrate();
    assertJsonFormat(allUsers, RefNames.refsDraftComments(changeId, accountId), revId);

    assertThat(readFile(tmpSitePaths.gerrit_config)).isEqualTo("[gerrit]\n[noteDb]\n");
    assertThat(readFile(tmpSitePaths.notedb_config)).isEqualTo("[noteDb]\n");
  }

  private static void populate(Comment comment) {
    comment.patchSet = 1;
    comment.path = Patch.COMMIT_MSG;
    comment.message = "comment";
  }

  private void migrate() throws Exception {
    schema166.migrateData(tmpSitePaths, new TestUpdateUI());
  }

  private void assertLegacyFormat(Project.NameKey project, String refName, RevId revId)
      throws Exception {
    assertThat(readNote(project, refName, revId)).startsWith("Revision: ");
  }

  private void assertJsonFormat(Project.NameKey project, String refName, RevId revId)
      throws Exception {
    assertThat(readNote(project, refName, revId)).startsWith("{\n  \"comments\": [\n");
  }

  private String readNote(Project.NameKey project, String refName, RevId revId) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        ObjectReader reader = repo.newObjectReader();
        RevWalk rw = new RevWalk(reader)) {
      Ref ref = repo.exactRef(refName);
      assertThat(ref).isNotNull();

      NoteMap noteMap = NoteMap.read(reader, rw.parseCommit(ref.getObjectId()));
      ObjectId noteId = noteMap.getNote(ObjectId.fromString(revId.get())).getData();
      return new String(reader.open(noteId, Constants.OBJ_BLOB).getBytes(1 << 20), UTF_8);
    }
  }

  private static String readFile(Path p) throws Exception {
    return new String(Files.readAllBytes(p), UTF_8);
  }
}
