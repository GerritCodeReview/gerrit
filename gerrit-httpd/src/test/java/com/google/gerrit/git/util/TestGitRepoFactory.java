package com.google.gerrit.git.util;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.util.FileUtils;

/**
 * Creates a fresh new git repository based on git repository bundle file.
 */
public class TestGitRepoFactory {

  protected static final String BASE_PATH = "gerrit-httpd" + File.separator
      + "src" + File.separator + "test" + File.separator + "resources"
      + File.separator;

  /**
   * Creates a temporary git instance and clones a repository from a given git
   * repository bundle file name.
   *
   * @param gitRepoBundleFileName
   * @return instance of the git repository
   * @throws IOException
   * @throws InvalidRemoteException
   * @throws TransportException
   * @throws GitAPIException
   */
  public static Git getRepositoryInstanceBy(String gitRepoBundleFileName)
      throws IOException, InvalidRemoteException, TransportException,
      GitAPIException {
    File tempDirectory = createTempDirectory(gitRepoBundleFileName);

    File file = new File(".");
    file.getAbsolutePath();

    File gitRepoBundle =
        new File(BASE_PATH + gitRepoBundleFileName + ".bundle");
    CloneCommand command = Git.cloneRepository();
    command.setDirectory(tempDirectory);
    command.setURI("file://" + gitRepoBundle.getAbsolutePath());
    return command.call();

  }

  /**
   * Creates a unique directory for test purposes.
   *
   * @param name of the subdirectory
   * @return a unique directory for a test
   * @throws IOException
   */
  private static File createTempDirectory(String name) throws IOException {
    File tempFile = File.createTempFile("tmp", "");
    if (!tempFile.delete()) {
      throw new IOException(
          "Cannot obtain unique test file for unique test path");
    }
    File tempDirectory = new File(tempFile, name);
    FileUtils.mkdirs(tempDirectory);
    return tempDirectory.getCanonicalFile();
  }

}
