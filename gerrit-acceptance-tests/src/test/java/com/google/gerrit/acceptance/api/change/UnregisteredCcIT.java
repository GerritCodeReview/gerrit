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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.AddressInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.git.ProjectConfig;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class UnregisteredCcIT extends AbstractDaemonTest {

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.setEnableUnregisteredCcs(true);
    saveProjectConfig(project, cfg);
  }

  @Test
  public void addUnregisteredCc() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);

    ChangeInfo info =
        gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    assertThat(info.unregisteredCcs).containsExactly(adr);
  }

  @Test
  public void removeUnregisteredCc() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);

    gApi.changes().id(r.getChangeId()).deleteUnregisteredCc(adr);
    ChangeInfo info =
        gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    assertThat(info.unregisteredCcs).isEmpty();
  }

  @Test
  public void rejectMissingName() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo("", "foo.bar@gerritcodereview.com");
    PushOneCommit.Result r = createChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name and email are required");
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);
  }

  @Test
  public void rejectMissingEmail() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo("Foo Bar", "");
    PushOneCommit.Result r = createChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("name and email are required");
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);
  }

  @Test
  public void rejectMalformedEmail() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo("Foo Bar", "foo.com");
    PushOneCommit.Result r = createChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("email invalid");
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);
  }

  @Test
  public void rejectOnNonPublicChange() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo("Foo Bar", "foo.bar@gerritcodereview.com");
    PushOneCommit.Result r = createDraftChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("change is not publicly visible");
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);
  }

  @Test
  public void rejectWhenFeatureIsDisabled() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.setEnableUnregisteredCcs(false);
    saveProjectConfig(project, cfg);

    AddressInfo adr = new AddressInfo("Foo Bar", "foo.bar@gerritcodereview.com");
    PushOneCommit.Result r = createChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("adding unregistered CCs not allowed");
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);
  }

  @Test
  public void rejectExistingUser() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AddressInfo adr = new AddressInfo(user.fullName, user.email);
    PushOneCommit.Result r = createChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("can't add existing account");
    gApi.changes().id(r.getChangeId()).addUnregisteredCc(adr);
  }
}
