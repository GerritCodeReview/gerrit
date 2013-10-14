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

package com.google.gerrit.server.query.change;

import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.ChangeControl;

import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

@Ignore
public abstract class AbstractIndexQueryChangesTest
    extends AbstractQueryChangesTest {
  @Test
  public void byFileExact() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("file1", "contents1").add("file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertTrue(query("file:file").isEmpty());
    assertResultEquals(change, queryOne("file:file1"));
    assertResultEquals(change, queryOne("file:file2"));
  }

  @Test
  public void byFileRegex() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    RevCommit commit = repo.parseBody(
        repo.commit().message("one")
        .add("file1", "contents1").add("file2", "contents2")
        .create());
    Change change = newChange(repo, commit, null, null, null).insert();

    assertTrue(query("file:file.*").isEmpty());
    assertResultEquals(change, queryOne("file:^file.*"));
  }

  @Test
  public void byComment() throws Exception {
    TestRepository<InMemoryRepository> repo = createProject("repo");
    ChangeInserter ins = newChange(repo, null, null, null, null);
    Change change = ins.insert();
    ChangeControl ctl = changeControlFactory.controlFor(change, user);

    PostReview.Input input = new PostReview.Input();
    input.message = "toplevel";
    PostReview.Comment comment = new PostReview.Comment();
    comment.line = 1;
    comment.message = "inline";
    input.comments = ImmutableMap.<String, List<PostReview.Comment>> of(
        "Foo.java", ImmutableList.<PostReview.Comment> of(comment));
    postReview.apply(new RevisionResource(
        new ChangeResource(ctl), ins.getPatchSet()), input);

    assertTrue(query("comment:foo").isEmpty());
    assertResultEquals(change, queryOne("comment:toplevel"));
    assertResultEquals(change, queryOne("comment:inline"));
  }
}
