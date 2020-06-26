/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import './gr-account-label.js';

const basicFixture = fixtureFromElement('gr-account-label');

suite('gr-account-label tests', () => {
  let element;

  setup(() => {
    stub('gr-rest-api-interface', {
      getConfig() { return Promise.resolve({}); },
      getLoggedIn() { return Promise.resolve(false); },
    });
    element = basicFixture.instantiate();
    element._config = {
      user: {
        anonymous_coward_name: 'Anonymous Coward',
      },
    };
  });

  test('null guard', () => {
    assert.doesNotThrow(() => {
      element.account = null;
    });
  });

  suite('_computeName', () => {
    test('not showing anonymous', () => {
      const account = {name: 'Wyatt'};
      assert.deepEqual(element._computeName(account, null), 'Wyatt');
    });

    test('showing anonymous but no config', () => {
      const account = {};
      assert.deepEqual(element._computeName(account, null),
          'Anonymous');
    });

    test('test for Anonymous Coward user and replace with Anonymous', () => {
      const config = {
        user: {
          anonymous_coward_name: 'Anonymous Coward',
        },
      };
      const account = {};
      assert.deepEqual(element._computeName(account, config),
          'Anonymous');
    });

    test('test for anonymous_coward_name', () => {
      const config = {
        user: {
          anonymous_coward_name: 'TestAnon',
        },
      };
      const account = {};
      assert.deepEqual(element._computeName(account, config),
          'TestAnon');
    });
  });
});

