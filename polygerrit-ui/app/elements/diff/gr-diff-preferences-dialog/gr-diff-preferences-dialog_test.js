/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-diff-preferences-dialog.js';

const basicFixture = fixtureFromElement('gr-diff-preferences-dialog');

suite('gr-diff-preferences-dialog', () => {
  let element;
  setup(() => {
    element = basicFixture.instantiate();
  });
  test('changes applies only on save', async () => {
    const originalDiffPrefs = {
      line_wrapping: true,
    };
    element.diffPrefs = originalDiffPrefs;

    element.open();
    await flush();
    assert.isTrue(element.$.diffPreferences.$.lineWrappingInput.checked);

    MockInteractions.tap(element.$.diffPreferences.$.lineWrappingInput);
    await flush();
    assert.isFalse(element.$.diffPreferences.$.lineWrappingInput.checked);
    assert.isTrue(element._diffPrefsChanged);
    assert.isTrue(element.diffPrefs.line_wrapping);
    assert.isTrue(originalDiffPrefs.line_wrapping);

    MockInteractions.tap(element.$.saveButton);
    await flush();
    // Original prefs must remains unchanged, dialog must expose a new object
    assert.isTrue(originalDiffPrefs.line_wrapping);
    assert.isFalse(element.diffPrefs.line_wrapping);
  });
});
