// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.getChangeId;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class ListCommitFilesIT extends AbstractDaemonTest {
  private static String SUBJECT_1 = "subject 1";
  private static String SUBJECT_2 = "subject 2";
  private static String FILE_A = "a.txt";
  private static String FILE_B = "b.txt";

  @Test
  public void listCommitFiles() throws Exception {
    commitBuilder().add(FILE_B, "2").message(SUBJECT_1).create();
    pushHead(testRepo, "refs/heads/master", false);

    RevCommit a = commitBuilder().add(FILE_A, "1").rm(FILE_B).message(SUBJECT_2).create();
    String id = getChangeId(testRepo, a).get();
    pushHead(testRepo, "refs/for/master", false);

    RestResponse r =
        userRestSession.get("/projects/" + project.get() + "/commits/" + a.name() + "/files/");
    r.assertOK();
    Type type = new TypeToken<Map<String, FileInfo>>() {}.getType();
    Map<String, FileInfo> files1 = newGson().fromJson(r.getReader(), type);
    r.consume();

    r = userRestSession.get("/changes/" + id + "/revisions/" + a.name() + "/files");
    r.assertOK();
    Map<String, FileInfo> files2 = newGson().fromJson(r.getReader(), type);
    r.consume();

    assertThat(files1).isEqualTo(files2);
  }

  @Test
  public void listMergeCommitFiles() throws Exception {
    PushOneCommit.Result result = createMergeCommitChange("refs/for/master");

    RestResponse r =
        userRestSession.get(
            "/projects/"
                + project.get()
                + "/commits/"
                + result.getCommit().name()
                + "/files/?parent=2");
    r.assertOK();
    Type type = new TypeToken<Map<String, FileInfo>>() {}.getType();
    Map<String, FileInfo> files1 = newGson().fromJson(r.getReader(), type);
    r.consume();

    r =
        userRestSession.get(
            "/changes/"
                + result.getChangeId()
                + "/revisions/"
                + result.getCommit().name()
                + "/files/?parent=2");
    r.assertOK();
    Map<String, FileInfo> files2 = newGson().fromJson(r.getReader(), type);
    r.consume();

    assertThat(files1).isEqualTo(files2);
  }
}
