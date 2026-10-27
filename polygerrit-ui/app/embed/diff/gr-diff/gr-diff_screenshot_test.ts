/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff';
import {GrDiff} from './gr-diff';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-expect-error
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {DiffInfo, DiffViewMode} from '../../../api/diff';

suite('gr-diff screenshot tests', () => {
  let element: GrDiff;

  setup(async () => {
    // Lines 99998 to 100004 straddle the 5 to 6 digit boundary. Responsive
    // mode is used, because it gives the table a fixed layout, so the line
    // number columns do not grow with their content.
    const diff: DiffInfo = {
      meta_a: {
        name: 'large.txt',
        content_type: 'text/plain',
        lines: 100004,
      },
      meta_b: {
        name: 'large.txt',
        content_type: 'text/plain',
        lines: 100004,
      },
      change_type: 'MODIFIED',
      intraline_status: 'OK',
      content: [
        {skip: 99997},
        {ab: ['line 99998', 'line 99999', 'line 100000']},
        {a: ['line 100001 old'], b: ['line 100001 new']},
        {ab: ['line 100002', 'line 100003', 'line 100004']},
      ],
    };

    element = await fixture<GrDiff>(html`<gr-diff
      .diff=${diff}
      .prefs=${{
        ...createDefaultDiffPrefs(),
        line_length: 40,
        responsive_mode: 'FULL_RESPONSIVE',
      }}
    ></gr-diff>`);
    // The test viewport is narrow enough to trigger the compact spacing from
    // app-theme. Use the desktop spacing, which the line number padding uses.
    element.style.setProperty('--spacing-m', '8px');
    await element.updateComplete;
  });

  test('line numbers above 100000', async () => {
    await visualDiff(element, 'gr-diff-large-line-numbers');
    await visualDiffDarkTheme(element, 'gr-diff-large-line-numbers');
  });

  test('unified line numbers above 100000', async () => {
    element.viewMode = DiffViewMode.UNIFIED;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-large-line-numbers-unified');
    await visualDiffDarkTheme(element, 'gr-diff-large-line-numbers-unified');
  });
});
