/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../test/common-test-setup';
import './gr-checks-attempt';
import {GrChecksAttempt} from './gr-checks-attempt';
import {CheckRun} from '../../models/checks/checks-model';

suite('gr-checks-attempt', () => {
  let element: GrChecksAttempt;

  setup(async () => {
    element = await fixture<GrChecksAttempt>(
      html`<gr-checks-attempt></gr-checks-attempt>`
    );
  });

  test('render nothing if run is undefined', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });

  test('render nothing if isSingleAttempt', async () => {
    element.run = {
      isSingleAttempt: true,
      attempt: 1,
    } as CheckRun;
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });

  test('render nothing if attempt is missing', async () => {
    element.run = {
      isSingleAttempt: false,
    } as CheckRun;
    await element.updateComplete;
    assert.shadowDom.equal(element, '');
  });

  test('renders attempt number if not single attempt', async () => {
    element.run = {
      isSingleAttempt: false,
      attempt: 2,
    } as CheckRun;
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <span class="attempt">
          <div class="box">2nd</div>
          <div class="angle">2nd</div>
        </span>
      `
    );
  });
});
