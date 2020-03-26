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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.client.ArchiveFormat;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.git.ObjectIds;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.Test;

public class GetArchiveIT extends AbstractDaemonTest {
  private static final String DIRECTORY_NAME = "foo";
  private static final String FILE_NAME = DIRECTORY_NAME + "/bar.txt";
  private static final String FILE_CONTENT = "some content";

  private String changeId;
  private RevCommit commit;

  @Before
  public void setUp() throws Exception {
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "My Change", FILE_NAME, FILE_CONTENT);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    changeId = result.getChangeId();
    commit = result.getCommit();
  }

  @Test
  public void formatNotSpecified() throws Exception {
    BadRequestException ex =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(changeId).current().getArchive(null));
    assertThat(ex).hasMessageThat().isEqualTo("format is not specified");
  }

  @Test
  public void unknownFormat() throws Exception {
    // Test this by a REST call, since the Java API doesn't allow to specify an
    // unknown format.
    RestResponse res =
        adminRestSession.get(
            String.format(
                "/changes/%s/revisions/current/archive?format=%s", changeId, "unknownFormat"));
    res.assertBadRequest();
    assertThat(res.getEntityContent()).isEqualTo("unknown archive format");
  }

  @Test
  public void zipFormatIsDisabled() throws Exception {
    MethodNotAllowedException ex =
        assertThrows(
            MethodNotAllowedException.class,
            () -> gApi.changes().id(changeId).current().getArchive(ArchiveFormat.ZIP));
    assertThat(ex).hasMessageThat().isEqualTo("zip format is disabled");
  }

  @Test
  public void getTarArchive() throws Exception {
    BinaryResult res = gApi.changes().id(changeId).current().getArchive(ArchiveFormat.TAR);
    assertThat(res.getAttachmentName())
        .isEqualTo(commit.abbreviate(ObjectIds.ABBREV_STR_LEN).name() + ".tar");
    assertThat(res.getContentType()).isEqualTo("application/x-tar");
    assertThat(res.canGzip()).isFalse();

    byte[] archiveBytes = getBinaryContent(res);
    HashMap<String, String> archiveEntries = getTarContent(new ByteArrayInputStream(archiveBytes));
    assertThat(archiveEntries).containsExactly(DIRECTORY_NAME + "/", null, FILE_NAME, FILE_CONTENT);
  }

  @Test
  public void getTgzArchive() throws Exception {
    BinaryResult res = gApi.changes().id(changeId).current().getArchive(ArchiveFormat.TGZ);
    assertThat(res.getAttachmentName())
        .isEqualTo(commit.abbreviate(ObjectIds.ABBREV_STR_LEN).name() + ".tar.gz");
    assertThat(res.getContentType()).isEqualTo("application/x-gzip");
    assertThat(res.canGzip()).isFalse();

    byte[] archiveBytes = getBinaryContent(res);
    GzipCompressorInputStream gzipIn =
        new GzipCompressorInputStream(new ByteArrayInputStream(archiveBytes));
    HashMap<String, String> archiveEntries = getTarContent(gzipIn);
    assertThat(archiveEntries).containsExactly(DIRECTORY_NAME + "/", null, FILE_NAME, FILE_CONTENT);
  }

  private HashMap<String, String> getTarContent(InputStream in) throws Exception {
    HashMap<String, String> archiveEntries = new HashMap<>();
    int bufferSize = 100;
    try (TarArchiveInputStream tarIn = new TarArchiveInputStream(in)) {
      TarArchiveEntry entry;
      while ((entry = tarIn.getNextTarEntry()) != null) {
        if (entry.isDirectory()) {
          archiveEntries.put(entry.getName(), null);
        } else {
          byte data[] = new byte[bufferSize];
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          try (BufferedOutputStream bufferedOut = new BufferedOutputStream(out, bufferSize)) {
            int count;
            while ((count = tarIn.read(data, 0, bufferSize)) != -1) {
              bufferedOut.write(data, 0, count);
            }
          }
          archiveEntries.put(entry.getName(), out.toString());
        }
      }
    }
    return archiveEntries;
  }

  private byte[] getBinaryContent(BinaryResult res) throws Exception {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      res.writeTo(out);
      return out.toByteArray();
    } finally {
      res.close();
    }
  }
}
