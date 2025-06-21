/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-account-chip';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAccountChip} from './gr-account-chip';
import {createAccountWithId} from '../../../test/test-data-generators';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-account-chip screenshot tests', () => {
  let element: GrAccountChip;

  setup(async () => {
    element = await fixture<GrAccountChip>(
      html`<gr-account-chip></gr-account-chip>`
    );
    element.account = createAccountWithId();
    element.showAttention = true;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-account-chip');
    await visualDiffDarkTheme(element, 'gr-account-chip');
  });
});
