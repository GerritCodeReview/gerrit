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
import './gr-diff-mode-selector';
import {GrDiffModeSelector} from './gr-diff-mode-selector';
import {DiffViewMode} from '../../../constants/constants';
import {stubUsers} from '../../../test/test-utils';
import {_testOnly_setState} from '../../../services/browser/browser-model';

const basicFixture = fixtureFromElement('gr-diff-mode-selector');

suite('gr-diff-mode-selector tests', () => {
  let element: GrDiffModeSelector;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('_computeSelectedClass', () => {
    assert.equal(
      element._computeSideBySideSelected(DiffViewMode.SIDE_BY_SIDE),
      'selected'
    );
    assert.equal(element._computeSideBySideSelected(DiffViewMode.UNIFIED), '');
    assert.equal(
      element._computeUnifiedSelected(DiffViewMode.UNIFIED),
      'selected'
    );
    assert.equal(
      element._computeUnifiedSelected(DiffViewMode.SIDE_BY_SIDE),
      ''
    );
  });

  test('setMode', () => {
    _testOnly_setState({screenWidth: 0});
    const saveStub = stubUsers('updatePreferences');

    flush();
    // Setting the mode initially does not save prefs.
    element.saveOnChange = true;
    element.setMode(DiffViewMode.SIDE_BY_SIDE);
    assert.isFalse(saveStub.called);

    // Setting the mode to itself does not save prefs.
    element.setMode(DiffViewMode.SIDE_BY_SIDE);
    assert.isFalse(saveStub.called);

    // Setting the mode to something else does not save prefs if saveOnChange
    // is false.
    element.saveOnChange = false;
    element.setMode(DiffViewMode.UNIFIED);
    assert.isFalse(saveStub.called);

    // Setting the mode to something else does not save prefs if saveOnChange
    // is false.
    element.saveOnChange = true;
    element.setMode(DiffViewMode.SIDE_BY_SIDE);
    assert.isTrue(saveStub.calledOnce);
  });
});
