/**
 * @license
 * Copyright 2024 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-account-dropdown';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAccountDropdown} from './gr-account-dropdown';
import {createAccountWithIdNameAndEmail} from '../../../test/test-data-generators';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-account-dropdown screenshot tests', () => {
  let element: GrAccountDropdown;

  setup(async () => {
    element = await fixture<GrAccountDropdown>(
      html`<gr-account-dropdown></gr-account-dropdown>`
    );
    element.account = createAccountWithIdNameAndEmail(1);
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-account-dropdown');
    await visualDiffDarkTheme(element, 'gr-account-dropdown');
  });
});
