// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AtomicEntry;
import com.google.gerrit.reviewdb.AtomicEntryAccess;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Subscription;
import com.google.gerrit.reviewdb.SubscriptionAccess;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Provider;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubmoduleOpTest extends LocalDiskRepositoryTestCase {

  private static final String newLine = System.getProperty("line.separator");

  private SchemaFactory<ReviewDb> schemaFactory;
  private SubscriptionAccess subscriptions;
  private ReviewDb schema;
  private Provider<String> urlProvider;
  private AtomicEntryAccess atomicEntries;
  private GitRepositoryManager repoManager;
  private ReplicationQueue replication;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    schemaFactory = createStrictMock(SchemaFactory.class);
    schema = createStrictMock(ReviewDb.class);
    subscriptions = createStrictMock(SubscriptionAccess.class);
    urlProvider = createStrictMock(Provider.class);
    atomicEntries = createStrictMock(AtomicEntryAccess.class);
    repoManager = createStrictMock(GitRepositoryManager.class);
    replication = createStrictMock(ReplicationQueue.class);
  }

  private void doReplay() {
    replay(schemaFactory, schema, subscriptions, urlProvider, atomicEntries, repoManager,
        replication);
  }

  private void doVerify() {
    verify(schemaFactory, schema, subscriptions, urlProvider, atomicEntries, repoManager,
        replication);
  }

  /**
   * It tests Submodule.update in the scenario a merged commit is an empty one
   * (it does not have a .gitmodule file) and the project the commit was merged
   * is not a submodule of other project.
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testEmptyCommit() throws Exception {
    expect(schemaFactory.open()).andReturn(schema);

    final Repository realDb = createWorkRepository();
    final Git git = new Git(realDb);

    final RevCommit mergeTip = git.commit().setMessage("test").call();

    final Branch.NameKey branchNameKey =
        new Branch.NameKey(new Project.NameKey("test-project"), "test-branch");

    expect(schema.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(mergeTip.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(schema.subscriptions()).andReturn(subscriptions);
    final ResultSet<Subscription> emptySubscriptions =
        new ListResultSet<Subscription>(new ArrayList<Subscription>());
    expect(subscriptions.getSubscribers(branchNameKey)).andReturn(
        emptySubscriptions);

    schema.close();

    doReplay();

    final SubmoduleOp submoduleOp =
        new SubmoduleOp(branchNameKey, mergeTip, new RevWalk(realDb), null,
            schemaFactory, realDb, null, new ArrayList<Change>(), null, null,
            null, null);

    submoduleOp.update();

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>no subscriptions existing to destination project</li>
   * <li>a commit is merged to "dest-project"</li>
   * <li>commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "source"]
   *       path = source
   *       url = http://localhost:8080/source
   *       revision = .
   * </pre>
   * <p>
   * It expects to insert a new row in subscriptions table. The row inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "a" on branch "refs/heads/master"</li>
   * <li>path "a"</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testNewSubscriptionToDotRevisionValue() throws Exception {
    final StringBuilder gitModulesBuilder = new StringBuilder();
    gitModulesBuilder.append("[submodule \"source\"]");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\tpath = source");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\turl = http://localhost:8080/source");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\trevision = .");
    gitModulesBuilder.append(newLine);

    doOneSubscriptionInsert(gitModulesBuilder.toString(), "refs/heads/master");

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>no subscriptions existing to destination project</li>
   * <li>a commit is merged to "dest-project"</li>
   * <li>commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "source"]
   *       path = source
   *       url = http://localhost:8080/source
   *       revision = refs/heads/master
   * </pre>
   *
   * <p>
   * It expects to insert a new row in subscriptions table. The row inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "source" on branch "refs/heads/master"</li>
   * <li>path "source"</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testNewSubscriptionToSameRevision() throws Exception {
    final StringBuilder gitModulesBuilder = new StringBuilder();
    gitModulesBuilder.append("[submodule \"source\"]");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\tpath = source");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\turl = http://localhost:8080/source");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\trevision = refs/heads/master");
    gitModulesBuilder.append(newLine);

    doOneSubscriptionInsert(gitModulesBuilder.toString(), "refs/heads/master");

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>no subscriptions existing to destination project</li>
   * <li>a commit is merged to "dest-project"</li>
   * <li>commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "source"]
   *       path = source
   *       url = http://localhost:8080/source
   *       revision = refs/heads/test
   * </pre>
   * <p>
   * It expects to insert a new row in subscriptions table. The row inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "source" on branch "refs/heads/test"</li>
   * <li>path "source"</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testNewSubscriptionToDifferentRevision() throws Exception {
    final StringBuilder gitModulesBuilder = new StringBuilder();
    gitModulesBuilder.append("[submodule \"source\"]");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\tpath = source");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\turl = http://localhost:8080/source");
    gitModulesBuilder.append(newLine);
    gitModulesBuilder.append("\trevision = refs/heads/test");
    gitModulesBuilder.append(newLine);

    doOneSubscriptionInsert(gitModulesBuilder.toString(), "refs/heads/test");

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>no subscriptions existing to destination project</li>
   * <li>a commit is merged to "dest-project" in "refs/heads/master" branch</li>
   * <li>commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "source-a"]
   *       path = source-a
   *       url = http://localhost:8080/source-a
   *       revision = .
   *
   *     [submodule "source-b"]
   *       path = source-b
   *       url = http://localhost:8080/source-b
   *       revision = .
   * </pre>
   * <p>
   * It expects to insert new rows in subscriptions table. The rows inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "source-a" on branch "refs/heads/master" with "source-a" path</li>
   * <li>source "source-b" on branch "refs/heads/master" with "source-b" path</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testNewSubscriptionsWithDotRevisionValue() throws Exception {
    final StringBuilder sb = new StringBuilder();

    sb.append("[submodule \"source-a\"]");
    sb.append(newLine);
    sb.append("\tpath = source-a");
    sb.append(newLine);
    sb.append("\turl = http://localhost:8080/source-a");
    sb.append(newLine);
    sb.append("\trevision = .");
    sb.append(newLine);

    sb.append("[submodule \"source-b\"]");
    sb.append(newLine);
    sb.append("\tpath = source-b");
    sb.append(newLine);
    sb.append("\turl = http://localhost:8080/source-b");
    sb.append(newLine);
    sb.append("\trevision = .");
    sb.append(newLine);

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<Subscription> subscriptionsToInsert =
        new ArrayList<Subscription>();
    subscriptionsToInsert
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/master"), "source-a"));
    subscriptionsToInsert
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-b"), "refs/heads/master"), "source-b"));

    doOnlySubscriptionInserts(sb.toString(), mergedBranch,
        subscriptionsToInsert);

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>no subscriptions existing to destination project</li>
   * <li>a commit is merged to "dest-project" in "refs/heads/master" branch</li>
   * <li>commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "source-a"]
   *       path = source-a
   *       url = http://localhost:8080/source-a
   *       revision = .
   *
   *     [submodule "source-b"]
   *       path = source-b
   *       url = http://localhost:8080/source-b
   *       revision = refs/heads/master
   * </pre>
   * <p>
   * It expects to insert new rows in subscriptions table. The rows inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "source-a" on branch "refs/heads/master" with "source-a" path</li>
   * <li>source "source-b" on branch "refs/heads/master" with "source-b" path</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testNewSubscriptionsDotAndSameRevisionValues() throws Exception {
    final StringBuilder sb = new StringBuilder();

    sb.append("[submodule \"source-a\"]");
    sb.append(newLine);
    sb.append("\tpath = source-a");
    sb.append(newLine);
    sb.append("\turl = http://localhost:8080/source-a");
    sb.append(newLine);
    sb.append("\trevision = .");
    sb.append(newLine);

    sb.append("[submodule \"source-b\"]");
    sb.append(newLine);
    sb.append("\tpath = source-b");
    sb.append(newLine);
    sb.append("\turl = http://localhost:8080/source-b");
    sb.append(newLine);
    sb.append("\trevision = refs/heads/master");
    sb.append(newLine);

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<Subscription> subscriptionsToInsert =
        new ArrayList<Subscription>();
    subscriptionsToInsert
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/master"), "source-a"));
    subscriptionsToInsert
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-b"), "refs/heads/master"), "source-b"));

    doOnlySubscriptionInserts(sb.toString(), mergedBranch,
        subscriptionsToInsert);

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>no subscriptions existing to destination project</li>
   * <li>a commit is merged to "dest-project" in "refs/heads/master" branch</li>
   * <li>commit contains .gitmodules file with content</li>
   *
   * <pre>
   *     [submodule "source-a"]
   *       path = source-a
   *       url = http://localhost:8080/source-a
   *       revision = refs/heads/test-a
   *
   *     [submodule "source-b"]
   *       path = source-b
   *       url = http://localhost:8080/source-b
   *       revision = refs/heads/test-b
   * </pre>
   *
   * <p>
   * It expects to insert new rows in subscriptions table. The rows inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "source-a" on branch "refs/heads/test-a" with "source-a" path</li>
   * <li>source "source-b" on branch "refs/heads/test-b" with "source-b" path</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testNewSubscriptionsSpecificRevisionValues() throws Exception {
    final StringBuilder sb = new StringBuilder();

    sb.append("[submodule \"source-a\"]");
    sb.append(newLine);
    sb.append("\tpath = source-a");
    sb.append(newLine);
    sb.append("\turl = http://localhost:8080/source-a");
    sb.append(newLine);
    sb.append("\trevision = refs/heads/test-a");
    sb.append(newLine);

    sb.append("[submodule \"source-b\"]");
    sb.append(newLine);
    sb.append("\tpath = source-b");
    sb.append(newLine);
    sb.append("\turl = http://localhost:8080/source-b");
    sb.append(newLine);
    sb.append("\trevision = refs/heads/test-b");
    sb.append(newLine);

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<Subscription> subscriptionsToInsert =
        new ArrayList<Subscription>();
    subscriptionsToInsert
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/test-a"), "source-a"));
    subscriptionsToInsert
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-b"), "refs/heads/test-b"), "source-b"));

    doOnlySubscriptionInserts(sb.toString(), mergedBranch,
        subscriptionsToInsert);

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>one subscription existing to destination project/branch</li>
   * <li>a commit is merged to "dest-project" in "refs/heads/master" branch</li>
   * <li>commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "source"]
   *       path = source
   *       url = http://localhost:8080/source
   *       revision = refs/heads/master
   * </pre>
   * <p>
   * It expects to insert a new row in subscriptions table. The rows inserted
   * specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "source" on branch "refs/heads/master" with "source" path</li>
   * </ul>
   * </p>
   * <p>
   * It also expects to remove the row in subscriptions table specifying another
   * project/branch subscribed to merged branch. This one to be removed is:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "old-source" on branch "refs/heads/master" with "old-source"
   * path</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testSubscriptionsInsertOneRemoveOne() throws Exception {
    final StringBuilder sbGitModulesContent = new StringBuilder();
    sbGitModulesContent.append("[submodule \"source\"]");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\tpath = source");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\turl = http://localhost:8080/source");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\trevision = refs/heads/master");
    sbGitModulesContent.append(newLine);

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<Subscription> subscriptionsToInsert =
        new ArrayList<Subscription>();
    subscriptionsToInsert.add(new Subscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("source"), "refs/heads/master"),
        "source"));

    final List<Subscription> oldOnesToMergedBranch =
        new ArrayList<Subscription>();
    oldOnesToMergedBranch.add(new Subscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("old-source"),
            "refs/heads/master"), "old-source"));

    doOnlySubscriptionTableOperations(sbGitModulesContent.toString(),
        mergedBranch, subscriptionsToInsert, oldOnesToMergedBranch);

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering:
   * <ul>
   * <li>one subscription existing to destination project/branch with a source
   * called old on refs/heads/master branch</li>
   * <li>a commit is merged to "dest-project" in "refs/heads/master" branch</li>
   * <li>
   * commit contains .gitmodules file with content</li>
   * </ul>
   *
   * <pre>
   *     [submodule "new"]
   *       path = new
   *       url = http://localhost:8080/new
   *       revision = refs/heads/master
   *
   *     [submodule "old"]
   *       path = old
   *       url = http://localhost:8080/old
   *       revision = refs/heads/master
   * </pre>
   * <p>
   * It expects to insert a new row in subscriptions table. It should not remove
   * any row. The rows inserted specifies:
   * <ul>
   * <li>target "dest-project" on branch "refs/heads/master"</li>
   * <li>source "new" on branch "refs/heads/master" with "new" path</li>
   * </ul>
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testSubscriptionAddedAndMantainPreviousOne() throws Exception {
    final StringBuilder sbGitModulesContent = new StringBuilder();
    sbGitModulesContent.append("[submodule \"new\"]");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\tpath = new");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\turl = http://localhost:8080/new");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\trevision = refs/heads/master");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("[submodule \"old\"]");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\tpath = old");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\turl = http://localhost:8080/old");
    sbGitModulesContent.append(newLine);
    sbGitModulesContent.append("\trevision = refs/heads/master");
    sbGitModulesContent.append(newLine);

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final Subscription old =
        new Subscription(mergedBranch, new Branch.NameKey(new Project.NameKey(
            "old"), "refs/heads/master"), "old");

    final List<Subscription> extractedsubscriptions =
        new ArrayList<Subscription>();
    extractedsubscriptions.add(new Subscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("new"), "refs/heads/master"),
        "new"));
    extractedsubscriptions.add(old);

    final List<Subscription> oldOnesToMergedBranch =
        new ArrayList<Subscription>();
    oldOnesToMergedBranch.add(old);

    doOnlySubscriptionTableOperations(sbGitModulesContent.toString(),
        mergedBranch, extractedsubscriptions, oldOnesToMergedBranch);

    doVerify();
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering an empty .gitmodules
   * file is part of a commit to a destination project/branch having two sources
   * subscribed.
   * <p>
   * It expects to remove the subscriptions to destination project/branch.
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testRemoveSubscriptions() throws Exception {
    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<Subscription> extractedsubscriptions =
        new ArrayList<Subscription>();

    final List<Subscription> oldOnesToMergedBranch =
        new ArrayList<Subscription>();
    oldOnesToMergedBranch
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/master"), "source-a"));
    oldOnesToMergedBranch
        .add(new Subscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-b"), "refs/heads/master"), "source-b"));

    doOnlySubscriptionTableOperations("", mergedBranch, extractedsubscriptions,
        oldOnesToMergedBranch);
  }

  /**
   * It tests SubmoduleOp.update in a scenario considering no .gitmodules file
   * in a merged commit to a destination project/branch that is a source one to
   * one called "target-project".
   * <p>
   * It expects to update the git link called "source-project" to be in target
   * repository.
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testOneSubscriberToUpdate() throws Exception {
    expect(schemaFactory.open()).andReturn(schema);

    final Repository sourceRepository = createWorkRepository();
    final Git sourceGit = new Git(sourceRepository);

    addRegularFileToIndex("file.txt", "test content", sourceRepository);

    final RevCommit sourceMergeTip =
        sourceGit.commit().setMessage("test").call();

    final Branch.NameKey sourceBranchNameKey =
        new Branch.NameKey(new Project.NameKey("source-project"),
            "refs/heads/master");

    final CodeReviewCommit codeReviewCommit =
        new CodeReviewCommit(sourceMergeTip.toObjectId());
    final Change submitedChange =
        new Change(new Change.Key(sourceMergeTip.toObjectId().getName()),
            new Change.Id(1), new Account.Id(1), sourceBranchNameKey);
    codeReviewCommit.change = submitedChange;

    final Map<Change.Id, CodeReviewCommit> mergedCommits =
        new HashMap<Change.Id, CodeReviewCommit>();
    mergedCommits.put(codeReviewCommit.change.getId(), codeReviewCommit);

    final List<Change> submited = new ArrayList<Change>();
    submited.add(submitedChange);

    final Repository targetRepository = createWorkRepository();
    final Git targetGit = new Git(targetRepository);

    addGitLinkToIndex("a", sourceMergeTip.copy(), targetRepository);

    targetGit.commit().setMessage("test").call();

    final Branch.NameKey targetBranchNameKey =
        new Branch.NameKey(new Project.NameKey("target-project"),
            sourceBranchNameKey.get());

    expect(schema.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(sourceMergeTip.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(schema.subscriptions()).andReturn(subscriptions);
    final ResultSet<Subscription> subscribers =
        new ListResultSet<Subscription>(Collections
            .singletonList(new Subscription(targetBranchNameKey,
                sourceBranchNameKey, "source-project")));
    expect(subscriptions.getSubscribers(sourceBranchNameKey)).andReturn(
        subscribers);

    expect(repoManager.openRepository(targetBranchNameKey.getParentKey()))
        .andReturn(targetRepository);

    replication.scheduleUpdate(targetBranchNameKey.getParentKey(),
        targetBranchNameKey.get());

    expect(schema.subscriptions()).andReturn(subscriptions);
    final ResultSet<Subscription> emptySubscriptions =
        new ListResultSet<Subscription>(new ArrayList<Subscription>());
    expect(subscriptions.getSubscribers(targetBranchNameKey)).andReturn(
        emptySubscriptions);

    schema.close();

    final PersonIdent myIdent =
        new PersonIdent("test-user", "test-user@email.com");

    doReplay();

    final SubmoduleOp submoduleOp =
        new SubmoduleOp(sourceBranchNameKey, sourceMergeTip, new RevWalk(
            sourceRepository), urlProvider, schemaFactory, sourceRepository,
            new Project(sourceBranchNameKey.getParentKey()), submited,
            mergedCommits, myIdent, repoManager, replication);

    submoduleOp.update();

    doVerify();
  }

  /**
   * It calls SubmoduleOp.update considering only one insert on Subscriptions
   * table.
   * <p>
   * It considers a commit containing a .gitmodules file was merged in
   * refs/heads/master of a dest-project.
   * </p>
   * <p>
   * The .gitmodules file content should indicate a source project called
   * "source".
   * </p>
   *
   * @param gitModulesFileContent The .gitmodules file content. During the test
   *        this file is created, so the commit containing it.
   * @param sourceBranchName The branch name of source project "pointed by"
   *        .gitmodule file.
   * @throws Exception If an exception occurs.
   */
  private void doOneSubscriptionInsert(final String gitModulesFileContent,
      final String sourceBranchName) throws Exception {
    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<Subscription> subscriptionsToInsert =
        new ArrayList<Subscription>();
    subscriptionsToInsert.add(new Subscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("source"), sourceBranchName),
        "source"));

    doOnlySubscriptionInserts(gitModulesFileContent, mergedBranch,
        subscriptionsToInsert);
  }

  /**
   * It calls SubmoduleOp.update method considering scenario only inserting new
   * subscriptions.
   * <p>
   * In this test a commit is created and considered merged to
   * <code>mergedBranch</code> branch.
   * </p>
   * <p>
   * The destination project the commit was merged is not considered to be a
   * source of another project (no subscribers found to this project).
   * </p>
   *
   * @param gitModulesFileContent The .gitmodule file content.
   * @param mergedBranch The {@link Branch.NameKey} instance representing the
   *        project/branch the commit was merged.
   * @param extractedSubscriptions The subscription rows extracted from
   *        gitmodules file.
   * @throws Exception If an exception occurs.
   */
  private void doOnlySubscriptionInserts(final String gitModulesFileContent,
      final Branch.NameKey mergedBranch,
      final List<Subscription> extractedSubscriptions) throws Exception {
    doOnlySubscriptionTableOperations(gitModulesFileContent, mergedBranch,
        extractedSubscriptions, new ArrayList<Subscription>());
  }

  /**
   * It calls SubmoduleOp.update method considering scenario only updating
   * Subscriptions table.
   * <p>
   * In this test a commit is created and considered merged to
   * <code>mergedBranch</code> branch.
   * </p>
   * <p>
   * The destination project the commit was merged is not considered to be a
   * source of another project (no subscribers found to this project).
   * </p>
   *
   * @param gitModulesFileContent The .gitmodules file content.
   * @param mergedBranch The {@link Branch.NameKey} instance representing the
   *        project/branch the commit was merged.
   * @param extractedSubscriptions The subscription rows extracted from
   *        gitmodules file.
   * @param previousSubscriptions The subscription rows to be considering as
   *        existing and pointing as target to the <code>mergedBranch</code>
   *        before updating the table.
   * @throws Exception If an exception occurs.
   */
  private void doOnlySubscriptionTableOperations(
      final String gitModulesFileContent, final Branch.NameKey mergedBranch,
      final List<Subscription> extractedSubscriptions,
      final List<Subscription> previousSubscriptions) throws Exception {
    expect(schemaFactory.open()).andReturn(schema);

    final Repository realDb = createWorkRepository();
    final Git git = new Git(realDb);

    addRegularFileToIndex(".gitmodules", gitModulesFileContent, realDb);

    final RevCommit mergeTip = git.commit().setMessage("test").call();

    expect(urlProvider.get()).andReturn("http://localhost:8080");

    expect(schema.subscriptions()).andReturn(subscriptions);
    expect(subscriptions.getSubscription(mergedBranch)).andReturn(
        new ListResultSet<Subscription>(previousSubscriptions));

    final List<Subscription> alreadySubscribeds = new ArrayList<Subscription>();
    for (Subscription s : extractedSubscriptions) {
      if (previousSubscriptions.contains(s)) {
        alreadySubscribeds.add(s);
      }
    }

    final List<Subscription> subscriptionsToRemove =
        new ArrayList<Subscription>(previousSubscriptions);
    final List<Subscription> subscriptionsToInsert =
        new ArrayList<Subscription>(extractedSubscriptions);

    subscriptionsToRemove.removeAll(subscriptionsToInsert);
    subscriptionsToInsert.removeAll(alreadySubscribeds);

    if (!subscriptionsToRemove.isEmpty()) {
      expect(schema.subscriptions()).andReturn(subscriptions);
      subscriptions.delete(subscriptionsToRemove);
    }

    expect(schema.subscriptions()).andReturn(subscriptions);
    subscriptions.insert(subscriptionsToInsert);

    expect(schema.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(mergeTip.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(schema.subscriptions()).andReturn(subscriptions);
    expect(subscriptions.getSubscribers(mergedBranch)).andReturn(
        new ListResultSet<Subscription>(new ArrayList<Subscription>()));

    schema.close();

    doReplay();

    final SubmoduleOp submoduleOp =
        new SubmoduleOp(mergedBranch, mergeTip, new RevWalk(realDb),
            urlProvider, schemaFactory, realDb, new Project(mergedBranch
                .getParentKey()), new ArrayList<Change>(), null, null, null,
            null);

    submoduleOp.update();
  }

  /**
   * It creates and adds a regular file to git index of a repository.
   *
   * @param fileName The file name.
   * @param content File content.
   * @param repository The Repository instance.
   * @throws IOException If an I/O exception occurs.
   */
  private void addRegularFileToIndex(final String fileName,
      final String content, final Repository repository) throws IOException {
    final ObjectInserter oi = repository.newObjectInserter();
    AnyObjectId objectId =
        oi.insert(Constants.OBJ_BLOB, Constants.encode(content));
    oi.flush();
    addEntryToIndex(fileName, FileMode.REGULAR_FILE, objectId, repository);
  }

  /**
   * It creates and adds a git link to git index of a repository.
   *
   * @param fileName The file name.
   * @param objectId The sha-1 value of git link.
   * @param repository The Repository instance.
   * @throws IOException If an I/O exception occurs.
   */
  private void addGitLinkToIndex(final String fileName,
      final AnyObjectId objectId, final Repository repository)
      throws IOException {
    addEntryToIndex(fileName, FileMode.GITLINK, objectId, repository);
  }

  /**
   * It adds an entry to index.
   *
   * @param path The entry path.
   * @param fileMode The entry file mode.
   * @param objectId The ObjectId value of the entry.
   * @param repository The repository instance.
   * @throws IOException If an I/O exception occurs.
   */
  private void addEntryToIndex(final String path, final FileMode fileMode,
      final AnyObjectId objectId, final Repository repository)
      throws IOException {
    final DirCacheEntry e = new DirCacheEntry(path);
    e.setFileMode(fileMode);
    e.setObjectId(objectId);

    final DirCacheBuilder dirCacheBuilder = repository.lockDirCache().builder();
    dirCacheBuilder.add(e);
    dirCacheBuilder.commit();
  }
}
