/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

const basicFixture = fixtureFromElement('gr-labeled-autocomplete');

suite('gr-labeled-autocomplete tests', () => {
  let element: GrLabeledAutocomplete;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('tapping trigger focuses autocomplete', () => {
    const e = {stopPropagation: () => undefined};
    const stopPropagationStub = sinon.stub(e, 'stopPropagation');
    const autocompleteStub = sinon.stub(element.$.autocomplete, 'focus');
    element._handleTriggerClick(e as Event);
    assert.isTrue(stopPropagationStub.calledOnce);
    assert.isTrue(autocompleteStub.calledOnce);
  });

  test('setText', () => {
    const setTextStub = sinon.stub(element.$.autocomplete, 'setText');
    element.setText('foo-bar');
    assert.isTrue(setTextStub.calledWith('foo-bar'));
  });
});
