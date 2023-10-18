// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.integration.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_CONTENT;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.GerritServer.TestSshServerAddress;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.junit.Test;

@NoHttpd
@UseSsh
public class UploadArchiveIT extends StandaloneSiteTest {
  private static final String[] SSH_KEYGEN_CMD =
      new String[] {"ssh-keygen", "-t", "rsa", "-q", "-P", "", "-f"};
  private static final String GIT_SSH_COMMAND =
      "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -o 'IdentitiesOnly yes' -i";
  private static final String ARCHIVE = "archive";

  @Inject private GerritApi gApi;
  @Inject private @TestSshServerAddress InetSocketAddress sshAddress;

  private String sshDestination;
  private String identityPath;
  private Project.NameKey project;
  private CommitInfo commit;

  @Test
  @GerritConfig(name = "download.archive", value = "off")
  public void archiveFeatureOff() throws Exception {
    try (ServerContext ctx = startServer()) {
      setUpTestHarness(ctx);
      assertArchiveNotPermitted();
    }
  }

  @Test
  @GerritConfig(
      name = "download.archive",
      values = {"tar", "tbz2", "tgz", "txz"})
  public void zipFormatDisabled() throws Exception {
    try (ServerContext ctx = startServer()) {
      setUpTestHarness(ctx);
      assertArchiveNotPermitted();
    }
  }

  @Test
  public void verifyUploadArchiveFormats() throws Exception {
    try (ServerContext ctx = startServer()) {
      setUpTestHarness(ctx);
      setUpChange();

      for (String f : Arrays.asList("zip", "tar", "tar.gz", "tar.bz2", "tar.xz")) {
        verifyUploadArchive(f);
      }
    }
  }

  private void verifyUploadArchive(String format) throws Exception {
    Path outputPath = sitePaths.data_dir.resolve(ARCHIVE);
    execute(
        cmd(format, commit.commit),
        sitePaths.data_dir.toFile(),
        ImmutableMap.of("GIT_SSH_COMMAND", GIT_SSH_COMMAND + identityPath),
        outputPath);
    try (InputStream fi = Files.newInputStream(outputPath);
        InputStream bi = new BufferedInputStream(fi);
        ArchiveInputStream archive = archiveStreamForFormat(bi, format)) {
      assertEntries(archive);
    }
  }

  private ArchiveInputStream archiveStreamForFormat(InputStream bi, String format)
      throws IOException {
    switch (format) {
      case "zip":
        return new ZipArchiveInputStream(bi);
      case "tar":
        return new TarArchiveInputStream(bi);
      case "tar.gz":
        return new TarArchiveInputStream(new GzipCompressorInputStream(bi));
      case "tar.bz2":
        return new TarArchiveInputStream(new BZip2CompressorInputStream(bi));
      case "tar.xz":
        return new TarArchiveInputStream(new XZCompressorInputStream(bi));
      default:
        throw new IllegalArgumentException("Unknown archive format: " + format);
    }
  }

  private void setUpTestHarness(ServerContext ctx) throws RestApiException, Exception {
    ctx.getInjector().injectMembers(this);
    project = Project.nameKey("upload-archive-project-test");
    gApi.projects().create(project.get());
    setUpAuthentication();

    sshDestination =
        String.format(
            "ssh://%s@%s:%s/%s",
            admin.username(), sshAddress.getHostName(), sshAddress.getPort(), project.get());
    identityPath =
        sitePaths.data_dir.resolve(String.format("id_rsa_%s", admin.username())).toString();
  }

  private void setUpAuthentication() throws Exception {
    execute(
        ImmutableList.<String>builder()
            .add(SSH_KEYGEN_CMD)
            .add(String.format("id_rsa_%s", admin.username()))
            .build());
    gApi.accounts()
        .id(admin.id().get())
        .addSshKey(
            new String(
                java.nio.file.Files.readAllBytes(
                    sitePaths.data_dir.resolve(String.format("id_rsa_%s.pub", admin.username()))),
                UTF_8));
  }

  private ImmutableList<String> cmd(String format, String commit) {
    return ImmutableList.<String>builder()
        .add("git")
        .add("archive")
        .add("-f=" + format)
        .add("--prefix=" + commit + "/")
        // --remote makes git execute "git archive" on the server through SSH.
        // The Gerrit/JGit version of the command understands the --compression-level
        // argument below.
        .add("--remote=" + sshDestination)
        .add("--compression-level=1") // set to 1 to reduce the memory footprint
        .add(commit)
        .add(FILE_NAME)
        .build();
  }

  private String execute(ImmutableList<String> cmd) throws Exception {
    return execute(cmd, sitePaths.data_dir.toFile(), ImmutableMap.of());
  }

  private void assertArchiveNotPermitted() {
    IOException exception =
        assertThrows(
            IOException.class,
            () ->
                execute(
                    cmd("zip", "master"),
                    sitePaths.data_dir.toFile(),
                    ImmutableMap.of("GIT_SSH_COMMAND", GIT_SSH_COMMAND + identityPath)));
    assertThat(exception)
        .hasMessageThat()
        .contains("fatal: upload-archive not permitted for format zip");
  }

  private void setUpChange() throws Exception {
    ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
    in.newBranch = true;
    String changeId = gApi.changes().create(in).info().changeId;

    gApi.changes().id(changeId).edit().modifyFile(FILE_NAME, RawInputUtil.create(FILE_CONTENT));
    gApi.changes().id(changeId).edit().publish();

    commit = gApi.changes().id(changeId).current().commit(false);
  }

  private void assertEntries(ArchiveInputStream o) throws IOException {
    Set<String> entryNames = new TreeSet<>();
    ArchiveEntry e;
    while ((e = o.getNextEntry()) != null) {
      entryNames.add(e.getName());
    }

    assertThat(entryNames)
        .containsExactly(
            String.format("%s/", commit.commit), String.format("%s/%s", commit.commit, FILE_NAME))
        .inOrder();
  }
}
