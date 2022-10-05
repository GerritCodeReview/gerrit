/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
import {GrAutocompleteDropdown} from '../shared/gr-autocomplete-dropdown/gr-autocomplete-dropdown';
import {waitEventLoop} from '../../test/test-utils';
import {fixture, html, assert} from '@open-wc/testing';

suite('fit controller', () => {
  let element: GrAutocompleteDropdown;

  setup(async () => {
    element = await fixture(
      html`<gr-autocomplete-dropdown></gr-autocomplete-dropdown>`
    );
    element.open();
    element.suggestions = [
      {dataValue: 'test value 1', name: 'test name 1', text: '1', label: 'hi'},
      {dataValue: 'test value 2', name: 'test name 2', text: '2'},
    ];
    await element.updateComplete;
    await waitEventLoop();
  });

  test('position target', () => {
    const positionTarget = element.fitController?.getPositionTarget();
    assert.dom.equal(
      positionTarget,
      /* HTML */ `
        <div>
          <gr-autocomplete-dropdown
            style="position: fixed; box-sizing: border-box; left: 0px; top: 0px; max-width: 42.9922px; max-height: 57px;"
          >
          </gr-autocomplete-dropdown>
        </div>
      `,
      {ignoreAttributes: ['style']}
    );
  });
});
