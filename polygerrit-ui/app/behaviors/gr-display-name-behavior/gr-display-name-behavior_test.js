
/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
    <test-element-anon></test-element-anon>
  </template>
</test-fixture>


import '../../test/common-test-setup.js';
import {Polymer} from '@polymer/polymer/lib/legacy/polymer-fn.js';
import {DisplayNameBehavior} from './gr-display-name-behavior.js';
suite('gr-display-name-behavior tests', () => {
  let element;
  // eslint-disable-next-line no-unused-vars
  const config = {
    user: {
      anonymous_coward_name: 'Anonymous Coward',
    },
  };

  suiteSetup(() => {
    // Define a Polymer element that uses this behavior.
    Polymer({
      is: 'test-element-anon',
      behaviors: [
        DisplayNameBehavior,
      ],
    });
  });

  setup(() => {
    element = fixture('basic');
  });

  test('getUserName name only', () => {
    const account = {
      name: 'test-name',
    };
    assert.equal(element.getUserName(config, account), 'test-name');
  });

  test('getUserName username only', () => {
    const account = {
      username: 'test-user',
    };
    assert.equal(element.getUserName(config, account), 'test-user');
  });

  test('getUserName email only', () => {
    const account = {
      email: 'test-user@test-url.com',
    };
    assert.equal(element.getUserName(config, account),
        'test-user@test-url.com');
  });

  test('getUserName returns not Anonymous Coward as the anon name', () => {
    assert.equal(element.getUserName(config, null), 'Anonymous');
  });

  test('getUserName for the config returning the anon name', () => {
    const config = {
      user: {
        anonymous_coward_name: 'Test Anon',
      },
    };
    assert.equal(element.getUserName(config, null), 'Test Anon');
  });

  test('getGroupDisplayName', () => {
    assert.equal(element.getGroupDisplayName({name: 'Some user name'}),
        'Some user name (group)');
  });
});

