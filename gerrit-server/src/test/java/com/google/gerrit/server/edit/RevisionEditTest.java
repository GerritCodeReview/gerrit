package com.google.gerrit.server.edit;

import static com.google.inject.Scopes.SINGLETON;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.Realm;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.testutil.FakeAccountCache;
import com.google.gerrit.testutil.FakeRealm;
import com.google.gerrit.testutil.InMemoryRepositoryManager;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

public class RevisionEditTest {
  private static final TimeZone TZ =
      TimeZone.getTimeZone("America/Los_Angeles");

  private PersonIdent serverIdent;
  private Project.NameKey project;
  private InMemoryRepositoryManager repoManager;
  private FakeAccountCache accountCache;
  private IdentifiedUser changeOwner;
  private Injector injector;
  private String systemTimeZone;
  private volatile long clockStepMs;

  @Before
  public void setUp() throws Exception {
    setTimeForTesting();
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());

    serverIdent = new PersonIdent(
        "Gerrit Server", "noreply@gerrit.com", TimeUtil.nowTs(), TZ);
    project = new Project.NameKey("test-project");
    repoManager = new InMemoryRepositoryManager();
    accountCache = new FakeAccountCache();
    Account co = new Account(new Account.Id(1), TimeUtil.nowTs());
    co.setFullName("Change Owner");
    co.setPreferredEmail("change@owner.com");
    accountCache.put(co);
    Account ou = new Account(new Account.Id(2), TimeUtil.nowTs());
    ou.setFullName("Other Account");
    ou.setPreferredEmail("other@account.com");
    accountCache.put(ou);

    injector = Guice.createInjector(new FactoryModule() {
      @Override
      public void configure() {
        install(new GitModule());
        bind(GitRepositoryManager.class).toInstance(repoManager);
        bind(ProjectCache.class).toProvider(Providers.<ProjectCache> of(null));
        bind(CapabilityControl.Factory.class)
            .toProvider(Providers.<CapabilityControl.Factory> of(null));
        bind(Config.class).annotatedWith(GerritServerConfig.class)
            .toInstance(new Config());
        bind(String.class).annotatedWith(AnonymousCowardName.class)
            .toProvider(AnonymousCowardNameProvider.class);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toInstance("http://localhost:8080/");
        bind(Realm.class).to(FakeRealm.class);
        bind(GroupBackend.class).to(SystemGroupBackend.class).in(SINGLETON);
        bind(AccountCache.class).toInstance(accountCache);
        bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class)
            .toInstance(serverIdent);
        bind(GitReferenceUpdated.class)
            .toInstance(GitReferenceUpdated.DISABLED);
      }
    });

    IdentifiedUser.GenericFactory userFactory =
        injector.getInstance(IdentifiedUser.GenericFactory.class);
    changeOwner = userFactory.create(co.getId());
  }

  private void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    clockStepMs = MILLISECONDS.convert(1, SECONDS);
    final AtomicLong clockMs = new AtomicLong(
        new DateTime(2009, 9, 30, 17, 0, 0).getMillis());

    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @After
  public void resetTime() {
    DateTimeUtils.setCurrentMillisSystem();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void revisionEditRef() throws Exception {
    Change c = newChange();
    RevisionEdit edit = new RevisionEdit(changeOwner, c.getId());
    assertEquals(edit.getRefName(), "refs/users/01/1/edit-1");
  }

  private Change newChange() {
    Change.Id changeId = new Change.Id(1);
    Change c = new Change(
        new Change.Key("Iabcd1234abcd1234abcd1234abcd1234abcd1234"),
        changeId,
        changeOwner.getAccount().getId(),
        new Branch.NameKey(project, "master"),
        TimeUtil.nowTs());
    incrementPatchSet(c);
    return c;
  }

  private static void incrementPatchSet(Change change) {
    PatchSet.Id curr = change.currentPatchSetId();
    PatchSetInfo ps = new PatchSetInfo(new PatchSet.Id(
        change.getId(), curr != null ? curr.get() + 1 : 1));
    ps.setSubject("Change subject");
    change.setCurrentPatchSet(ps);
  }
}
