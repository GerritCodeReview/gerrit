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

  test('renders warning message in tooltip', async () => {
    element.state = FlowStageState.PENDING;
    element.condition = 'label:Code-Review=+1';
    element.message = 'Something is odd';
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="stage">
          <gr-tooltip-content title="Something is odd" has-tooltip="">
            <gr-icon
              aria-label="pending"
              class="pending"
              icon="timelapse"
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
