/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-file-list-filter';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrFileListFilter} from './gr-file-list-filter';
import {visualDiffDarkTheme, waitUntil} from '../../../test/test-utils';
import {queryAndAssert} from '../../../utils/common-util';
import {MdMenu} from '@material/web/menu/menu';

suite('gr-file-list-filter screenshot tests', () => {
  let element: GrFileListFilter;

  setup(async () => {
    element = await fixture<GrFileListFilter>(
      html`<gr-file-list-filter
        .allFileExtensions=${['.ts', '.java', '.json', '']}
        .fileExtensionCounts=${{'.ts': 12, '.java': 5, '.json': 2, '': 1}}
        .hiddenFileExtensions=${['.json']}
      ></gr-file-list-filter>`
    );
    await element.updateComplete;
  });

  test('button active screenshot', async () => {
    await visualDiff(element, 'gr-file-list-filter-active');
    await visualDiffDarkTheme(element, 'gr-file-list-filter-active');
  });

  test('dropdown open screenshot', async () => {
    const button = queryAndAssert<HTMLElement>(element, '#filterBtn');
    const mdMenu = queryAndAssert<MdMenu>(element, '#filterMenu');
    mdMenu.anchorElement = button;
    element.openDropdown(button);
    await waitUntil(() => element.isDropdownOpen);
    await element.updateComplete;

    const panel = queryAndAssert<HTMLElement>(element, '.dropdown-panel');
    await visualDiff(panel, 'gr-file-list-filter-dropdown');
    await visualDiffDarkTheme(panel, 'gr-file-list-filter-dropdown');
  });
});
