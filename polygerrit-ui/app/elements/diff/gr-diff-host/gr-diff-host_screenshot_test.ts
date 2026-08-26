/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-host';
import {GrDiffHost} from './gr-diff-host';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {stubRestApi, visualDiffDarkTheme} from '../../../test/test-utils';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  createChange,
  createPatchRange,
} from '../../../test/test-data-generators';
import {EDIT, NumericChangeId} from '../../../types/common';
import {DiffInfo, DiffViewMode} from '../../../api/diff';

suite('gr-diff-host screenshot tests', () => {
  let element: GrDiffHost;

  setup(async () => {
    const diff: DiffInfo = {
      meta_a: {
        name: 'sample.ts',
        content_type: 'application/typescript',
        lines: 10,
      },
      meta_b: {
        name: 'sample.ts',
        content_type: 'application/typescript',
        lines: 10,
      },
      change_type: 'MODIFIED',
      intraline_status: 'OK',
      content: [
        {
          ab: [
            '// Copyright 2026 Google LLC',
            'import {LitElement, html} from "lit";',
            '',
          ],
        },
        {
          a: [
            'export function calculateSum(a: number, b: number): number {',
            '  return a + b;',
            '}',
          ],
          b: [
            'export function calculateSum(x: number, y: number): number {',
            '  // Updated implementation',
            '  return x + y;',
            '}',
          ],
        },
        {
          ab: [
            '',
            'export function helper(): void {',
            '  console.log("ready");',
          ],
        },
        {
          a: ['  console.log("old debug line");'],
        },
        {
          ab: ['}'],
        },
        {
          b: ['', '// Added at end of file', 'export const VERSION = 2;'],
        },
      ],
    };

    stubRestApi('getDiff').resolves(diff);
    element = await fixture<GrDiffHost>(html`<gr-diff-host
      .changeNum=${42 as NumericChangeId}
      .path=${'sample.ts'}
      .change=${createChange()}
      .patchRange=${{
        ...createPatchRange(),
        patchNum: EDIT,
      }}
      .prefs=${createDefaultDiffPrefs()}
    ></gr-diff-host>`);
    await element.reload(true);
    await element.updateComplete;
  });

  test('edit mode diff with revert buttons', async () => {
    await visualDiff(element, 'gr-diff-host-edit-mode-revert');
    await visualDiffDarkTheme(element, 'gr-diff-host-edit-mode-revert');
  });

  test('unified edit mode diff with revert buttons', async () => {
    element.viewMode = DiffViewMode.UNIFIED;
    await element.updateComplete;

    await visualDiff(element, 'gr-diff-host-edit-mode-revert-unified');
    await visualDiffDarkTheme(element, 'gr-diff-host-edit-mode-revert-unified');
  });
});
