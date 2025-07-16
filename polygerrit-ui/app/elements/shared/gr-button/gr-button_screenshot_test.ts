/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-button';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrButton} from './gr-button';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-button screenshot tests', () => {
  let element: GrButton;

  setup(async () => {
    element = await fixture<GrButton>(html`<gr-button>Click Me</gr-button>`);
  });

  test('default', async () => {
    await visualDiff(element, 'gr-button-default');
    await visualDiffDarkTheme(element, 'gr-button-default');
  });

  test('disabled', async () => {
    element.disabled = true;
    await element.updateComplete;
    await visualDiff(element, 'gr-button-disabled');
    await visualDiffDarkTheme(element, 'gr-button-disabled');
  });

  test('link', async () => {
    element.link = true;
    await element.updateComplete;
    await visualDiff(element, 'gr-button-link');
    await visualDiffDarkTheme(element, 'gr-button-link');
  });

  test('down-arrow', async () => {
    element.downArrow = true;
    await element.updateComplete;
    await visualDiff(element, 'gr-button-down-arrow');
    await visualDiffDarkTheme(element, 'gr-button-down-arrow');
  });
});
