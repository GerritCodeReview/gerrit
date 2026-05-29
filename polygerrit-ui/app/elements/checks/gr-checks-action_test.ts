/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../test/common-test-setup';
import './gr-checks-action';
import {GrChecksAction} from './gr-checks-action';
import {Action} from '../../api/checks';

suite('gr-checks-action', () => {
  let element: GrChecksAction;

  setup(async () => {
    element = await fixture<GrChecksAction>(
      html`<gr-checks-action
        .action=${{name: 'test-action'} as Action}
      ></gr-checks-action>`
    );
  });

  test('render', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ ' <gr-button class="action" link=""> test-action </gr-button> '
    );
  });
});
