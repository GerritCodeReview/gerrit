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
import java.util.Set;

public class HandleAtomicCommitsTest extends LocalDiskRepositoryTestCase {

  private static final String newLine = System.getProperty("line.separator");

  private SchemaFactory<ReviewDb> schemaFactory;
  private SubscriptionAccess subscriptions;
  private ReviewDb db;
  private Provider<String> urlProvider;
  private AtomicEntryAccess atomicEntries;
  private GitRepositoryManager repoManager;
  private ReplicationQueue replication;
  private Repository repositoryA;
  private Repository repositoryB;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    schemaFactory = createStrictMock(SchemaFactory.class);
    db = createStrictMock(ReviewDb.class);
    subscriptions = createStrictMock(SubscriptionAccess.class);
    urlProvider = createStrictMock(Provider.class);
    atomicEntries = createStrictMock(AtomicEntryAccess.class);
    repoManager = createStrictMock(GitRepositoryManager.class);
    replication = createStrictMock(ReplicationQueue.class);
    repositoryA = createStrictMock(Repository.class);
    repositoryB = createStrictMock(Repository.class);
  }

  private void doReplay() {
    replay(schemaFactory, db, subscriptions, urlProvider, atomicEntries,
        repoManager, replication, repositoryA, repositoryB);
  }

  private void doVerify() {
    verify(schemaFactory, db, subscriptions, urlProvider, atomicEntries,
        repoManager, replication, repositoryA, repositoryB);
  }

  @Test
  public void testCreateOrdinaryCommit() throws Exception {
    final Repository repository = createWorkRepository();
    final Git git = new Git(repository);

    final RevCommit revCommit = git.commit().setMessage("test").call();

    final Branch.NameKey branchNameKey =
        new Branch.NameKey(new Project.NameKey("test-project"), "test-branch");

    final Change change =
        new Change(new Change.Key(revCommit.toObjectId().getName()),
            new Change.Id(1), new Account.Id(1), branchNameKey);

    expect(schemaFactory.open()).andReturn(db);

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(revCommit.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    atomicEntries.insert(new ArrayList<AtomicEntry>());

    db.close();

    doReplay();

    HandleAtomicCommits handleAtomicCommits =
        new HandleAtomicCommits(schemaFactory, replication, repository,
            repoManager);

    handleAtomicCommits.handleAtCreateChange(revCommit, change);

    doVerify();
  }

  @Test
  public void testReplaceOrdinaryCommit() throws Exception {
    final Repository repository = createWorkRepository();
    final Git git = new Git(repository);

    final RevCommit revCommit = git.commit().setMessage("test").call();

    final Branch.NameKey branchNameKey =
        new Branch.NameKey(new Project.NameKey("test-project"), "test-branch");

    final Change change =
        new Change(new Change.Key(revCommit.toObjectId().getName()),
            new Change.Id(1), new Account.Id(1), branchNameKey);

    expect(schemaFactory.open()).andReturn(db);

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySuperChangeId(change.getId())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(revCommit.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceChangeId(change.getId())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    atomicEntries.delete(new ArrayList<AtomicEntry>());

    expect(db.atomicEntries()).andReturn(atomicEntries);
    atomicEntries.insert(new ArrayList<AtomicEntry>());

    db.close();

    doReplay();

    HandleAtomicCommits handleAtomicCommits =
        new HandleAtomicCommits(schemaFactory, replication, repository,
            repoManager);

    handleAtomicCommits.handleAtReplaceChange(revCommit, change);

    doVerify();
  }

  @Test
  public void testCreateWithTwoGitLinks() throws Exception {
    final Repository repository = createWorkRepository();
    final Git git = new Git(repository);

    final DirCacheBuilder dirCacheBuilder = repository.lockDirCache().builder();

    final Repository userRepositoryA = createWorkRepository();
    final Git userGitA = new Git(userRepositoryA);
    final RevCommit userRevCommitA =
        userGitA.commit().setMessage("test").call();

    final DirCacheEntry entryA = new DirCacheEntry("a");
    entryA.setFileMode(FileMode.GITLINK);
    entryA.setObjectId(userRevCommitA.toObjectId());

    dirCacheBuilder.add(entryA);

    final Repository userRepositoryB = createWorkRepository();
    final Git userGitB = new Git(userRepositoryB);
    final RevCommit userRevCommitB =
        userGitB.commit().setMessage("test").call();

    final DirCacheEntry entryB = new DirCacheEntry("b");
    entryB.setFileMode(FileMode.GITLINK);
    entryB.setObjectId(userRevCommitB.toObjectId());

    dirCacheBuilder.add(entryB);

    dirCacheBuilder.commit();

    final RevCommit revCommit = git.commit().setMessage("test").call();

    final Branch.NameKey branchNameKey =
        new Branch.NameKey(new Project.NameKey("test-project"), "test-branch");

    final Change change =
        new Change(new Change.Key(revCommit.toObjectId().getName()),
            new Change.Id(1), new Account.Id(1), branchNameKey);

    expect(schemaFactory.open()).andReturn(db);

    expect(db.subscriptions()).andReturn(subscriptions);
    final List<Subscription> aSubscriptions =
        Collections.singletonList(new Subscription(branchNameKey,
            new Branch.NameKey(new Project.NameKey("a"), "a"), "a"));
    expect(subscriptions.getSubscription(change.getDest(), "a")).andReturn(
        new ListResultSet<Subscription>(aSubscriptions));

    expect(repoManager.openRepository(new Project.NameKey("a"))).andReturn(
        repositoryA);
    expect(repositoryA.hasObject(userRevCommitA.toObjectId())).andReturn(false);

    expect(db.subscriptions()).andReturn(subscriptions);
    final List<Subscription> bSubscriptions =
        Collections.singletonList(new Subscription(branchNameKey,
            new Branch.NameKey(new Project.NameKey("b"), "b"), "b"));
    expect(subscriptions.getSubscription(change.getDest(), "b")).andReturn(
        new ListResultSet<Subscription>(bSubscriptions));

    expect(repoManager.openRepository(new Project.NameKey("b"))).andReturn(
        repositoryB);
    expect(repositoryB.hasObject(userRevCommitB.toObjectId())).andReturn(false);

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(revCommit.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    final List<AtomicEntry> atomicEntriesToInsert =
        new ArrayList<AtomicEntry>();
    atomicEntriesToInsert.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), userRevCommitA.toObjectId().getName())));
    atomicEntriesToInsert.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), userRevCommitB.toObjectId().getName())));
    atomicEntries.insert(atomicEntriesToInsert);

    db.close();

    doReplay();

    final HandleAtomicCommits handleAtomicCommits =
        new HandleAtomicCommits(schemaFactory, replication, repository,
            repoManager);

    handleAtomicCommits.handleAtCreateChange(revCommit, change);

    doVerify();
  }

  @Test
  public void testReplaceWithTwoGitLinks() throws Exception {
    final Repository repository = createWorkRepository();
    final Git git = new Git(repository);

    final DirCacheBuilder dirCacheBuilder = repository.lockDirCache().builder();

    final Repository userRepositoryA = createWorkRepository();
    final Git userGitA = new Git(userRepositoryA);
    final RevCommit userRevCommitA =
        userGitA.commit().setMessage("test").call();

    final DirCacheEntry entryA = new DirCacheEntry("a");
    entryA.setFileMode(FileMode.GITLINK);
    entryA.setObjectId(userRevCommitA.toObjectId());

    dirCacheBuilder.add(entryA);

    final Repository userRepositoryB = createWorkRepository();
    final Git userGitB = new Git(userRepositoryB);
    final RevCommit userRevCommitB =
        userGitB.commit().setMessage("test").call();

    final DirCacheEntry entryB = new DirCacheEntry("b");
    entryB.setFileMode(FileMode.GITLINK);
    entryB.setObjectId(userRevCommitB.toObjectId());

    dirCacheBuilder.add(entryB);

    dirCacheBuilder.commit();

    final RevCommit revCommit = git.commit().setMessage("test").call();

    final Branch.NameKey branchNameKey =
        new Branch.NameKey(new Project.NameKey("test-project"), "test-branch");

    final Change change =
        new Change(new Change.Key(revCommit.toObjectId().getName()),
            new Change.Id(1), new Account.Id(1), branchNameKey);

    expect(schemaFactory.open()).andReturn(db);

    expect(db.atomicEntries()).andReturn(atomicEntries);
    expect(atomicEntries.bySuperChangeId(change.getId())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.subscriptions()).andReturn(subscriptions);
    final List<Subscription> aSubscriptions =
        Collections.singletonList(new Subscription(branchNameKey,
            new Branch.NameKey(new Project.NameKey("a"), "a"), "a"));
    expect(subscriptions.getSubscription(change.getDest(), "a")).andReturn(
        new ListResultSet<Subscription>(aSubscriptions));

    expect(repoManager.openRepository(new Project.NameKey("a"))).andReturn(
        repositoryA);
    expect(repositoryA.hasObject(userRevCommitA.toObjectId())).andReturn(false);

    expect(db.subscriptions()).andReturn(subscriptions);
    final List<Subscription> bSubscriptions =
        Collections.singletonList(new Subscription(branchNameKey,
            new Branch.NameKey(new Project.NameKey("b"), "b"), "b"));
    expect(subscriptions.getSubscription(change.getDest(), "b")).andReturn(
        new ListResultSet<Subscription>(bSubscriptions));

    expect(repoManager.openRepository(new Project.NameKey("b"))).andReturn(
        repositoryB);
    expect(repositoryB.hasObject(userRevCommitB.toObjectId())).andReturn(false);

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(revCommit.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceChangeId(change.getId())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);
    atomicEntries.delete(new ArrayList<AtomicEntry>());

    expect(db.atomicEntries()).andReturn(atomicEntries);

    final List<AtomicEntry> atomicEntriesToInsert =
        new ArrayList<AtomicEntry>();
    atomicEntriesToInsert.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), userRevCommitA.toObjectId().getName())));
    atomicEntriesToInsert.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), userRevCommitB.toObjectId().getName())));
    atomicEntries.insert(atomicEntriesToInsert);

    db.close();

    doReplay();

    final HandleAtomicCommits handleAtomicCommits =
        new HandleAtomicCommits(schemaFactory, replication, repository,
            repoManager);

    handleAtomicCommits.handleAtReplaceChange(revCommit, change);

    doVerify();
  }

  @Test
  public void testReplaceUpdatingEntries() throws Exception {
    final Repository repository = createWorkRepository();
    final Git git = new Git(repository);

    final DirCacheBuilder dirCacheBuilder = repository.lockDirCache().builder();

    final Repository userRepositoryA = createWorkRepository();
    final Git userGitA = new Git(userRepositoryA);
    final RevCommit userRevCommitA =
        userGitA.commit().setMessage("test-a").call();

    final DirCacheEntry entryA = new DirCacheEntry("a");
    entryA.setFileMode(FileMode.GITLINK);
    entryA.setObjectId(userRevCommitA.toObjectId());

    dirCacheBuilder.add(entryA);

    final Repository userRepositoryB = createWorkRepository();
    final Git userGitB = new Git(userRepositoryB);
    final RevCommit userRevCommitB =
        userGitB.commit().setMessage("test-b").call();

    final DirCacheEntry entryB = new DirCacheEntry("b");
    entryB.setFileMode(FileMode.GITLINK);
    entryB.setObjectId(userRevCommitB.toObjectId());

    dirCacheBuilder.add(entryB);

    dirCacheBuilder.commit();

    final RevCommit revCommit = git.commit().setMessage("test").call();

    final Branch.NameKey branchNameKey =
        new Branch.NameKey(new Project.NameKey("test-project"), "test-branch");

    final Change change =
        new Change(new Change.Key(revCommit.toObjectId().getName()),
            new Change.Id(1), new Account.Id(1), branchNameKey);

    expect(schemaFactory.open()).andReturn(db);

    expect(db.atomicEntries()).andReturn(atomicEntries);

    final List<AtomicEntry> existingAtomicEntries = new ArrayList<AtomicEntry>();
    existingAtomicEntries.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), userRevCommitA.toObjectId().getName())));
    final String fakeCommitSourceC = "fake";
    existingAtomicEntries.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), fakeCommitSourceC)));

    expect(atomicEntries.bySuperChangeId(change.getId())).andReturn(
        new ListResultSet<AtomicEntry>(existingAtomicEntries));

    expect(db.subscriptions()).andReturn(subscriptions);
    final List<Subscription> aSubscriptions =
        Collections.singletonList(new Subscription(branchNameKey,
            new Branch.NameKey(new Project.NameKey("a"), "a"), "a"));
    expect(subscriptions.getSubscription(change.getDest(), "a")).andReturn(
        new ListResultSet<Subscription>(aSubscriptions));

    expect(repoManager.openRepository(new Project.NameKey("a"))).andReturn(
        repositoryA);
    expect(repositoryA.hasObject(userRevCommitA.toObjectId())).andReturn(false);

    expect(db.subscriptions()).andReturn(subscriptions);
    final List<Subscription> bSubscriptions =
        Collections.singletonList(new Subscription(branchNameKey,
            new Branch.NameKey(new Project.NameKey("b"), "b"), "b"));
    expect(subscriptions.getSubscription(change.getDest(), "b")).andReturn(
        new ListResultSet<Subscription>(bSubscriptions));

    expect(repoManager.openRepository(new Project.NameKey("b"))).andReturn(
        repositoryB);
    expect(repositoryB.hasObject(userRevCommitB.toObjectId())).andReturn(false);

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceSha1(revCommit.getId().getName())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    expect(atomicEntries.bySourceChangeId(change.getId())).andReturn(
        new ListResultSet<AtomicEntry>(new ArrayList<AtomicEntry>()));

    expect(db.atomicEntries()).andReturn(atomicEntries);
    atomicEntries.delete(Collections.singletonList(new AtomicEntry(
        new AtomicEntry.Id(change.getId(), fakeCommitSourceC))));

    expect(db.atomicEntries()).andReturn(atomicEntries);

    final List<AtomicEntry> atomicEntriesToInsert =
        new ArrayList<AtomicEntry>();
    atomicEntriesToInsert.add(new AtomicEntry(new AtomicEntry.Id(
        change.getId(), userRevCommitB.toObjectId().getName())));
    atomicEntries.insert(atomicEntriesToInsert);

    db.close();

    doReplay();

    final HandleAtomicCommits handleAtomicCommits =
        new HandleAtomicCommits(schemaFactory, replication, repository,
            repoManager);

    handleAtomicCommits.handleAtReplaceChange(revCommit, change);

    doVerify();
  }

}
