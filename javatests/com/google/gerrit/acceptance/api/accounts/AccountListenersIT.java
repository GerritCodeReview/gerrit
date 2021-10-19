// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.accounts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.extensions.events.AccountActivationListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the wiring of a real plugin's account listeners
 *
 * <p>This test really puts focus on the wiring of the account listeners. Tests for the inner
 * workings of account activation/deactivation can be found in {@link AccountIT}.
 */
@TestPlugin(
    name = "account-listener-it-plugin",
    sysModule = "com.google.gerrit.acceptance.api.accounts.AccountListenersIT$TestModule")
public class AccountListenersIT extends LightweightPluginDaemonTest {
  @Inject private AccountOperations accountOperations;

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      DynamicSet.bind(binder(), AccountActivationValidationListener.class).to(Validator.class);
      DynamicSet.bind(binder(), AccountActivationListener.class).to(Listener.class);
    }
  }

  Validator validator;
  Listener listener;

  @Before
  public void setUp() {
    validator = plugin.getSysInjector().getInstance(Validator.class);

    listener = plugin.getSysInjector().getInstance(Listener.class);
  }

  @Test
  public void testActivation() throws RestApiException {
    int id = accountOperations.newAccount().inactive().create().get();

    gApi.accounts().id(id).setActive(true);

    validator.assertActivationValidation(id);
    listener.assertActivated(id);
    assertNoMoreEvents();
    assertThat(gApi.accounts().id(id).getActive()).isTrue();
  }

  @Test
  public void testActivationProhibited() throws RestApiException {
    int id = accountOperations.newAccount().inactive().create().get();

    validator.failActivationValidations();

    assertThrows(
        ResourceConflictException.class,
        () -> {
          gApi.accounts().id(id).setActive(true);
        });

    validator.assertActivationValidation(id);
    // No call to activation listener as validation failed
    assertNoMoreEvents();
    assertThat(gApi.accounts().id(id).getActive()).isFalse();
  }

  @Test
  public void testDeactivation() throws RestApiException {
    int id = accountOperations.newAccount().active().create().get();

    gApi.accounts().id(id).setActive(false);

    validator.assertDeactivationValidation(id);
    listener.assertDeactivated(id);
    assertNoMoreEvents();
    assertThat(gApi.accounts().id(id).getActive()).isFalse();
  }

  @Test
  public void testDeactivationProhibited() throws RestApiException {
    int id = accountOperations.newAccount().active().create().get();

    validator.failDeactivationValidations();

    assertThrows(
        ResourceConflictException.class,
        () -> {
          gApi.accounts().id(id).setActive(false);
        });

    validator.assertDeactivationValidation(id);
    // No call to activation listener as validation failed
    assertNoMoreEvents();
    assertThat(gApi.accounts().id(id).getActive()).isTrue();
  }

  private void assertNoMoreEvents() {
    validator.assertNoMoreEvents();
    listener.assertNoMoreEvents();
  }

  @Singleton
  public static class Validator implements AccountActivationValidationListener {
    private Integer lastIdActivationValidation;
    private Integer lastIdDeactivationValidation;
    private boolean failActivationValidations;
    private boolean failDeactivationValidations;

    @Override
    public void validateActivation(AccountState account) throws ValidationException {
      assertThat(lastIdActivationValidation).isNull();
      lastIdActivationValidation = account.account().id().get();
      if (failActivationValidations) {
        throw new ValidationException("testing validation failure");
      }
    }

    @Override
    public void validateDeactivation(AccountState account) throws ValidationException {
      assertThat(lastIdDeactivationValidation).isNull();
      lastIdDeactivationValidation = account.account().id().get();
      if (failDeactivationValidations) {
        throw new ValidationException("testing validation failure");
      }
    }

    public void failActivationValidations() {
      failActivationValidations = true;
    }

    public void failDeactivationValidations() {
      failDeactivationValidations = true;
    }

    private void assertNoMoreEvents() {
      assertThat(lastIdActivationValidation).isNull();
      assertThat(lastIdDeactivationValidation).isNull();
    }

    private void assertActivationValidation(int id) {
      assertThat(lastIdActivationValidation).isEqualTo(id);
      lastIdActivationValidation = null;
    }

    private void assertDeactivationValidation(int id) {
      assertThat(lastIdDeactivationValidation).isEqualTo(id);
      lastIdDeactivationValidation = null;
    }
  }

  @Singleton
  public static class Listener implements AccountActivationListener {
    private Integer lastIdActivated;
    private Integer lastIdDeactivated;

    @Override
    public void onAccountActivated(int id) {
      assertThat(lastIdActivated).isNull();
      lastIdActivated = id;
    }

    @Override
    public void onAccountDeactivated(int id) {
      assertThat(lastIdDeactivated).isNull();
      lastIdDeactivated = id;
    }

    private void assertNoMoreEvents() {
      assertThat(lastIdActivated).isNull();
      assertThat(lastIdDeactivated).isNull();
    }

    private void assertDeactivated(int id) {
      assertThat(lastIdDeactivated).isEqualTo(id);
      lastIdDeactivated = null;
    }

    private void assertActivated(int id) {
      assertThat(lastIdActivated).isEqualTo(id);
      lastIdActivated = null;
    }
  }
}
