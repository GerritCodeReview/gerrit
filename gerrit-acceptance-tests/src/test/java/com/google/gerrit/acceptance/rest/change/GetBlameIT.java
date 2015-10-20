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

package com.google.gerrit.acceptance.rest.change;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.BlameInfo;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.GetBlame;
import com.google.gerrit.server.change.Revisions;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GetBlameIT extends AbstractSubmit {

  @Inject
  private GetBlame getBlame;
  @Inject
  private Revisions revisions;
  @Inject
  private ChangesCollection changes;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }

  @Test
  public void blameNonExist() throws IOException {
    RestResponse r =
      userSession.get("/changes/1/revisions/1/files/a.txt/blame");
    assertEquals(404, r.getStatusCode());
  }

  @Test
  public void oneChangeBlame() throws Exception {
    PushOneCommit.Result c = createChange();

    RestResponse r = userSession.get(
      "/changes/" + c.getChange().currentPatchSet().getId().getParentKey().get()
        + "/revisions/" + c.getChange().currentPatchSet().getId().get()
        + "/files/" + c.getChange().currentFilePaths().get(0) + "/blame");
    BlameInfo info = newGson().fromJson(r.getReader(), BlameInfo.class);
    assertNotNull(info);
    assertEquals(1, info.blames.size());
    assertBlame(c, info.blames.get(0));
  }

  @Test
  public void blameBase() throws Exception {
    PushOneCommit.Result c1 =
      createChange("Change 1", "a.txt", "content");
    submit(c1.getChangeId());

    PushOneCommit.Result c2 =
      createChange("Change 2", "a.txt", "content\nother content");

    RestResponse r = userSession.get(
      "/changes/" + c2.getChange().currentPatchSet().getId().getParentKey()
        .get()
        + "/revisions/" + c2.getChange().currentPatchSet().getId().get()
        + "/files/" + c2.getChange().currentFilePaths().get(0) + "/blame"
        + "?base=t");

    BlameInfo info = newGson().fromJson(r.getReader(), BlameInfo.class);
    assertNotNull(info);
    assertEquals(1, info.blames.size());
    assertBlame(c1, info.blames.get(0));
  }

  //  @Test
  //  public void blameDirect() throws Exception {
  //    PushOneCommit.Result c1 =
  //      createChange("Change 1", "a.txt", "content");
  //    submit(c1.getChangeId());
  //
  //    ChangeResource changeResource = changes.parse(c1.getChange().getId());
  //    RevisionResource revisionResource =
  //      revisions.parse(changeResource, IdString.fromUrl("current"));
  //    FileResource fileResource = new FileResource(revisionResource, "a.txt");
  //    Response<BlameInfo> response = getBlame.apply(fileResource);
  //    BlameInfo info = response.value();
  //
  //    assertNotNull(info);
  //    assertEquals(1, info.blames.size());
  //    assertBlame(c1, info.blames.get(0));
  //  }

  private static void assertBlame(PushOneCommit.Result expected,
    BlameInfo.Blame actual)
    throws OrmException {
    assertEquals(expected.getCommit().getId().name(), actual.meta.id);
    assertEquals(expected.getCommit().getAuthorIdent().getName(),
      actual.meta.author);
    assertEquals(expected.getCommit().getCommitTime(), actual.meta.time);
    assertEquals(
      expected.getChange().currentPatchSet().getId().getParentKey().get(),
      actual.meta.changeId);
    assertEquals(expected.getChange().currentPatchSet().getId().get(),
      actual.meta.patchSetId);
  }
}
