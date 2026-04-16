/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
/* eslint-disable lit/prefer-static-styles */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
import '../../../styles/themes/app-theme';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GrSelectionActionBox} from './gr-selection-action-box';
import './gr-selection-action-box';
import {grDiffElementStyles} from '../gr-diff/gr-diff-styles';

// Skipped because local generation differs from CI by ~3%, exceeding the 2% threshold.
suite.skip('gr-selection-action-box screenshot tests', () => {
  let container: HTMLDivElement;
  let element: GrSelectionActionBox;

  setup(async () => {
    container = await fixture<HTMLDivElement>(html`
      <div
        style="position: relative; height: 100px; width: 300px; background-color: var(--background-color-primary);"
      >
        <!-- eslint-disable-next-line lit/prefer-static-styles -->
        <style>
          ${grDiffElementStyles} gr-selection-action-box .menu {
            position: relative !important;
            z-index: auto !important;
            visibility: visible !important;
            width: 130px !important;
          }
        </style>
        <gr-diff-element>
          <gr-selection-action-box></gr-selection-action-box>
        </gr-diff-element>
      </div>
    `);
    element = container.querySelector('gr-selection-action-box')!;
    await element.updateComplete;
  });

  test('renders with one item', async () => {
    const menu = element.querySelector('.menu') as HTMLElement;
    assert.isOk(menu);
    await visualDiff(menu, 'gr-selection-action-box-one-item');
    await visualDiffDarkTheme(menu, 'gr-selection-action-box-one-item');
  });
});
