package com.google.gerrit.acceptance.server.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountLimits;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.junit.Test;

public class AccountLimitIT extends AbstractDaemonTest {

  @Inject private AccountLimits.Factory accountLimitFactory;

  @Inject private Provider<CurrentUser> self;
  @Inject private ProjectOperations projectOperations;

  @Test
  @GerritConfig(name = "index.maxLimit", value = "10000")
  public void queryLimit_capabilitySetToDefault_respectsMaxIndexBackendLimitIfNotSet() {
    assertThat(accountLimitFactory.create(self.get()).getQueryLimit()).isEqualTo(10000);
  }

  @Test
  @GerritConfig(name = "index.maxLimit", value = "10000")
  public void queryLimit_capabilitySet_respectCapabilityLimit() {
    assertThat(accountLimitFactory.create(self.get()).getQueryLimit()).isEqualTo(10000);
    projectOperations
        .allProjectsForUpdate()
        .add(
            allowCapability(GlobalCapability.QUERY_LIMIT)
                .group(SystemGroupBackend.REGISTERED_USERS)
                .range(0, 2))
        .update();

    assertThat(accountLimitFactory.create(self.get()).getQueryLimit()).isEqualTo(2);
  }
}
