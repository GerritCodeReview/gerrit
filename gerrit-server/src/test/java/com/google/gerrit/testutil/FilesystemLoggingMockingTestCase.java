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

package com.google.gerrit.testutil;

import com.google.common.base.Strings;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import org.eclipse.jgit.util.FileUtils;

public abstract class FilesystemLoggingMockingTestCase extends LoggingMockingTestCase {

  private Collection<File> toCleanup = new ArrayList<>();

  /**
   * Asserts that a given file exists.
   *
   * @param file The file to test.
   */
  protected void assertExists(File file) {
    assertTrue("File '" + file.getAbsolutePath() + "' does not exist", file.exists());
  }

  /**
   * Asserts that a given file does not exist.
   *
   * @param file The file to test.
   */
  protected void assertDoesNotExist(File file) {
    assertFalse("File '" + file.getAbsolutePath() + "' exists", file.exists());
  }

  /**
   * Asserts that a given file exists and is a directory.
   *
   * @param file The file to test.
   */
  protected void assertDirectory(File file) {
    // Although isDirectory includes checking for existence, we nevertheless
    // explicitly check for existence, to get more appropriate error messages
    assertExists(file);
    assertTrue("File '" + file.getAbsolutePath() + "' is not a directory", file.isDirectory());
  }

  /**
   * Asserts that creating a directory from the given file worked
   *
   * @param file The directory to create
   */
  protected void assertMkdirs(File file) {
    assertTrue("Could not create directory '" + file.getAbsolutePath() + "'", file.mkdirs());
  }

  /**
   * Asserts that creating a directory from the specified file worked
   *
   * @param parent The parent of the directory to create
   * @param name The name of the directoryto create (relative to {@code parent}
   * @return The created directory
   */
  protected File assertMkdirs(File parent, String name) {
    File file = new File(parent, name);
    assertMkdirs(file);
    return file;
  }

  /**
   * Asserts that creating a file worked
   *
   * @param file The file to create
   */
  protected void assertCreateFile(File file) throws IOException {
    assertTrue("Could not create file '" + file.getAbsolutePath() + "'", file.createNewFile());
  }

  /**
   * Asserts that creating a file worked
   *
   * @param parent The parent of the file to create
   * @param name The name of the file to create (relative to {@code parent}
   * @return The created file
   */
  protected File assertCreateFile(File parent, String name) throws IOException {
    File file = new File(parent, name);
    assertCreateFile(file);
    return file;
  }

  /**
   * Creates a file in the system's default folder for temporary files.
   *
   * <p>The file/directory automatically gets removed during tearDown.
   *
   * <p>The name of the created file begins with 'gerrit_test_', and is located in the system's
   * default folder for temporary files.
   *
   * @param suffix Trailing part of the file name.
   * @return The temporary file.
   * @throws IOException If a file could not be created.
   */
  private File createTempFile(String suffix) throws IOException {
    String prefix = "gerrit_test_";
    if (!Strings.isNullOrEmpty(getName())) {
      prefix += getName() + "_";
    }
    File tmp = File.createTempFile(prefix, suffix);
    toCleanup.add(tmp);
    return tmp;
  }

  /**
   * Creates a file in the system's default folder for temporary files.
   *
   * <p>The file/directory automatically gets removed during tearDown.
   *
   * <p>The name of the created file begins with 'gerrit_test_', and is located in the system's
   * default folder for temporary files.
   *
   * @return The temporary file.
   * @throws IOException If a file could not be created.
   */
  protected File createTempFile() throws IOException {
    return createTempFile("");
  }

  /**
   * Creates a directory in the system's default folder for temporary files.
   *
   * <p>The directory (and all it's contained files/directory) automatically get removed during
   * tearDown.
   *
   * <p>The name of the created directory begins with 'gerrit_test_', and is be located in the
   * system's default folder for temporary files.
   *
   * @return The temporary directory.
   * @throws IOException If a file could not be created.
   */
  protected File createTempDir() throws IOException {
    File tmp = createTempFile(".dir");
    if (!tmp.delete()) {
      throw new IOException("Cannot delete temporary file '" + tmp.getPath() + "'");
    }
    tmp.mkdir();
    return tmp;
  }

  private void cleanupCreatedFiles() throws IOException {
    for (File file : toCleanup) {
      FileUtils.delete(file, FileUtils.RECURSIVE);
    }
  }

  @Override
  public void tearDown() throws Exception {
    cleanupCreatedFiles();
    super.tearDown();
  }
}
