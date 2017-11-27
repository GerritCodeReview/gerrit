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
// limitations under the License

package com.google.gerrit.acceptance.rest.group;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.group.Rebuild;
import com.google.gerrit.server.group.db.GroupBundle;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class GroupsIT extends AbstractDaemonTest {
  @Inject private GroupsMigration groupsMigration;
  @Inject private GroupBundle.Factory bundleFactory;

  @Test
  public void invalidQueryOptions() throws Exception {
    RestResponse r = adminRestSession.put("/groups/?query=foo&query2=bar");
    r.assertBadRequest();
    assertThat(r.getEntityContent())
        .isEqualTo("\"query\" and \"query2\" options are mutually exclusive");
  }

  @Test
  public void rebuild() throws Exception {
    assume().that(groupsMigration.writeToNoteDb()).isTrue();
    assume().that(groupsMigration.readFromNoteDb()).isFalse();

    GroupInfo g = gApi.groups().create(name("group")).get();
    AccountGroup.UUID uuid = new AccountGroup.UUID(g.id);
    String refName = RefNames.refsGroups(uuid);
    ObjectId oldId;
    GroupBundle oldBundle;
    try (Repository repo = repoManager.openRepository(allUsers)) {
      oldId = repo.exactRef(refName).getObjectId();
      oldBundle = bundleFactory.fromNoteDb(repo, uuid);
      new TestRepository<>(repo).delete(refName);
    }

    assertThat(
            adminRestSession.postOK("/groups/" + uuid + "/rebuild", input(null)).getEntityContent())
        .isEqualTo("No differences between ReviewDb and NoteDb");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(refName);
      assertThat(ref).isNotNull();

      // An artifact of the migration process makes the SHA-1 different, but it's actually ok
      // because the bundles are equal.
      assertThat(ref.getObjectId()).isNotEqualTo(oldId);

      assertNoDifferences(oldBundle, bundleFactory.fromNoteDb(repo, uuid));
    }
  }

  @Test
  public void rebuildFailsWithWritesDisabled() throws Exception {
    assume().that(groupsMigration.writeToNoteDb()).isFalse();

    GroupInfo g = gApi.groups().create(name("group")).get();
    AccountGroup.UUID uuid = new AccountGroup.UUID(g.id);

    RestResponse res = adminRestSession.post("/groups/" + uuid + "/rebuild", input(null));
    assertThat(res.getStatusCode()).isEqualTo(405);
    assertThat(res.getEntityContent()).isEqualTo("NoteDb writes must be enabled");
  }

  @Test
  public void rebuildForceRequiresReadsDisabled() throws Exception {
    assume().that(groupsMigration.writeToNoteDb()).isTrue();
    assume().that(groupsMigration.readFromNoteDb()).isTrue();

    GroupInfo g = gApi.groups().create(name("group")).get();
    AccountGroup.UUID uuid = new AccountGroup.UUID(g.id);

    RestResponse res = adminRestSession.post("/groups/" + uuid + "/rebuild", input(true));
    assertThat(res.getStatusCode()).isEqualTo(405);
    assertThat(res.getEntityContent())
        .isEqualTo("NoteDb reads must not be enabled when force=true");
  }

  @Test
  public void rebuildWithoutForceFailsIfRefExists() throws Exception {
    assume().that(groupsMigration.writeToNoteDb()).isTrue();
    assume().that(groupsMigration.readFromNoteDb()).isFalse();

    GroupInfo g = gApi.groups().create(name("group")).get();
    AccountGroup.UUID uuid = new AccountGroup.UUID(g.id);
    String refName = RefNames.refsGroups(uuid);

    try (Repository repo = repoManager.openRepository(allUsers)) {
      new TestRepository<>(repo)
          .branch(refName)
          .commit()
          .add("somefile", "contents")
          .create()
          .copy();
    }

    RestResponse res = adminRestSession.post("/groups/" + uuid + "/rebuild", input(null));
    assertThat(res.getStatusCode()).isEqualTo(409);
    assertThat(res.getEntityContent()).isEqualTo("Rebuild failed with lock failure");
  }

  @Test
  public void rebuildForce() throws Exception {
    assume().that(groupsMigration.writeToNoteDb()).isTrue();
    assume().that(groupsMigration.readFromNoteDb()).isFalse();

    GroupInfo g = gApi.groups().create(name("group")).get();
    AccountGroup.UUID uuid = new AccountGroup.UUID(g.id);
    String refName = RefNames.refsGroups(uuid);

    ObjectId oldId;
    GroupBundle oldBundle;
    try (Repository repo = repoManager.openRepository(allUsers)) {
      oldBundle = bundleFactory.fromNoteDb(repo, uuid);

      oldId =
          new TestRepository<>(repo)
              .branch(refName)
              .commit()
              .add("somefile", "contents")
              .create()
              .copy();
    }

    assertThat(
            adminRestSession.postOK("/groups/" + uuid + "/rebuild", input(true)).getEntityContent())
        .isEqualTo("No differences between ReviewDb and NoteDb");

    try (Repository repo = repoManager.openRepository(allUsers)) {
      Ref ref = repo.exactRef(refName);
      assertThat(ref).isNotNull();

      // oldId contains some garbage, so rebuilt value should definitely be different.
      assertThat(ref.getObjectId()).isNotEqualTo(oldId);

      assertNoDifferences(oldBundle, bundleFactory.fromNoteDb(repo, uuid));
    }
  }

  private static void assertNoDifferences(GroupBundle expected, GroupBundle actual) {
    // Comparing NoteDb to NoteDb, so compare fields instead of using static compare method.
    assertThat(actual.group()).isEqualTo(expected.group());
    assertThat(actual.members()).isEqualTo(expected.members());
    assertThat(actual.memberAudit()).isEqualTo(expected.memberAudit());
    assertThat(actual.byId()).isEqualTo(expected.byId());
    assertThat(actual.byIdAudit()).isEqualTo(expected.byIdAudit());
  }

  private static Rebuild.Input input(Boolean force) {
    Rebuild.Input input = new Rebuild.Input();
    input.force = force;
    return input;
  }
}
