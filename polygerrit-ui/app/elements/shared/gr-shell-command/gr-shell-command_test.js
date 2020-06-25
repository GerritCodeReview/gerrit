
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


<meta charset="utf-8">







<test-fixture id="basic">
  <template>
    <gr-shell-command></gr-shell-command>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import './gr-shell-command.js';
suite('gr-shell-command tests', () => {
  let element;
  let sandbox;

  setup(() => {
    sandbox = sinon.sandbox.create();
    element = fixture('basic');
    element.text = `git fetch http://gerrit@localhost:8080/a/test-project
        refs/changes/05/5/1 && git checkout FETCH_HEAD`;
    flushAsynchronousOperations();
  });

  teardown(() => {
    sandbox.restore();
  });

  test('focusOnCopy', () => {
    const focusStub = sandbox.stub(element.shadowRoot
        .querySelector('gr-copy-clipboard'),
    'focusOnCopy');
    element.focusOnCopy();
    assert.isTrue(focusStub.called);
  });
});

