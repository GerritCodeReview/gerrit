// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.DeprecatedIdentifierException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Project;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ChangeIdIT extends AbstractDaemonTest {
  private ChangeInfo changeInfo;

  @Before
  public void setup() throws Exception {
    changeInfo = gApi.changes().create(new ChangeInput(project.get(), "master", "msg")).get();
  }

  @Test
  public void projectChangeNumberReturnsChange() throws Exception {
    ChangeApi cApi = gApi.changes().id(project.get(), changeInfo._number);
    assertThat(cApi.get().changeId).isEqualTo(changeInfo.changeId);
  }

  @Test
  public void projectChangeNumberReturnsChangeWhenProjectContainsSlashes() throws Exception {
    Project.NameKey p = createProject("foo/bar");
    ChangeInfo ci = gApi.changes().create(new ChangeInput(p.get(), "master", "msg")).get();
    ChangeApi cApi = gApi.changes().id(p.get(), ci._number);
    assertThat(cApi.get().changeId).isEqualTo(ci.changeId);
  }

  @Test
  public void wrongProjectInProjectChangeNumberReturnsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: unknown~" + changeInfo._number);
    gApi.changes().id("unknown", changeInfo._number);
  }

  @Test
  public void wrongIdInProjectChangeNumberReturnsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + project.get() + "~" + Integer.MAX_VALUE);
    gApi.changes().id(project.get(), Integer.MAX_VALUE);
  }

  @Test
  public void changeNumberReturnsChange() throws Exception {
    ChangeApi cApi = gApi.changes().id(changeInfo._number);
    assertThat(cApi.get().changeId).isEqualTo(changeInfo.changeId);
  }

  @Test
  public void wrongChangeNumberReturnsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.changes().id(Integer.MAX_VALUE);
  }

  @Test
  public void tripletChangeIdReturnsChange() throws Exception {
    ChangeApi cApi = gApi.changes().id(project.get(), changeInfo.branch, changeInfo.changeId);
    assertThat(cApi.get().changeId).isEqualTo(changeInfo.changeId);
  }

  @Test
  public void wrongProjectInTripletChangeIdReturnsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: unknown~" + changeInfo.branch + "~" + changeInfo.changeId);
    gApi.changes().id("unknown", changeInfo.branch, changeInfo.changeId);
  }

  @Test
  public void wrongBranchInTripletChangeIdReturnsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + project.get() + "~unknown~" + changeInfo.changeId);
    gApi.changes().id(project.get(), "unknown", changeInfo.changeId);
  }

  @Test
  public void wrongIdInTripletChangeIdReturnsNotFound() throws Exception {
    String unknownId = "I1234567890";
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage(
        "Not found: " + project.get() + "~" + changeInfo.branch + "~" + unknownId);
    gApi.changes().id(project.get(), changeInfo.branch, unknownId);
  }

  @Test
  public void changeIdReturnsChange() throws Exception {
    // ChangeId is not unique and this method needs a unique changeId to work.
    // Hence we generate a new change with a different content.
    ChangeInfo ci =
        gApi.changes().create(new ChangeInput(project.get(), "master", "different message")).get();
    ChangeApi cApi = gApi.changes().id(ci.changeId);
    assertThat(cApi.get()._number).isEqualTo(ci._number);
  }

  @Test
  public void wrongChangeIdReturnsNotFound() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    gApi.changes().id("I1234567890");
  }

  @Test
  @GerritConfig(
      name = "change.api.allowedIdentifier",
      values = {"PROJECT_NUMERIC_ID", "NUMERIC_ID"})
  public void deprecatedChangeIdReturnsBadRequest() throws Exception {
    // project~changeNumber still works
    ChangeApi cApi1 = gApi.changes().id(project.get(), changeInfo._number);
    assertThat(cApi1.get().changeId).isEqualTo(changeInfo.changeId);
    // Change number still works
    ChangeApi cApi2 = gApi.changes().id(changeInfo._number);
    assertThat(cApi2.get().changeId).isEqualTo(changeInfo.changeId);
    // IHash throws
    ChangeInfo ci =
        gApi.changes().create(new ChangeInput(project.get(), "master", "different message")).get();
    exception.expect(DeprecatedIdentifierException.class);
    exception.expectMessage(
        "The provided change identifier "
            + ci.changeId
            + " is deprecated. Use 'project~changeNumber' instead.");
    gApi.changes().id(ci.changeId);
  }
}
