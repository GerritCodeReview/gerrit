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

package com.google.gerrit.server.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.account.AccountsUpdate.AccountUpdater;
import com.google.gerrit.server.account.InternalAccountUpdate.Builder;
import com.google.gerrit.server.plugincontext.PluginContext.ExtensionImplConsumer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Provider;
import java.sql.Timestamp;
import java.util.Optional;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class SetInactiveFlagTest {
  SetInactiveFlag setInactiveFlag;
  Account.Id accountId = Account.id(4711);
  AccountsUpdate accountsUpdate;
  AccountsUpdate.AccountUpdater accountsUpdater;
  Account account;
  AccountState accountState;
  Provider<AccountsUpdate> accountsUpdateProvider;
  PluginSetContext<AccountActivationValidationListener> validatorsPSC;
  ValidationListenerStub validator;
  Builder update;

  @SuppressWarnings("unchecked")
  public SetInactiveFlag createSetInactiveFlag(boolean active) throws Exception {
    validator = new ValidationListenerStub();
    validatorsPSC = mock(PluginSetContext.class);
    doAnswer(
            new Answer<Void>() {
              @Override
              public Void answer(InvocationOnMock invocation) throws Exception {
                ExtensionImplConsumer<AccountActivationValidationListener> extensionImplConsumer =
                    invocation.getArgument(0);
                extensionImplConsumer.run(validator);
                return null;
              }
            })
        .when(validatorsPSC)
        .runEach(any(), any());

    accountsUpdate = mock(AccountsUpdate.class);
    accountsUpdateProvider = mock(Provider.class);
    when(accountsUpdateProvider.get()).thenReturn(accountsUpdate);

    account = Account.builder(accountId, new Timestamp(0)).setActive(active).build();

    accountState = mock(AccountState.class);
    when(accountState.account()).thenReturn(account);

    update = mock(Builder.class);

    AccountState updateResult = mock(AccountState.class);
    when(accountsUpdate.update(anyString(), eq(accountId), any(AccountUpdater.class)))
        .thenAnswer(
            new Answer<Optional<AccountState>>() {

              @Override
              public Optional<AccountState> answer(InvocationOnMock invocation) throws Throwable {
                AccountUpdater updater = invocation.getArgument(2);

                updater.update(accountState, update);

                return Optional.ofNullable(updateResult);
              }
            });

    return new SetInactiveFlag(validatorsPSC, accountsUpdateProvider);
  }

  @Test
  public void testActivateInactive() throws Exception {
    SetInactiveFlag setInactiveFlag = createSetInactiveFlag(false);
    Response<String> res = setInactiveFlag.activate(accountId);

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    verify(accountsUpdate).update(msgCaptor.capture(), eq(accountId), any(AccountUpdater.class));
    String msg = msgCaptor.getValue();
    assertThat(msg).ignoringCase().contains("activat");
    assertThat(msg).ignoringCase().doesNotContain("deactivat");
    verifyNoMoreInteractions(accountsUpdate);

    verify(accountState).account();
    verifyNoMoreInteractions(accountState);

    // Check that the code did not change account directly. Setting active has to go through the
    // update builder.
    assertThat(account.isActive()).isFalse();

    verify(update).setActive(true);
    verifyNoMoreInteractions(update); // No other updates than setting active.

    validator.verify(ValidationType.VALIDATE_ACTIVATION, accountState);
    assertThat(res.statusCode()).isEqualTo(201);
  }

  @Test
  public void testActivateInactiveFailingValidation() throws Exception {
    SetInactiveFlag setInactiveFlag = createSetInactiveFlag(false);

    ValidationException e = new ValidationException("fail");
    validator.setException(e);

    ResourceConflictException caught =
        assertThrows(
            ResourceConflictException.class,
            () -> {
              setInactiveFlag.activate(accountId);
            });
    assertThat(caught).hasCauseThat().isSameInstanceAs(e);

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    verify(accountsUpdate).update(msgCaptor.capture(), eq(accountId), any(AccountUpdater.class));
    String msg = msgCaptor.getValue();
    assertThat(msg).ignoringCase().contains("activat");
    assertThat(msg).ignoringCase().doesNotContain("deactivat");
    verifyNoMoreInteractions(accountsUpdate);

    verify(accountState).account();
    verifyNoMoreInteractions(accountState);

    // Check that the code did not change account directly. Setting active has to go through the
    // update builder.
    assertThat(account.isActive()).isFalse();

    verifyNoMoreInteractions(update); // No updates happened.

    validator.verify(ValidationType.VALIDATE_ACTIVATION, accountState);
  }

  @Test
  public void testActivateActive() throws Exception {
    SetInactiveFlag setInactiveFlag = createSetInactiveFlag(true);
    Response<String> res = setInactiveFlag.activate(accountId);

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    verify(accountsUpdate).update(msgCaptor.capture(), eq(accountId), any(AccountUpdater.class));
    String msg = msgCaptor.getValue();
    assertThat(msg).ignoringCase().contains("activat");
    assertThat(msg).ignoringCase().doesNotContain("deactivat");
    verifyNoMoreInteractions(accountsUpdate);

    verify(accountState).account();
    verifyNoMoreInteractions(accountState);

    assertThat(account.isActive()).isTrue();

    verifyNoMoreInteractions(update); // No update is made

    validator.verify(ValidationType.NONE);
    assertThat(res.statusCode()).isEqualTo(200);
  }

  @Test
  public void testDeactivateActive() throws Exception {
    SetInactiveFlag setInactiveFlag = createSetInactiveFlag(true);
    Response<?> res = setInactiveFlag.deactivate(accountId);

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    verify(accountsUpdate).update(msgCaptor.capture(), eq(accountId), any(AccountUpdater.class));
    String msg = msgCaptor.getValue();
    assertThat(msg).ignoringCase().contains("deactivat");
    verifyNoMoreInteractions(accountsUpdate);

    verify(accountState).account();
    verifyNoMoreInteractions(accountState);

    // Check that the code did not change account directly. Setting active has to go through the
    // update builder.
    assertThat(account.isActive()).isTrue();

    verify(update).setActive(false);
    verifyNoMoreInteractions(update); // No other updates than setting active.

    validator.verify(ValidationType.VALIDATE_DEACTIVATION, accountState);
    assertThat(res.statusCode()).isEqualTo(204);
  }

  @Test
  public void testDeactivateActiveFailingValidation() throws Exception {
    SetInactiveFlag setInactiveFlag = createSetInactiveFlag(true);

    ValidationException e = new ValidationException("fail");
    validator.setException(e);

    ResourceConflictException caught =
        assertThrows(
            ResourceConflictException.class,
            () -> {
              setInactiveFlag.deactivate(accountId);
            });
    assertThat(caught).hasCauseThat().isSameInstanceAs(e);

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    verify(accountsUpdate).update(msgCaptor.capture(), eq(accountId), any(AccountUpdater.class));
    String msg = msgCaptor.getValue();
    assertThat(msg).ignoringCase().contains("deactivat");
    verifyNoMoreInteractions(accountsUpdate);

    verify(accountState).account();
    verifyNoMoreInteractions(accountState);

    // Check that the code did not change account directly. Setting active has to go through the
    // update builder.
    assertThat(account.isActive()).isTrue();

    verifyNoMoreInteractions(update); // No updates happened.

    validator.verify(ValidationType.VALIDATE_DEACTIVATION, accountState);
  }

  @Test
  public void testDeactivateInactive() throws Exception {
    SetInactiveFlag setInactiveFlag = createSetInactiveFlag(false);

    ResourceConflictException e =
        assertThrows(
            ResourceConflictException.class,
            () -> {
              setInactiveFlag.deactivate(accountId);
            });
    assertThat(e).hasMessageThat().ignoringCase().contains("not active");

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    verify(accountsUpdate).update(msgCaptor.capture(), eq(accountId), any(AccountUpdater.class));
    String msg = msgCaptor.getValue();
    assertThat(msg).ignoringCase().contains("deactivat");
    verifyNoMoreInteractions(accountsUpdate);

    verify(accountState).account();
    verifyNoMoreInteractions(accountState);

    assertThat(account.isActive()).isFalse();

    verifyNoMoreInteractions(update); // No update is made

    validator.verify(ValidationType.NONE);
  }

  private static enum ValidationType {
    NONE,
    VALIDATE_ACTIVATION,
    VALIDATE_DEACTIVATION,
  }

  private static class ValidationListenerStub implements AccountActivationValidationListener {
    private ValidationType lastType = ValidationType.NONE;
    private AccountState lastAccountState = null;
    private ValidationException e = null;

    private void assertNoActionSeen() {
      if (lastType != ValidationType.NONE) {
        throw new AssertionError("ActionTracker has already seen an action: " + lastType);
      }
    }

    public void setException(ValidationException e) {
      this.e = e;
    }

    private void throwExceptionIfSupplied() throws ValidationException {
      if (e != null) {
        throw e;
      }
    }

    @Override
    public void validateActivation(AccountState accountState) throws ValidationException {
      assertNoActionSeen();
      lastType = ValidationType.VALIDATE_ACTIVATION;
      lastAccountState = accountState;
      throwExceptionIfSupplied();
    }

    @Override
    public void validateDeactivation(AccountState accountState) throws ValidationException {
      assertNoActionSeen();
      lastType = ValidationType.VALIDATE_DEACTIVATION;
      lastAccountState = accountState;
      throwExceptionIfSupplied();
    }

    public void verify(ValidationType expectedType) {
      verify(expectedType, null);
    }

    public void verify(ValidationType expectedType, AccountState expectedAccountState) {
      assertThat(lastType).isEqualTo(expectedType);
      assertThat(lastAccountState).isEqualTo(expectedAccountState);
    }
  }
}
