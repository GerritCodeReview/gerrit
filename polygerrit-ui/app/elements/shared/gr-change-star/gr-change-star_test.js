/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-change-star.js';

const basicFixture = fixtureFromElement('gr-change-star');

suite('gr-change-star tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
    element.change = {
      _number: 2,
      starred: true,
    };
  });

  test('star visibility states', () => {
    element.set('change.starred', true);
    let icon = element.shadowRoot
        .querySelector('iron-icon');
    assert.isTrue(icon.classList.contains('active'));
    assert.equal(icon.icon, 'gr-icons:star');

    element.set('change.starred', false);
    icon = element.shadowRoot
        .querySelector('iron-icon');
    assert.isFalse(icon.classList.contains('active'));
    assert.equal(icon.icon, 'gr-icons:star-border');
  });

  test('starring', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    element.addEventListener('toggle-star', resolve);
    element.set('change.starred', false);
    MockInteractions.tap(element.shadowRoot
        .querySelector('button'));

    await promise;
    assert.equal(element.change.starred, true);
  });

  test('unstarring', async () => {
    let resolve;
    const promise = new Promise(r => resolve = r);
    element.addEventListener('toggle-star', resolve);
    element.set('change.starred', true);
    MockInteractions.tap(element.shadowRoot
        .querySelector('button'));

    await promise;
    assert.equal(element.change.starred, false);
  });
});

