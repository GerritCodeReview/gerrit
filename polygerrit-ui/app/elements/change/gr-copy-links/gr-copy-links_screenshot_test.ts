/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-copy-links';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrCopyLinks} from './gr-copy-links';
import {waitUntil} from '../../../test/test-utils';

suite('gr-copy-links screenshot tests', () => {
  let element: GrCopyLinks;

  setup(async () => {
    const links = [
      {
        label: 'Change ID',
        shortcut: 'd',
        value: '123456',
      },
      {
        label: 'Description',
        shortcut: 'm',
        value:
          'This is a test commit message\nWith multiple lines\nAnd more details',
        multiline: true,
      },
    ];
    element = await fixture<GrCopyLinks>(
      html`<gr-copy-links .copyLinks=${links}></gr-copy-links>`
    );
    await element.updateComplete;
    // md-menu requires anchor to be set
    // so we create a dummy element to allow
    // us to open it.
    const button = document.createElement('button');
    const mdMenu = element.shadowRoot?.querySelector('md-menu');
    mdMenu!.anchorElement = button;
    element.openDropdown();
    await waitUntil(() => element.isDropdownOpen);
    await element.updateComplete;
  });

  test('dropdown screenshot', async () => {
    await visualDiff(
      element.shadowRoot?.querySelector('.dropdown-content'),
      'gr-copy-links'
    );
  });
});
