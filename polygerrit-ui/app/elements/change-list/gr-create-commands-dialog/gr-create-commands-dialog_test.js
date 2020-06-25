
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
    <gr-create-commands-dialog></gr-create-commands-dialog>
  </template>
</test-fixture>


import '../../../test/common-test-setup.js';
import './gr-create-commands-dialog.js';
suite('gr-create-commands-dialog tests', () => {
  let element;

  setup(() => {
    element = fixture('basic');
  });

  test('_computePushCommand', () => {
    element.branch = 'master';
    assert.equal(element._pushCommand,
        'git push origin HEAD:refs/for/master');

    element.branch = 'stable-2.15';
    assert.equal(element._pushCommand,
        'git push origin HEAD:refs/for/stable-2.15');
  });
});

