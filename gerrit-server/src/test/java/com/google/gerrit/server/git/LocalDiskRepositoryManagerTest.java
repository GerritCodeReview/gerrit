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

package com.google.gerrit.server.git;

import static org.easymock.EasyMock.expect;

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.testutil.LoggingMockingTestCase;
import com.google.gerrit.testutil.PassThroughKeyUtilEncoder;
import com.google.gwtorm.client.KeyUtil;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.h2.store.fs.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Integration tests for LocalDiskRepositoryManager.
 */
// Since LocalDiskRepositoryManager accesses the file system directly in
// several parts, unit tests that fully mock this behavior would heavily rely
// on internals of many classes, and would require mocking the file system. As
// furthermore, LocalDiskRepositoryManagar is at the lower level of the class
// hierarchy anyways , we directly integration test with the filesystem instead
// of unit testing it with mocks.
public class LocalDiskRepositoryManagerTest extends LoggingMockingTestCase {
  /**
   * Pause that many milliseconds when testing whether locks work.
   */
  private final static int LOCK_TEST_SLEEP_INTERVAL = 1000;

  private Injector injector;

  private File sitePath;
  private Config serverConfig;

  public void testRenameNonExisting() throws ProjectRenamingFailedException {
    replayMocks();

    LocalDiskRepositoryManager repoManager =
        injector.getInstance(LocalDiskRepositoryManager.class);

    try {
      repoManager.renameRepository(new Project.NameKey("dummysource"),
          new Project.NameKey("dummydestination"));
      fail("No exception thrown, although source repository does not exist");
    } catch (RepositoryNotFoundException e) {
      assertTrue(e.getMessage().contains("dummysource"));
    }
    assertRepositoryDoesNotExist("dummysource");
    assertRepositoryDoesNotExist("dummydestination");
  }

  public void testRenameSourceEqualsDestination()
      throws ProjectRenamingFailedException, RepositoryCaseMismatchException,
      RepositoryNotFoundException {
    replayMocks();

    LocalDiskRepositoryManager repoManager =
        injector.getInstance(LocalDiskRepositoryManager.class);

    Repository sourceRepo = repoManager.createRepository(
        new Project.NameKey("dummysource"));
    sourceRepo.close();

    try {
      repoManager.renameRepository(new Project.NameKey("dummysource"),
          new Project.NameKey("dummysource"));
      fail("No exception thrown, although source and destination repository "
          + "match");
    } catch (ProjectRenamingFailedException e) {
      assertTrue(e.getMessage().contains("dummysource"));
    }

    verifyMocks();
    assertRepositoryExists("dummysource");
    assertRepositoryDoesNotExist("dummydestination");
  }

  public void testRename() throws ProjectRenamingFailedException,
      IOException {
    replayMocks();

    LocalDiskRepositoryManager repoManager =
        injector.getInstance(LocalDiskRepositoryManager.class);

    Repository sourceRepo = repoManager.createRepository(
        new Project.NameKey("dummysource"));
    sourceRepo.close();

    repoManager.renameRepository(new Project.NameKey("dummysource"),
        new Project.NameKey("dummydestination"));

    verifyMocks();
    assertRepositoryDoesNotExist("dummysource");
    assertRepositoryExists("dummydestination");
  }

  public void testRenameDirectoryCleanup()
      throws ProjectRenamingFailedException, IOException {
    replayMocks();

    LocalDiskRepositoryManager repoManager =
        injector.getInstance(LocalDiskRepositoryManager.class);

    Repository sourceRepo = repoManager.createRepository(
        new Project.NameKey("a/b/c/dummysource"));
    sourceRepo.close();

    File fileInA = new File(sitePath, "gitBaseDir" + File.separator + "a"
        + File.separator + "File" );
    fileInA.createNewFile();

    File dirB = new File(sitePath, "gitBaseDir" + File.separator + "a"
        + File.separator + "b" );

    assertFileExists(fileInA);
    assertFileExists(dirB);

    repoManager.renameRepository(new Project.NameKey("a/b/c/dummysource"),
        new Project.NameKey("z/x/dummydestination"));

    verifyMocks();
    assertRepositoryDoesNotExist("a/b/c/dummysource");
    assertRepositoryExists("z/x/dummydestination");

    assertFileExists(fileInA);
    assertFileDoesNotExist(dirB);
  }

  public void testRenameProhibitedOnSourceLock() throws InterruptedException,
      RepositoryCaseMismatchException, RepositoryNotFoundException {
    replayMocks();

    final LocalDiskRepositoryManager repoManager =
        injector.getInstance(LocalDiskRepositoryManager.class);

    RenamingThread renamingThread = new RenamingThread(repoManager,
        "dummysource", "dummydestination");
    Repository sourceRepo = repoManager.createRepository(
        new Project.NameKey("dummysource"));
    try {
      renamingThread.start();

      assertThreadWaiting(renamingThread);
    } finally {
      sourceRepo.close();
    }
    renamingThread.join();

    verifyMocks();
    assertRepositoryDoesNotExist("dummysource");
    assertRepositoryExists("dummydestination");
    assertNull("Renaming threw expection: "
        + renamingThread.getMostRecentlyThrownException(),
        renamingThread.getMostRecentlyThrownException());
  }

  /**
   * Obtain a File instance for a repository's git directory
   *
   * @param repositoryName The repository's name.
   * @return File instance for {@code repositoryName}'s git directory.
   */
  private File getRepositoryDirectory(String repositoryName) {
    File repoDir = new File(sitePath, "gitBaseDir" + File.separator
        + repositoryName.replaceAll("/", File.separator)
        + Constants.DOT_GIT_EXT);
    return repoDir;
  }

  /**
   * Asserts that a given file exists.
   *
   * @param file The file to test.
   */
  private void assertFileExists(File file) {
    assertTrue("File '" + file + "' does not exist", file.exists());
  }

  /**
   * Asserts that a given file does not exist.
   *
   * @param file The file to test.
   */
  private void assertFileDoesNotExist(File file) {
    assertFalse("File '" + file + "' exists", file.exists());
  }

  /**
   * Asserts that a given file exists and is a directory.
   *
   * @param file The file to test.
   */
  private void assertDirExists(File file) {
    assertFileExists(file);
    assertTrue("File '" + file + "' is not a directory", file.isDirectory());
  }

  /**
   * Asserts that a repository name refers to an existing git repository.
   *
   * @param file The file to test.
   */
  private void assertRepositoryExists(String repositoryName) {
    File repoDir = getRepositoryDirectory(repositoryName);
    assertDirExists(repoDir);
    assertFileExists(new File(repoDir, "HEAD"));
    assertFileExists(new File(repoDir, "config"));
  }

  /**
   * Asserts that a repository name does not refer to an existing git
   * repository.
   *
   * @param file The file to test.
   */
  private void assertRepositoryDoesNotExist(String repositoryName) {
    File repoDir = getRepositoryDirectory(repositoryName);
    assertFileDoesNotExist(repoDir);
  }

  /**
   * Asserts that the given thread is in state waiting.
   *
   * If the thread is not yet in waiting state, at most
   * {@LOCK_TEST_SLEEP_INTERVAL@} milliseconds is waited for the thread to get
   * in waiting state.
   *
   * @param thread
   */
  private void assertThreadWaiting(Thread thread) {
    try {
      int waitCounter = 0;
      int maxSteps = 50;
      while (thread.isAlive() && thread.getState() != Thread.State.WAITING
          && waitCounter++ < maxSteps) {
        Thread.sleep(LOCK_TEST_SLEEP_INTERVAL/maxSteps);
      }
      assertEquals("thread is not waiting", Thread.State.WAITING,
          thread.getState());
    } catch (InterruptedException e) {
      fail("Interrupted during waiting for thread status");
    }
  }

  public void setupCommonMocks() {
    expect(serverConfig.getString("gerrit", null, "basePath"))
        .andReturn("gitBaseDir");
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    injector = Guice.createInjector(new TestModule());

    KeyUtil.setEncoderImpl(new PassThroughKeyUtilEncoder());

    setupCommonMocks();
  }

  @Override
  public void tearDown() throws Exception {
    FileUtils.deleteRecursive(sitePath.getAbsolutePath(), false);
    super.tearDown();
  }

  private class TestModule extends FactoryModule {
    @Override
    protected void configure() {

      serverConfig = createMock(Config.class);
      bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(serverConfig);

      sitePath = randomTargetFile();
      bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
    }

    private File randomTargetFile() {
      final File t = new File("target");
      return new File(t, "random-name-" + UUID.randomUUID().toString());
    }
  }

  /**
   * Thread used for testing locks during renaming
   */
  private class RenamingThread extends Thread {
    private final GitRepositoryManager repoManager;
    private final Project.NameKey source;
    private final Project.NameKey destination;
    private Throwable mostRecentlyThrownException;

    private RenamingThread(GitRepositoryManager repoManager, String source,
        String destination) {
      this.repoManager = repoManager;
      this.source = new Project.NameKey(source);
      this.destination = new Project.NameKey(destination);
      this.mostRecentlyThrownException = null;
    }

    public void run() {
      try {
        repoManager.renameRepository(source, destination);
      } catch (Throwable e) {
        mostRecentlyThrownException = e;
      }
    }

    private Throwable getMostRecentlyThrownException() {
      return mostRecentlyThrownException;
    }
  }
}