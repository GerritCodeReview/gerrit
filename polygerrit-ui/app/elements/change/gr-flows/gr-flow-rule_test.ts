/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flow-rule';
import {GrFlowRule} from './gr-flow-rule';
import {assert, fixture, html} from '@open-wc/testing';
import {FlowStageState} from '../../../api/rest-api';
import {stubRestApi} from '../../../test/test-utils';
import {createAccountDetailWithId} from '../../../test/test-data-generators';
import {EmailAddress, UserId} from '../../../types/common';

suite('gr-flow-rule tests', () => {
  let element: GrFlowRule;
  setup(async () => {
    element = await fixture<GrFlowRule>(
      html`<gr-flow-rule .condition=${'label:Code-Review=+1'}></gr-flow-rule>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <span class="condition"> label:Code-Review=+1 </span>
        </div>
      `
    );
  });

  test('renders with state', async () => {
    element.state = FlowStageState.DONE;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <gr-tooltip-content>
            <gr-icon
              aria-label="done"
              class="done"
              filled=""
              icon="check_circle"
              role="img"
            >
            </gr-icon>
          </gr-tooltip-content>
          <span class="condition"> label:Code-Review=+1 </span>
        </div>
      `
    );
  });

  test('renders with action', async () => {
    element.action = 'add_reviewer';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <span class="condition"> label:Code-Review=+1 </span>
          <gr-icon class="arrow" icon="arrow_forward"> </gr-icon>
          <div class="stage-action">
            <b> Add Reviewer </b>
          </div>
        </div>
      `
    );
  });

  test('renders with action and parameters', async () => {
    element.action = 'add_reviewer';
    element.parameters = ['user@example.com'];
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <span class="condition"> label:Code-Review=+1 </span>
          <gr-icon class="arrow" icon="arrow_forward"> </gr-icon>
          <div class="stage-action">
            <b> Add Reviewer </b>
            <span class="parameter">
              <code> user@example.com </code>
            </span>
          </div>
        </div>
      `
    );
  });

  test('renders email parameter as account chip when account exists', async () => {
    const account = {
      ...createAccountDetailWithId(1),
      email: 'user@example.com' as EmailAddress,
    };
    const getAccountDetailsStub = stubRestApi('getAccountDetails');
    getAccountDetailsStub
      .withArgs('user@example.com' as UserId)
      .resolves(account);
    getAccountDetailsStub
      .withArgs('not-found@example.com' as UserId)
      .resolves(undefined);

    element.action = 'add_reviewer';
    element.parameters = [
      'user@example.com',
      'not-an-email',
      'not-found@example.com',
    ];
    await element.updateComplete;

    // wait for async account fetching and re-rendering
    await new Promise(resolve => setTimeout(resolve, 0));
    await element.updateComplete;

    assert.isTrue(
      getAccountDetailsStub.calledWith('user@example.com' as UserId)
    );
    assert.isTrue(
      getAccountDetailsStub.calledWith('not-found@example.com' as UserId)
    );
    // getAccountDetails should not be called for 'not-an-email'
    assert.equal(getAccountDetailsStub.callCount, 2);

    const params = element.shadowRoot?.querySelectorAll(
      '.account-parameter, .parameter'
    );
    assert.isOk(params);
    assert.equal(params.length, 3);

    const accountParam = params[0];
    assert.isTrue(accountParam.classList.contains('account-parameter'));
    const avatar = accountParam.querySelector('gr-avatar');
    assert.isOk(avatar);
    assert.deepEqual(avatar.account, account);
    const label = accountParam.querySelector('gr-account-label');
    assert.isOk(label);
    assert.deepEqual(label.account, account);

    const notAnEmailParam = params[1];
    assert.isTrue(notAnEmailParam.classList.contains('parameter'));
    assert.equal(notAnEmailParam.textContent?.trim(), 'not-an-email');

    const notFoundParam = params[2];
    assert.isTrue(notFoundParam.classList.contains('parameter'));
    assert.equal(notFoundParam.textContent?.trim(), 'not-found@example.com');
  });

  test('renders error message directly', async () => {
    element.state = FlowStageState.FAILED;
    element.condition = 'label:Code-Review=+1';
    element.action = 'add_reviewer';
    element.message = 'An error occurred';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <gr-tooltip-content>
            <gr-icon
              aria-label="failed"
              class="failed"
              icon="error"
              role="img"
              filled=""
            >
            </gr-icon>
          </gr-tooltip-content>
          <span class="condition"> label:Code-Review=+1 </span>
          <gr-icon class="arrow" icon="arrow_forward"> </gr-icon>
          <div class="stage-action">
            <b> Add Reviewer </b>
          </div>
          <span class="error"> An error occurred </span>
        </div>
      `
    );
  });

  test('renders message in tooltip for successful state', async () => {
    element.state = FlowStageState.DONE;
    element.condition = 'label:Code-Review=+1';
    element.message = 'Conditions met';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <gr-tooltip-content title="Conditions met" has-tooltip="">
            <gr-icon
              aria-label="done"
              class="done"
              filled=""
              icon="check_circle"
              role="img"
            >
            </gr-icon>
          </gr-tooltip-content>
          <span class="condition"> label:Code-Review=+1 </span>
        </div>
      `
    );
  });

  test('renders error message for terminated state directly', async () => {
    element.state = FlowStageState.TERMINATED;
    element.condition = 'label:Code-Review=+1';
    element.action = 'add_reviewer';
    element.message = 'An error occurred';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <gr-tooltip-content>
            <gr-icon
              aria-label="terminated"
              class="failed"
              icon="error"
              role="img"
              filled=""
            >
            </gr-icon>
          </gr-tooltip-content>
          <span class="condition"> label:Code-Review=+1 </span>
          <gr-icon class="arrow" icon="arrow_forward"> </gr-icon>
          <div class="stage-action">
            <b> Add Reviewer </b>
          </div>
          <span class="error"> An error occurred </span>
        </div>
      `
    );
  });
});
