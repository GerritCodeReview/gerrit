// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment.Status;
import com.google.gerrit.entities.CommentRange;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.Test;

public class ChangeRevisionNotesMigratorTest extends AbstractChangeNotesTest {
  private static final String IMPORTED_SERVER_ID = "imported-server-id";

  private static class InMemoryExternalIdCache implements ExternalIdCache {
    public static Module module() {
      return new AbstractModule() {
        @Override
        protected void configure() {
          bind(ExternalIdCache.class).to(InMemoryExternalIdCache.class);
        }
      };
    }

    private static final Map<ExternalId.Key, ExternalId> map = new HashMap<>();

    @Override
    public Optional<ExternalId> byKey(ExternalId.Key key) throws IOException {
      return Optional.ofNullable(map.get(key));
    }

    @Override
    public ImmutableSet<ExternalId> byAccount(Account.Id accountId) throws IOException {
      return ImmutableSet.<ExternalId>builder().build();
    }

    @Override
    public ImmutableSetMultimap<Account.Id, ExternalId> allByAccount() throws IOException {
      return ImmutableSetMultimap.<Account.Id, ExternalId>builder().build();
    }

    @Override
    public ImmutableSetMultimap<String, ExternalId> byEmails(String... emails) throws IOException {
      return ImmutableSetMultimap.<String, ExternalId>builder().build();
    }

    @Override
    public ImmutableSetMultimap<String, ExternalId> allByEmail() throws IOException {
      return ImmutableSetMultimap.<String, ExternalId>builder().build();
    }
  }

  private ChangeRevisionNotesMigrator.Factory processorFactory;

  @Override
  public void setUpTestEnvironment() throws Exception {
    super.setUpTestEnvironment();
    injector =
        createTestInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                install(InMemoryExternalIdCache.module());
                factory(ChangeRevisionNotesMigrator.Factory.class);
              }
            },
            serverId,
            IMPORTED_SERVER_ID);
    processorFactory = injector.getInstance(ChangeRevisionNotesMigrator.Factory.class);
  }

  @Test
  public void updateServerId() throws Exception {
    Change change = createChangeNoteWithExternalServerId();
    assertCommentServerId(change, IMPORTED_SERVER_ID);

    ChangeRevisionNotesMigrator processor = processorFactory.create(changeOwner, project);
    processor.migrate(this::updateServerId, () -> "test");

    assertCommentServerId(change, serverId);
  }

  private void assertCommentServerId(Change change, String expectedServerId) throws Exception {
    for (HumanComment comment : getRawHumanComments(change)) {
      assertThat(comment.serverId).isEqualTo(expectedServerId);
    }
  }

  private Map<ObjectId, ChangeRevisionNote> updateServerId(
      Map<ObjectId, ChangeRevisionNote> input) {
    for (Map.Entry<ObjectId, ChangeRevisionNote> entry : input.entrySet()) {
      for (HumanComment comment : entry.getValue().getEntities()) {
        comment.serverId = serverId;
      }
    }
    return input;
  }

  private Change createChangeNoteWithExternalServerId() throws Exception {
    Change change = newChange();
    RevCommit commit = tr.commit().message("Notes from another Gerrit").create();
    System.out.println("tr commit " + commit.name());
    ChangeUpdate update = newUpdate(change, changeOwner);
    update.putComment(
        HumanComment.Status.PUBLISHED,
        newComment(
            change.currentPatchSetId(),
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            TimeUtil.now(),
            "Comment on another Gerrit instance",
            (short) 1,
            commit,
            false,
            IMPORTED_SERVER_ID));
    ObjectId updateObjectId = update.commit();
    System.out.println("updated commit " + updateObjectId);

    return change;
  }

  private Collection<HumanComment> getRawHumanComments(Change change) throws Exception {
    List<HumanComment> comments = new ArrayList<>();
    ChangeNoteJson changeNoteJson = injector.getInstance(ChangeNoteJson.class);

    try (ObjectReader reader = repo.newObjectReader();
        TreeWalk tw = new TreeWalk(repo, repo.newObjectReader())) {
      Ref metaRef = repo.getRefDatabase().exactRef(RefNames.changeMetaRef(change.getId()));
      System.out.println("reading " + metaRef.toString());
      RevCommit metaTip = repo.parseCommit(metaRef.getObjectId());
      int treeId = tw.addTree(metaTip.getTree());
      while (tw.next()) {
        ObjectId blobId = tw.getObjectId(treeId);
        ChangeRevisionNote changeRevisionNote =
            new ChangeRevisionNote(changeNoteJson, reader, blobId, Status.PUBLISHED);
        ObjectLoader loader = reader.open(blobId);
        comments.addAll(changeRevisionNote.parse(loader.getBytes(), 0));
      }

      return comments;
    }
  }
}
