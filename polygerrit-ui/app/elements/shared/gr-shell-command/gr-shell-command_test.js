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

import '../../../test/common-test-setup-karma.js';
import './gr-shell-command.js';

const basicFixture = fixtureFromElement('gr-shell-command');

suite('gr-shell-command tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
    element.text = `git fetch http://gerrit@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`;
    flush();
  });

  test('focusOnCopy', () => {
    const focusStub = sinon.stub(element.shadowRoot
        .querySelector('gr-copy-clipboard'),
    'focusOnCopy');
    element.focusOnCopy();
    assert.isTrue(focusStub.called);
  });
});

