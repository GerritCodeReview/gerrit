/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-labeled-autocomplete';
import {GrLabeledAutocomplete} from './gr-labeled-autocomplete';
import {assertIsDefined} from '../../../utils/common-util';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-labeled-autocomplete tests', () => {
  let element: GrLabeledAutocomplete;

  setup(async () => {
    element = await fixture(
      html`<gr-labeled-autocomplete></gr-labeled-autocomplete>`
    );
  });

  test('tapping trigger focuses autocomplete', () => {
    const e = {stopPropagation: () => undefined};
    const stopPropagationStub = sinon.stub(e, 'stopPropagation');
    assertIsDefined(element.autocomplete);
    const autocompleteStub = sinon.stub(element.autocomplete, 'focus');
    element._handleTriggerClick(e as Event);
    assert.isTrue(stopPropagationStub.calledOnce);
    assert.isTrue(autocompleteStub.calledOnce);
  });

  test('setText', () => {
    assertIsDefined(element.autocomplete);
    const setTextStub = sinon.stub(element.autocomplete, 'setText');
    element.setText('foo-bar');
    assert.isTrue(setTextStub.calledWith('foo-bar'));
  });

  test('shadowDom', async () => {
    element.label = 'Some label';
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div id="container">
          <div id="header">Some label</div>
          <div id="body">
            <gr-autocomplete
              id="autocomplete"
              threshold="0"
              borderless=""
            ></gr-autocomplete>
            <div id="trigger">▼</div>
          </div>
        </div>
      `
    );
  });
});
