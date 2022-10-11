/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
<<<<<<< PATCH SET (3dc534 Add tests for FitController)
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

  test('refit positioning', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const windowRect = {
      top: 0,
      left: 0,
      width: 600,
      height: 600,
      right: 600,
      bottom: 600,
    } as DOMRect;

    suggestionsEl().fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    // verticalOffset is 37 + 31 = 68, extra 31 due to GrAutocomplete
    assert.equal(parseInt(suggestionsEl().style.top), 68);
    assert.equal(parseInt(suggestionsEl().style.left), 37);
  });

  test('host margin updates positioning', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const windowRect = {
      top: 0,
      left: 0,
      width: 600,
      height: 600,
      right: 600,
      bottom: 600,
    } as DOMRect;

    suggestionsEl().style.marginLeft = '10px';
    suggestionsEl().style.marginTop = '10px';
    suggestionsEl().fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    // is 10px extra from the previous test due to host margin
    assert.equal(parseInt(suggestionsEl().style.top), 78);
    assert.equal(parseInt(suggestionsEl().style.left), 47);
  });

  test('host minWidth, minHeight overrides positioning', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const windowRect = {
      top: 0,
      left: 0,
      width: 600,
      height: 600,
      right: 600,
      bottom: 600,
    } as DOMRect;

    suggestionsEl().style.marginLeft = '10px';
    suggestionsEl().style.marginTop = '10px';

    suggestionsEl().style.minHeight = '50px';
    suggestionsEl().style.minWidth = '60px';

    suggestionsEl().fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(parseInt(suggestionsEl().style.top), 78);

    // Should be 47 like the previous test but that would make it overall
    // smaller in width than the minWidth defined
    assert.equal(parseInt(suggestionsEl().style.left), 37);
    assert.equal(parseInt(suggestionsEl().style.maxWidth), 60);
  });

  test('positioning happens within window size ', async () => {
    const hostRect = {
      top: 0,
      left: 0,
      width: 50,
      height: 50,
      right: 0,
      bottom: 0,
    } as DOMRect;

    const positionRect = {
      top: 37,
      left: 37,
      width: 300,
      height: 60,
      right: 0,
      bottom: 0,
    } as DOMRect;

    // window size is small hence limits the position
    const windowRect = {
      top: 0,
      left: 0,
      width: 40,
      height: 40,
      right: 40,
      bottom: 40,
    } as DOMRect;

    suggestionsEl().style.marginLeft = '10px';
    suggestionsEl().style.marginTop = '10px';

    suggestionsEl().fitController.calculatePositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(parseInt(suggestionsEl().style.top), 40);
    assert.equal(parseInt(suggestionsEl().style.left), 40);
  });

  test('refit respects vertical and horizontal offset', async () => {
    suggestionsEl().fitController.refit();

    // GrAutocomplete specifies a vertical offset of 31px
    assert.equal(parseInt(suggestionsEl().style.top), 31);
    assert.equal(parseInt(suggestionsEl().style.left), 0);

    element.verticalOffset = 44;
    await element.updateComplete;

    suggestionsEl().fitController.refit();
    assert.equal(parseInt(suggestionsEl().style.top), 44);
    assert.equal(parseInt(suggestionsEl().style.left), 0);

    suggestionsEl().horizontalOffset = 27;
    suggestionsEl().fitController.refit();
    assert.equal(parseInt(suggestionsEl().style.top), 44);
    assert.equal(parseInt(suggestionsEl().style.left), 27);
  });

  test('element is kept inside window', async () => {
    suggestionsEl().verticalAlign = 'top';
    suggestionsEl().style.width = '5000px';
    suggestionsEl().style.height = '5000px';
    suggestionsEl().style.maxWidth = '5000px';
    suggestionsEl().style.maxHeight = '5000px';
    window.innerWidth = 600;
    window.innerHeight = 600;

    suggestionsEl().fitController.refit();
    const rect = suggestionsEl().getBoundingClientRect();
    // refit() will ensure the dimensions are less than window size
    assert.isTrue(rect.width <= 600);
    assert.isTrue(rect.height <= 600);
    assert.isTrue(parseInt(suggestionsEl().style.maxWidth) <= 600);
    assert.isTrue(parseInt(suggestionsEl().style.maxHeight) <= 600);
=======
import {fixture, html, assert} from '@open-wc/testing';
import {FitController} from './fit-controller';
import {LitElement} from 'lit';
import {customElement} from 'lit/decorators.js';

@customElement('fit-element')
class FitElement extends LitElement {
  fitController = new FitController(this);

  horizontalOffset = 0;

  verticalOffset = 0;

  override render() {
    return html`<div></div>`;
  }
}

suite('fit controller', () => {
  let element: FitElement;
  setup(async () => {
    element = await fixture(html`<fit-element></fit-element>`);
  });

  test('refit positioning', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(element.style.top, '37px');
    assert.equal(element.style.left, '37px');
  });

  test('refit positioning with offset', async () => {
    const elementWithOffset: FitElement = await fixture(
      html`<fit-element></fit-element>`
    );
    elementWithOffset.verticalOffset = 10;
    elementWithOffset.horizontalOffset = 20;

    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    elementWithOffset.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(elementWithOffset.style.top, '47px');
    assert.equal(elementWithOffset.style.left, '57px');
  });

  test('host margin updates positioning', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';
    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    // is 10px extra from the previous test due to host margin
    assert.equal(element.style.top, '47px');
    assert.equal(element.style.left, '47px');
  });

  test('host minWidth, minHeight overrides positioning', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    const windowRect = new DOMRect(0, 0, 600, 600);

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';

    element.style.minHeight = '50px';
    element.style.minWidth = '60px';

    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(element.style.top, '47px');

    // Should be 47 like the previous test but that would make it overall
    // smaller in width than the minWidth defined
    assert.equal(element.style.left, '37px');
    assert.equal(element.style.maxWidth, '60px');
  });

  test('positioning happens within window size ', async () => {
    const hostRect = new DOMRect(0, 0, 50, 50);

    const positionRect = new DOMRect(37, 37, 300, 60);

    // window size is small hence limits the position
    const windowRect = new DOMRect(0, 0, 50, 50);

    element.style.marginLeft = '10px';
    element.style.marginTop = '10px';

    element.fitController.calculateAndSetPositions(
      hostRect,
      positionRect,
      windowRect
    );

    assert.equal(element.style.top, '47px');
    assert.equal(element.style.left, '47px');
    // With the window size being 50, the element is styled with width 3px
    // width = windowSize - leftPosition = 50 - 47 = 3px
    // Without the window width restriction, in previous test maxWidth is 60px
    assert.equal(element.style.maxWidth, '3px');
>>>>>>> BASE      (262536 Merge "Avoid re-saving an Unsaved comment")
  });
});
