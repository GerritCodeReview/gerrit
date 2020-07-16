// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.entities.Permission.forLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.label;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.change.LabelNormalizer.Result;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link LabelNormalizer}. */
public class LabelNormalizerTest {
  @Inject private AccountManager accountManager;
  @Inject private AllProjectsName allProjects;
  @Inject private GitRepositoryManager repoManager;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private LabelNormalizer norm;
  @Inject private MetaDataUpdate.User metaDataUpdateFactory;
  @Inject private ProjectCache projectCache;
  @Inject private SchemaCreator schemaCreator;
  @Inject protected ThreadLocalRequestContext requestContext;
  @Inject private ChangeNotes.Factory changeNotesFactory;
  @Inject private ProjectConfig.Factory projectConfigFactory;
  @Inject private GerritApi gApi;
  @Inject private ProjectOperations projectOperations;

  private LifecycleManager lifecycle;
  private Account.Id userId;
  private IdentifiedUser user;
  private Change change;
  private ChangeNotes notes;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    schemaCreator.create();
    userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    user = userFactory.create(userId);

    requestContext.setContext(() -> user);

    configureProject();
    setUpChange();
  }

  private void configureProject() throws Exception {
    ProjectConfig pc = loadAllProjects();

    for (AccessSection sec : ImmutableList.copyOf(pc.getAccessSections())) {
      pc.upsertAccessSection(
          sec.getName(),
          updatedSection -> {
            for (String label : pc.getLabelSections().keySet()) {
              updatedSection.removePermission(forLabel(label));
            }
          });
    }
    LabelType lt =
        label("Verified", value(1, "Verified"), value(0, "No score"), value(-1, "Fails"));
    pc.upsertLabelType(lt);
    save(pc);
  }

  private void setUpChange() throws Exception {
    ChangeInput input = new ChangeInput();
    input.project = allProjects.get();
    input.branch = "master";
    input.newBranch = true;
    input.subject = "Test change";
    ChangeInfo info = gApi.changes().create(input).get();
    notes = changeNotesFactory.createChecked(allProjects, Change.id(info._number));
    change = notes.getChange();
  }

  @After
  public void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
  }

  @Test
  public void noNormalizeByPermission() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-1, 1))
        .add(allowLabel("Verified").ref("refs/heads/*").group(REGISTERED_USERS).range(-1, 1))
        .update();

    PatchSetApproval cr = psa(userId, "Code-Review", 2);
    PatchSetApproval v = psa(userId, "Verified", 1);
    assertEquals(Result.create(list(cr, v), list(), list()), norm.normalize(notes, list(cr, v)));
  }

  @Test
  public void normalizeByType() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-5, 5))
        .add(allowLabel("Verified").ref("refs/heads/*").group(REGISTERED_USERS).range(-5, 5))
        .update();

    PatchSetApproval cr = psa(userId, "Code-Review", 5);
    PatchSetApproval v = psa(userId, "Verified", 5);
    assertEquals(
        Result.create(list(), list(copy(cr, 2), copy(v, 1)), list()),
        norm.normalize(notes, list(cr, v)));
  }

  @Test
  public void emptyPermissionRangeKeepsResult() throws Exception {
    PatchSetApproval cr = psa(userId, "Code-Review", 1);
    PatchSetApproval v = psa(userId, "Verified", 1);
    assertEquals(Result.create(list(cr, v), list(), list()), norm.normalize(notes, list(cr, v)));
  }

  @Test
  public void explicitZeroVoteOnNonEmptyRangeIsPresent() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowLabel("Code-Review").ref("refs/heads/*").group(REGISTERED_USERS).range(-1, 1))
        .update();

    PatchSetApproval cr = psa(userId, "Code-Review", 0);
    PatchSetApproval v = psa(userId, "Verified", 0);
    assertEquals(Result.create(list(cr, v), list(), list()), norm.normalize(notes, list(cr, v)));
  }

  private ProjectConfig loadAllProjects() throws Exception {
    try (Repository repo = repoManager.openRepository(allProjects)) {
      ProjectConfig pc = projectConfigFactory.create(allProjects);
      pc.load(repo);
      return pc;
    }
  }

  private void save(ProjectConfig pc) throws Exception {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(pc.getProject().getNameKey(), user)) {
      pc.commit(md);
      projectCache.evict(pc.getProject().getNameKey());
    }
  }

  private PatchSetApproval psa(Account.Id accountId, String label, int value) {
    return PatchSetApproval.builder()
        .key(PatchSetApproval.key(change.currentPatchSetId(), accountId, LabelId.create(label)))
        .value(value)
        .granted(TimeUtil.nowTs())
        .build();
  }

  private PatchSetApproval copy(PatchSetApproval src, int newValue) {
    return src.toBuilder().value(newValue).build();
  }

  private static List<PatchSetApproval> list(PatchSetApproval... psas) {
    return ImmutableList.copyOf(psas);
  }
}
