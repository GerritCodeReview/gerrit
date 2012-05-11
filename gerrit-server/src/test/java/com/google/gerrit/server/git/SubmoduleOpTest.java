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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.gwtorm.server.StandardKeyEncoder;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class SubmoduleOpTest extends LocalDiskRepositoryTestCase {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private static final String newLine = System.getProperty("line.separator");

  private SchemaFactory<ReviewDb> schemaFactory;
  private SubmoduleSubscriptionAccess subscriptions;
  private ReviewDb schema;
  private Provider<String> urlProvider;
  private GitRepositoryManager repoManager;
  private GitReferenceUpdated replication;

  @SuppressWarnings("unchecked")
  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    schemaFactory = createStrictMock(SchemaFactory.class);
    schema = createStrictMock(ReviewDb.class);
    subscriptions = createStrictMock(SubmoduleSubscriptionAccess.class);
    urlProvider = createStrictMock(Provider.class);
    repoManager = createStrictMock(GitRepositoryManager.class);
    replication = createStrictMock(GitReferenceUpdated.class);
  }

  private void doReplay() {
    replay(schemaFactory, schema, subscriptions, urlProvider, repoManager,
        replication);
  }

  private void doVerify() {
    verify(schemaFactory, schema, subscriptions, urlProvider, repoManager,
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

    expect(urlProvider.get()).andReturn("http://localhost:8080");

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    final ResultSet<SubmoduleSubscription> emptySubscriptions =
        new ListResultSet<SubmoduleSubscription>(new ArrayList<SubmoduleSubscription>());
    expect(subscriptions.bySubmodule(branchNameKey)).andReturn(
        emptySubscriptions);

    schema.close();

    doReplay();

    final SubmoduleOp submoduleOp =
        new SubmoduleOp(branchNameKey, mergeTip, new RevWalk(realDb), urlProvider,
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
   *       branch = .
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
  public void testNewSubscriptionToDotBranchValue() throws Exception {
    doOneSubscriptionInsert(buildSubmoduleSection("source", "source",
        "http://localhost:8080/source", ".").toString(), "refs/heads/master");

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
   *       branch = refs/heads/master
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
  public void testNewSubscriptionToSameBranch() throws Exception {
    doOneSubscriptionInsert(buildSubmoduleSection("source", "source",
        "http://localhost:8080/source", "refs/heads/master").toString(),
        "refs/heads/master");

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
   *       branch = refs/heads/test
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
  public void testNewSubscriptionToDifferentBranch() throws Exception {
    doOneSubscriptionInsert(buildSubmoduleSection("source", "source",
        "http://localhost:8080/source", "refs/heads/test").toString(),
        "refs/heads/test");

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
   *       branch = .
   *
   *     [submodule "source-b"]
   *       path = source-b
   *       url = http://localhost:8080/source-b
   *       branch = .
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
  public void testNewSubscriptionsWithDotBranchValue() throws Exception {
    final StringBuilder sb =
        buildSubmoduleSection("source-a", "source-a",
            "http://localhost:8080/source-a", ".");
    sb.append(buildSubmoduleSection("source-b", "source-b",
        "http://localhost:8080/source-b", "."));

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<SubmoduleSubscription> subscriptionsToInsert =
        new ArrayList<SubmoduleSubscription>();
    subscriptionsToInsert
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/master"), "source-a"));
    subscriptionsToInsert
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
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
   *       branch = .
   *
   *     [submodule "source-b"]
   *       path = source-b
   *       url = http://localhost:8080/source-b
   *       branch = refs/heads/master
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
  public void testNewSubscriptionsDotAndSameBranchValues() throws Exception {
    final StringBuilder sb =
        buildSubmoduleSection("source-a", "source-a",
            "http://localhost:8080/source-a", ".");
    sb.append(buildSubmoduleSection("source-b", "source-b",
        "http://localhost:8080/source-b", "refs/heads/master"));

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<SubmoduleSubscription> subscriptionsToInsert =
        new ArrayList<SubmoduleSubscription>();
    subscriptionsToInsert
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/master"), "source-a"));
    subscriptionsToInsert
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
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
   *       branch = refs/heads/test-a
   *
   *     [submodule "source-b"]
   *       path = source-b
   *       url = http://localhost:8080/source-b
   *       branch = refs/heads/test-b
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
  public void testNewSubscriptionsSpecificBranchValues() throws Exception {
    final StringBuilder sb =
        buildSubmoduleSection("source-a", "source-a",
            "http://localhost:8080/source-a", "refs/heads/test-a");
    sb.append(buildSubmoduleSection("source-b", "source-b",
        "http://localhost:8080/source-b", "refs/heads/test-b"));

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<SubmoduleSubscription> subscriptionsToInsert =
        new ArrayList<SubmoduleSubscription>();
    subscriptionsToInsert
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/test-a"), "source-a"));
    subscriptionsToInsert
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
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
   *       branch = refs/heads/master
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
    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final List<SubmoduleSubscription> subscriptionsToInsert =
        new ArrayList<SubmoduleSubscription>();
    subscriptionsToInsert.add(new SubmoduleSubscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("source"), "refs/heads/master"),
        "source"));

    final List<SubmoduleSubscription> oldOnesToMergedBranch =
        new ArrayList<SubmoduleSubscription>();
    oldOnesToMergedBranch.add(new SubmoduleSubscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("old-source"),
            "refs/heads/master"), "old-source"));

    doOnlySubscriptionTableOperations(buildSubmoduleSection("source", "source",
        "http://localhost:8080/source", "refs/heads/master").toString(),
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
   *       branch = refs/heads/master
   *
   *     [submodule "old"]
   *       path = old
   *       url = http://localhost:8080/old
   *       branch = refs/heads/master
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
    final StringBuilder sb =
        buildSubmoduleSection("new", "new", "http://localhost:8080/new",
            "refs/heads/master");
    sb.append(buildSubmoduleSection("old", "old", "http://localhost:8080/old",
        "refs/heads/master"));

    final Branch.NameKey mergedBranch =
        new Branch.NameKey(new Project.NameKey("dest-project"),
            "refs/heads/master");

    final SubmoduleSubscription old =
        new SubmoduleSubscription(mergedBranch, new Branch.NameKey(new Project.NameKey(
            "old"), "refs/heads/master"), "old");

    final List<SubmoduleSubscription> extractedsubscriptions =
        new ArrayList<SubmoduleSubscription>();
    extractedsubscriptions.add(new SubmoduleSubscription(mergedBranch,
        new Branch.NameKey(new Project.NameKey("new"), "refs/heads/master"),
        "new"));
    extractedsubscriptions.add(old);

    final List<SubmoduleSubscription> oldOnesToMergedBranch =
        new ArrayList<SubmoduleSubscription>();
    oldOnesToMergedBranch.add(old);

    doOnlySubscriptionTableOperations(sb.toString(), mergedBranch,
        extractedsubscriptions, oldOnesToMergedBranch);

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

    final List<SubmoduleSubscription> extractedsubscriptions =
        new ArrayList<SubmoduleSubscription>();

    final List<SubmoduleSubscription> oldOnesToMergedBranch =
        new ArrayList<SubmoduleSubscription>();
    oldOnesToMergedBranch
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
            new Project.NameKey("source-a"), "refs/heads/master"), "source-a"));
    oldOnesToMergedBranch
        .add(new SubmoduleSubscription(mergedBranch, new Branch.NameKey(
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

    expect(urlProvider.get()).andReturn("http://localhost:8080");

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    final ResultSet<SubmoduleSubscription> subscribers =
        new ListResultSet<SubmoduleSubscription>(Collections
            .singletonList(new SubmoduleSubscription(targetBranchNameKey,
                sourceBranchNameKey, "source-project")));
    expect(subscriptions.bySubmodule(sourceBranchNameKey)).andReturn(
        subscribers);

    expect(repoManager.openRepository(targetBranchNameKey.getParentKey()))
        .andReturn(targetRepository);

    replication.fire(targetBranchNameKey.getParentKey(),
        targetBranchNameKey.get());

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    final ResultSet<SubmoduleSubscription> emptySubscriptions =
        new ListResultSet<SubmoduleSubscription>(new ArrayList<SubmoduleSubscription>());
    expect(subscriptions.bySubmodule(targetBranchNameKey)).andReturn(
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
   * It tests SubmoduleOp.update in a scenario considering established circular
   * reference in submodule_subscriptions table.
   * <p>
   * In the tested scenario there is no .gitmodules file in a merged commit to a
   * destination project/branch that is a source one to one called
   * "target-project".
   * <p>
   * submodule_subscriptions table will be incorrect due source appearing as a
   * subscriber or target-project: according to database target-project has as
   * source the source-project, and source-project has as source the
   * target-project.
   * <p>
   * It expects to update the git link called "source-project" to be in target
   * repository and ignoring the incorrect row in database establishing the
   * circular reference.
   * </p>
   *
   * @throws Exception If an exception occurs.
   */
  @Test
  public void testAvoidingCircularReference() throws Exception {
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

    expect(urlProvider.get()).andReturn("http://localhost:8080");

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    final ResultSet<SubmoduleSubscription> subscribers =
        new ListResultSet<SubmoduleSubscription>(Collections
            .singletonList(new SubmoduleSubscription(targetBranchNameKey,
                sourceBranchNameKey, "source-project")));
    expect(subscriptions.bySubmodule(sourceBranchNameKey)).andReturn(
        subscribers);

    expect(repoManager.openRepository(targetBranchNameKey.getParentKey()))
        .andReturn(targetRepository);

    replication.fire(targetBranchNameKey.getParentKey(),
        targetBranchNameKey.get());

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    final ResultSet<SubmoduleSubscription> incorrectSubscriptions =
        new ListResultSet<SubmoduleSubscription>(Collections
            .singletonList(new SubmoduleSubscription(sourceBranchNameKey,
                targetBranchNameKey, "target-project")));
    expect(subscriptions.bySubmodule(targetBranchNameKey)).andReturn(
        incorrectSubscriptions);

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

    final List<SubmoduleSubscription> subscriptionsToInsert =
        new ArrayList<SubmoduleSubscription>();
    subscriptionsToInsert.add(new SubmoduleSubscription(mergedBranch,
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
      final List<SubmoduleSubscription> extractedSubscriptions) throws Exception {
    doOnlySubscriptionTableOperations(gitModulesFileContent, mergedBranch,
        extractedSubscriptions, new ArrayList<SubmoduleSubscription>());
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
      final List<SubmoduleSubscription> extractedSubscriptions,
      final List<SubmoduleSubscription> previousSubscriptions) throws Exception {
    expect(schemaFactory.open()).andReturn(schema);

    final Repository realDb = createWorkRepository();
    final Git git = new Git(realDb);

    addRegularFileToIndex(".gitmodules", gitModulesFileContent, realDb);

    final RevCommit mergeTip = git.commit().setMessage("test").call();

    expect(urlProvider.get()).andReturn("http://localhost:8080").times(2);

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    expect(subscriptions.bySuperProject(mergedBranch)).andReturn(
        new ListResultSet<SubmoduleSubscription>(previousSubscriptions));

    SortedSet<Project.NameKey> existingProjects =
        new TreeSet<Project.NameKey>();

    for (SubmoduleSubscription extracted : extractedSubscriptions) {
      existingProjects.add(extracted.getSubmodule().getParentKey());
    }

    for (int index = 0; index < extractedSubscriptions.size(); index++) {
      expect(repoManager.list()).andReturn(existingProjects);
    }

    final Set<SubmoduleSubscription> alreadySubscribeds =
        new HashSet<SubmoduleSubscription>();
    for (SubmoduleSubscription s : extractedSubscriptions) {
      if (previousSubscriptions.contains(s)) {
        alreadySubscribeds.add(s);
      }
    }

    final Set<SubmoduleSubscription> subscriptionsToRemove =
        new HashSet<SubmoduleSubscription>(previousSubscriptions);
    final List<SubmoduleSubscription> subscriptionsToInsert =
        new ArrayList<SubmoduleSubscription>(extractedSubscriptions);

    subscriptionsToRemove.removeAll(subscriptionsToInsert);
    subscriptionsToInsert.removeAll(alreadySubscribeds);

    if (!subscriptionsToRemove.isEmpty()) {
      expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
      subscriptions.delete(subscriptionsToRemove);
    }

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    subscriptions.insert(subscriptionsToInsert);

    expect(schema.submoduleSubscriptions()).andReturn(subscriptions);
    expect(subscriptions.bySubmodule(mergedBranch)).andReturn(
        new ListResultSet<SubmoduleSubscription>(new ArrayList<SubmoduleSubscription>()));

    schema.close();

    doReplay();

    final SubmoduleOp submoduleOp =
        new SubmoduleOp(mergedBranch, mergeTip, new RevWalk(realDb),
            urlProvider, schemaFactory, realDb, new Project(mergedBranch
                .getParentKey()), new ArrayList<Change>(), null, null,
            repoManager, null);

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

  private static StringBuilder buildSubmoduleSection(final String name,
      final String path, final String url, final String branch) {
    final StringBuilder sb = new StringBuilder();

    sb.append("[submodule \"");
    sb.append(name);
    sb.append("\"]");
    sb.append(newLine);

    sb.append("\tpath = ");
    sb.append(path);
    sb.append(newLine);

    sb.append("\turl = ");
    sb.append(url);
    sb.append(newLine);

    sb.append("\tbranch = ");
    sb.append(branch);
    sb.append(newLine);

    return sb;
  }
}
