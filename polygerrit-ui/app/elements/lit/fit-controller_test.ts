/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
import '../shared/gr-autocomplete/gr-autocomplete';
import {pressKey, queryAndAssert, waitEventLoop} from '../../test/test-utils';
import {fixture, html, assert, waitUntil} from '@open-wc/testing';
import {
  AutocompleteSuggestion,
  GrAutocomplete,
} from '../shared/gr-autocomplete/gr-autocomplete';
import {Key} from '../../utils/dom-util';
import {GrAutocompleteDropdown} from '../shared/gr-autocomplete-dropdown/gr-autocomplete-dropdown';

suite('fit controller', () => {
  let element: GrAutocomplete;

  const focusOnInput = () => {
    pressKey(inputEl(), Key.ENTER);
  };

  const suggestionsEl = () =>
    queryAndAssert<GrAutocompleteDropdown>(element, '#suggestions');

  const inputEl = () => queryAndAssert<HTMLInputElement>(element, '#input');

  setup(async () => {
    element = await fixture(html`<gr-autocomplete></gr-autocomplete>`);
    const queryStub = sinon.spy((input: string) =>
      Promise.resolve([
        {name: input + ' 0', value: '0'},
        {name: input + ' 1', value: '1'},
        {name: input + ' 2', value: '2'},
        {name: input + ' 3', value: '3'},
        {name: input + ' 4', value: '4'},
      ] as AutocompleteSuggestion[])
    );
    element.query = queryStub;
    focusOnInput();
    element.text = 'blah';
    await waitUntil(() => queryStub.called);
    await element.updateComplete;

    await waitEventLoop();
  });

  test('position target', () => {
    const positionTarget = suggestionsEl().fitController?.getPositionTarget();
    assert.dom.equal(
      positionTarget,
      /* HTML */ `
        <gr-autocomplete
          style="position: fixed; box-sizing: border-box; left: 0px; top: 0px; max-width: 42.9922px; max-height: 57px;"
        >
        </gr-autocomplete>
      `,
      {ignoreAttributes: ['style']}
    );
  });

  test('element is kept inside window', () => {
    suggestionsEl().verticalAlign = 'top';
    // Make it go out of screen
    suggestionsEl().style.width = '5000px';
    suggestionsEl().style.height = '5000px';
    suggestionsEl().fitController?.refit();
    const rect = suggestionsEl().getBoundingClientRect();
    // refit() will ensure the width is less than window size which is much
    // smaller than 5000px
    assert.isTrue(rect.width < 2000);
    assert.isTrue(rect.height < 2000);
  });
});
