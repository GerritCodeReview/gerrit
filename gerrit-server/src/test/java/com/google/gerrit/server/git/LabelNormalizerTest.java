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

package com.google.gerrit.server.git;

import static com.google.gerrit.common.data.Permission.forLabel;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.allow;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.LabelNormalizer.Result;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gerrit.testutil.InMemoryModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
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
  @Inject private InMemoryDatabase schemaFactory;
  @Inject private LabelNormalizer norm;
  @Inject private MetaDataUpdate.User metaDataUpdateFactory;
  @Inject private ProjectCache projectCache;
  @Inject private SchemaCreator schemaCreator;
  @Inject protected ThreadLocalRequestContext requestContext;

  private LifecycleManager lifecycle;
  private ReviewDb db;
  private Account.Id userId;
  private IdentifiedUser user;
  private Change change;

  @Before
  public void setUpInjector() throws Exception {
    Injector injector = Guice.createInjector(new InMemoryModule());
    injector.injectMembers(this);
    lifecycle = new LifecycleManager();
    lifecycle.add(injector);
    lifecycle.start();

    db = schemaFactory.open();
    schemaCreator.create(db);
    userId = accountManager.authenticate(AuthRequest.forUser("user")).getAccountId();
    user = userFactory.create(userId);

    requestContext.setContext(
        new RequestContext() {
          @Override
          public CurrentUser getUser() {
            return user;
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return Providers.of(db);
          }
        });

    configureProject();
    setUpChange();
  }

  private void configureProject() throws Exception {
    ProjectConfig pc = loadAllProjects();
    for (AccessSection sec : pc.getAccessSections()) {
      for (String label : pc.getLabelSections().keySet()) {
        sec.removePermission(forLabel(label));
      }
    }
    LabelType lt =
        category("Verified", value(1, "Verified"), value(0, "No score"), value(-1, "Fails"));
    pc.getLabelSections().put(lt.getName(), lt);
    save(pc);
  }

  private void setUpChange() throws Exception {
    change =
        new Change(
            new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
            new Change.Id(1),
            userId,
            new Branch.NameKey(allProjects, "refs/heads/master"),
            TimeUtil.nowTs());
    PatchSetInfo ps = new PatchSetInfo(new PatchSet.Id(change.getId(), 1));
    ps.setSubject("Test change");
    change.setCurrentPatchSet(ps);
    db.changes().insert(ImmutableList.of(change));
  }

  @After
  public void tearDown() {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    requestContext.setContext(null);
    if (db != null) {
      db.close();
    }
    InMemoryDatabase.drop(schemaFactory);
  }

  @Test
  public void normalizeByPermission() throws Exception {
    ProjectConfig pc = loadAllProjects();
    allow(pc, forLabel("Code-Review"), -1, 1, REGISTERED_USERS, "refs/heads/*");
    allow(pc, forLabel("Verified"), -1, 1, REGISTERED_USERS, "refs/heads/*");
    save(pc);

    PatchSetApproval cr = psa(userId, "Code-Review", 2);
    PatchSetApproval v = psa(userId, "Verified", 1);
    assertEquals(
        Result.create(list(v), list(copy(cr, 1)), list()), norm.normalize(change, list(cr, v)));
  }

  @Test
  public void normalizeByType() throws Exception {
    ProjectConfig pc = loadAllProjects();
    allow(pc, forLabel("Code-Review"), -5, 5, REGISTERED_USERS, "refs/heads/*");
    allow(pc, forLabel("Verified"), -5, 5, REGISTERED_USERS, "refs/heads/*");
    save(pc);

    PatchSetApproval cr = psa(userId, "Code-Review", 5);
    PatchSetApproval v = psa(userId, "Verified", 5);
    assertEquals(
        Result.create(list(), list(copy(cr, 2), copy(v, 1)), list()),
        norm.normalize(change, list(cr, v)));
  }

  @Test
  public void emptyPermissionRangeOmitsResult() throws Exception {
    PatchSetApproval cr = psa(userId, "Code-Review", 1);
    PatchSetApproval v = psa(userId, "Verified", 1);
    assertEquals(Result.create(list(), list(), list(cr, v)), norm.normalize(change, list(cr, v)));
  }

  @Test
  public void explicitZeroVoteOnNonEmptyRangeIsPresent() throws Exception {
    ProjectConfig pc = loadAllProjects();
    allow(pc, forLabel("Code-Review"), -1, 1, REGISTERED_USERS, "refs/heads/*");
    save(pc);

    PatchSetApproval cr = psa(userId, "Code-Review", 0);
    PatchSetApproval v = psa(userId, "Verified", 0);
    assertEquals(Result.create(list(cr), list(), list(v)), norm.normalize(change, list(cr, v)));
  }

  private ProjectConfig loadAllProjects() throws Exception {
    try (Repository repo = repoManager.openRepository(allProjects)) {
      ProjectConfig pc = new ProjectConfig(allProjects);
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
    return new PatchSetApproval(
        new PatchSetApproval.Key(change.currentPatchSetId(), accountId, new LabelId(label)),
        (short) value,
        TimeUtil.nowTs());
  }

  private PatchSetApproval copy(PatchSetApproval src, int newValue) {
    PatchSetApproval result = new PatchSetApproval(src.getKey().getParentKey(), src);
    result.setValue((short) newValue);
    return result;
  }

  private static List<PatchSetApproval> list(PatchSetApproval... psas) {
    return ImmutableList.<PatchSetApproval>copyOf(psas);
  }
}
