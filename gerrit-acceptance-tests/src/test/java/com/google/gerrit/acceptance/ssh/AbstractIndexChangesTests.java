package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth8.assertThat;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Injector;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@NoHttpd
@Ignore
@RunWith(ConfigSuite.class)
@UseLocalDisk
@UseSsh
public abstract class AbstractIndexChangesTests extends AbstractDaemonTest {
  /** @param injector injector */
  public abstract void configureIndex(Injector injector) throws Exception;

  @Test
  public void testIndexChange() throws Exception {
    configureIndex(server.getTestInjector());
    PushOneCommit.Result change = createChange("first change", "test1.txt", "test1");
    String changeId = change.getChangeId();
    String changeLegacyId = change.getChange().getId().toString();

    disableChangeIndexWrites();

    amendChange(changeId, "second test", "test2.txt", "test2");

    assertThat(gApi.changes().query("message:second").get().stream().map(c -> c.changeId))
        .isEmpty();
    enableChangeIndexWrites();

    String cmd = Joiner.on(" ").join("gerrit", "index", "changes", changeLegacyId);
    adminSshSession.exec(cmd);

    assertThat(gApi.changes().query("message:second").get().stream().map(c -> c.changeId))
        .containsExactly(changeId);
  }
}
