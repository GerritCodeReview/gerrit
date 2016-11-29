// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.rest.change.AbstractSubmit;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.reviewdb.client.Project;

import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class PreviewSubmitIT extends AbstractSubmit {

  @Test
  public void testTGZ() throws Exception {
    Project.NameKey p1 = createProject("project-name");

    TestRepository<?> repo1 = cloneProject(p1);
    PushOneCommit.Result change1 = createChange(repo1, "master",
        "test", "a.txt", "1", "topic");
    approve(change1.getChangeId());

    // get a preview before submitting:
    BinaryResult request = submitPreview(change1.getChangeId(), "tgz");


    assertThat(request.getContentType()).isEqualTo("application/x-gzip");
    File tempfile = File.createTempFile("test", null);
    request.writeTo(new FileOutputStream(tempfile));

    InputStream is = new GZIPInputStream(new FileInputStream(tempfile));

    final List<String> untaredFiles = new LinkedList<>();

    try (TarArchiveInputStream debInputStream = (TarArchiveInputStream)
        new ArchiveStreamFactory().createArchiveInputStream("tar", is)) {
      TarArchiveEntry entry = null;
      while ((entry = (TarArchiveEntry)debInputStream.getNextEntry()) != null) {
        untaredFiles.add(entry.getName());
      }
    }

    assertThat(untaredFiles).containsExactly(name("project-name") + ".git");
  }

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }
}