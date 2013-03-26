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

package com.google.gerrit.server.change;

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.ChangeJson.ChangeMessageInfo;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.eclipse.jgit.lib.Config;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ChangeJsonTest extends TestCase {

  public void testFormatChangeMessages() throws OrmException {

    // create mocks
    final CurrentUser currentUser = createMock(CurrentUser.class);
    final GitRepositoryManager grm = createMock(GitRepositoryManager.class);
    final AccountByEmailCache abec = createMock(AccountByEmailCache.class);
    final AccountCache ac = createMock(AccountCache.class);
    final AccountInfo.Loader.Factory alf =
        createMock(AccountInfo.Loader.Factory.class);
    final CapabilityControl.Factory ccf =
        createMock(CapabilityControl.Factory.class);
    final GroupBackend gb = createMock(GroupBackend.class);
    final Realm r = createMock(Realm.class);
    final PatchListCache plc = createMock(PatchListCache.class);
    final ProjectCache pc = createMock(ProjectCache.class);
    final Config config = new Config();  // unable to mock
    final ReviewDb rdb = createMock(ReviewDb.class);
    final ChangeAccess ca = createMock(ChangeAccess.class);
    final PatchSetAccess psa = createMock(PatchSetAccess.class);
    final PatchSetApprovalAccess psaa =
        createMock(PatchSetApprovalAccess.class);
    final ChangeMessageAccess cma = createMock(ChangeMessageAccess.class);
    AccountInfo.Loader accountLoader = createMock(AccountInfo.Loader.class);

    // create ChangeJson instance
    Module mod = new Module() {
      @Override
      public void configure(Binder binder) {
        binder.bind(CurrentUser.class).toInstance(currentUser);
        binder.bind(GitRepositoryManager.class).toInstance(grm);
        binder.bind(AccountByEmailCache.class).toInstance(abec);
        binder.bind(AccountCache.class).toInstance(ac);
        binder.bind(AccountInfo.Loader.Factory.class).toInstance(alf);
        binder.bind(CapabilityControl.Factory.class).toInstance(ccf);
        binder.bind(GroupBackend.class).toInstance(gb);
        binder.bind(Realm.class).toInstance(r);
        binder.bind(PatchListCache.class).toInstance(plc);
        binder.bind(ProjectCache.class).toInstance(pc);
        binder.bind(ReviewDb.class).toInstance(rdb);
        binder.bind(Config.class).annotatedWith(GerritServerConfig.class)
            .toInstance(config);
        binder.bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toInstance("");
        binder.bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toInstance("");
      }
    };
    ChangeJson json = Guice.createInjector(mod).getInstance(ChangeJson.class);

    // define mock behavior for tests
    expect(alf.create(anyBoolean())).andReturn(accountLoader).anyTimes();

    Project.NameKey proj = new Project.NameKey("ProjectNameKey");
    Branch.NameKey forBranch = new Branch.NameKey(proj, "BranchNameKey");

    Change.Key changeKey123 = new Change.Key("ChangeKey123");
    Change.Id changeId123 = new Change.Id(123);
    Change change123 = new Change(changeKey123, changeId123, null, forBranch);

    Change.Key changeKey234 = new Change.Key("ChangeKey234");
    Change.Id changeId234 = new Change.Id(234);
    Change change234 = new Change(changeKey234, changeId234, null, forBranch);

    expect(ca.get(Sets.newHashSet(changeId123)))
        .andAnswer(results(Change.class, change123)).anyTimes();
    expect(ca.get(changeId123)).andReturn(change123).anyTimes();
    expect(ca.get(Sets.newHashSet(changeId234)))
        .andAnswer(results(Change.class, change234));
    expect(ca.get(changeId234)).andReturn(change234);
    expect(rdb.changes()).andReturn(ca).anyTimes();

    expect(psa.get(EasyMock.<Iterable<PatchSet.Id>>anyObject()))
        .andAnswer(results(PatchSet.class)).anyTimes();
    expect(rdb.patchSets()).andReturn(psa).anyTimes();

    expect(psaa.byPatchSet(anyObject(PatchSet.Id.class)))
        .andAnswer(results(PatchSetApproval.class)).anyTimes();
    expect(rdb.patchSetApprovals()).andReturn(psaa).anyTimes();

    expect(currentUser.getStarredChanges())
        .andReturn(Collections.<Change.Id>emptySet()).anyTimes();

    long timeBase = System.currentTimeMillis();
    ChangeMessage changeMessage1 =changeMessage(
        changeId123, "cm1", 111, timeBase, 1111, "first message");
    ChangeMessage changeMessage2 = changeMessage(
        changeId123, "cm2", 222, timeBase + 1000, 1111, "second message");
    expect(cma.byChange(changeId123))
        .andAnswer(results(ChangeMessage.class, changeMessage2, changeMessage1))
        .anyTimes();
    expect(cma.byChange(changeId234)).andAnswer(results(ChangeMessage.class));
    expect(rdb.changeMessages()).andReturn(cma).anyTimes();

    expect(accountLoader.get(anyObject(Account.Id.class)))
        .andAnswer(accountForId()).anyTimes();
    accountLoader.fill();
    expectLastCall().anyTimes();

    replay(rdb, ca, psa, psaa, alf, currentUser, cma, accountLoader);

    // test 1: messages not returned by default
    ChangeInfo ci = json.format(new ChangeData(changeId123));
    assertNull(ci.messages);

    json.addOption(ListChangesOption.MESSAGES);

    // test 2: two change messages, in chronological order
    ci = json.format(new ChangeData(changeId123));
    assertNotNull(ci.messages);
    assertEquals(2, ci.messages.size());
    Iterator<ChangeMessageInfo> cmis = ci.messages.iterator();
    assertEquals(changeMessage1, cmis.next());
    assertEquals(changeMessage2, cmis.next());

    // test 3: no change messages
    ci = json.format(new ChangeData(changeId234));
    assertNotNull(ci.messages);
    assertEquals(0, ci.messages.size());
  }

  private static IAnswer<AccountInfo> accountForId() {
    return new IAnswer<AccountInfo>() {
      @Override
      public AccountInfo answer() throws Throwable {
        Account.Id id = (Account.Id) EasyMock.getCurrentArguments()[0];
        AccountInfo ai = new AccountInfo(id);
        return ai;
      }};
  }

  private static <T> IAnswer<ResultSet<T>> results(Class<T> type, T... items) {
    final List<T> list = Lists.newArrayList(items);
    return new IAnswer<ResultSet<T>>() {
      @Override
      public ResultSet<T> answer() throws Throwable {
        return new ListResultSet<T>(list);
      }};
  }

  private static void assertEquals(ChangeMessage cm, ChangeMessageInfo cmi) {
    assertEquals(cm.getPatchSetId().get(), (int) cmi._revisionNumber);
    assertEquals(cm.getMessage(), cmi.message);
    assertEquals(cm.getKey().get(), cmi.id);
    assertEquals(cm.getWrittenOn(), cmi.date);
    assertNotNull(cmi.author);
    assertEquals(cm.getAuthor(), cmi.author._id);
  }

  private static ChangeMessage changeMessage(Change.Id changeId,
      String uuid, int accountId, long time, int psId, String message) {
    ChangeMessage.Key key = new ChangeMessage.Key(changeId, uuid);
    Account.Id author = new Account.Id(accountId);
    Timestamp updated = new Timestamp(time);
    PatchSet.Id ps = new PatchSet.Id(changeId, psId);
    ChangeMessage changeMessage = new ChangeMessage(key, author, updated, ps);
    changeMessage.setMessage(message);
    return changeMessage;
  }
}
