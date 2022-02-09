/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma';
import './gr-labeled-autocomplete';
import {GrLabeledAutocomplete} from './gr-labeled-autocomplete';
import {assertIsDefined} from '../../../utils/common-util';

const basicFixture = fixtureFromElement('gr-labeled-autocomplete');

suite('gr-labeled-autocomplete tests', () => {
  let element: GrLabeledAutocomplete;

  setup(async () => {
    element = basicFixture.instantiate();
    await element.updateComplete;
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

    expect(element).shadowDom.to.equal(/* HTML */ `
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
    `);
  });
});
