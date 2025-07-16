/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GrEditFileControls} from './gr-edit-file-controls';
import './gr-edit-file-controls';
import {GrDropdown} from '../../shared/gr-dropdown/gr-dropdown';
import {queryAndAssert} from '../../../test/test-utils';

suite('gr-edit-file-controls screenshot tests', () => {
  let element: GrEditFileControls;

  setup(async () => {
    element = await fixture(
      html`<gr-edit-file-controls></gr-edit-file-controls>`
    );
    element.filePath = 'foo/bar.baz';
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-edit-file-controls');
    await visualDiffDarkTheme(element, 'gr-edit-file-controls');
  });

  test('dropdown open', async () => {
    const actions = queryAndAssert<GrDropdown>(element, '#actions');
    actions.dropdownTriggerTapHandler();
    await actions.updateComplete;

    const dropdownContent =
      actions.shadowRoot?.querySelector('.dropdown-content');
    assert.isOk(dropdownContent);

    await visualDiff(dropdownContent, 'gr-edit-file-controls-dropdown');
    await visualDiffDarkTheme(
      dropdownContent,
      'gr-edit-file-controls-dropdown'
    );
  });
});
