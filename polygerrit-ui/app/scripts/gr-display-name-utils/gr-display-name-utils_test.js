/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import '../../test/common-test-setup-karma.js';
import {GrDisplayNameUtils} from './gr-display-name-utils.js';

suite('gr-display-name-utils tests', () => {
  // eslint-disable-next-line no-unused-vars
  const config = {
    user: {
      anonymous_coward_name: 'Anonymous Coward',
    },
  };

  test('getDisplayName name only', () => {
    const account = {
      name: 'test-name',
    };
    assert.equal(GrDisplayNameUtils.getDisplayName(config, account),
        'test-name');
  });

  test('getDisplayName prefer displayName', () => {
    const account = {
      name: 'test-name',
      display_name: 'better-name',
    };
    assert.equal(GrDisplayNameUtils.getDisplayName(config, account),
        'better-name');
  });

  test('getDisplayName prefer username default', () => {
    const account = {
      name: 'test-name',
      username: 'user-name',
    };
    const config = {
      accounts: {
        default_display_name: 'USERNAME',
      },
    };
    assert.equal(GrDisplayNameUtils.getDisplayName(config, account),
        'user-name');
  });

  test('getDisplayName prefer first name default', () => {
    const account = {
      name: 'firstname lastname',
    };
    const config = {
      accounts: {
        default_display_name: 'FIRST_NAME',
      },
    };
    assert.equal(GrDisplayNameUtils.getDisplayName(config, account),
        'firstname');
  });

  test('getDisplayName ignore leading whitespace for first name', () => {
    const account = {
      name: '   firstname lastname',
    };
    const config = {
      accounts: {
        default_display_name: 'FIRST_NAME',
      },
    };
    assert.equal(GrDisplayNameUtils.getDisplayName(config, account),
        'firstname');
  });

  test('getDisplayName full name default', () => {
    const account = {
      name: 'firstname lastname',
    };
    const config = {
      accounts: {
        default_display_name: 'FULL_NAME',
      },
    };
    assert.equal(GrDisplayNameUtils.getDisplayName(config, account),
        'firstname lastname');
  });

  test('getDisplayName name only', () => {
    const account = {
      name: 'test-name',
    };
    assert.deepEqual(GrDisplayNameUtils.getUserName(config, account),
        'test-name');
  });

  test('getUserName username only', () => {
    const account = {
      username: 'test-user',
    };
    assert.deepEqual(GrDisplayNameUtils.getUserName(config, account),
        'test-user');
  });

  test('getUserName email only', () => {
    const account = {
      email: 'test-user@test-url.com',
    };
    assert.deepEqual(GrDisplayNameUtils.getUserName(config, account),
        'test-user@test-url.com');
  });

  test('getUserName returns not Anonymous Coward as the anon name', () => {
    assert.deepEqual(GrDisplayNameUtils.getUserName(config, null),
        'Anonymous');
  });

  test('getUserName for the config returning the anon name', () => {
    const config = {
      user: {
        anonymous_coward_name: 'Test Anon',
      },
    };
    assert.deepEqual(GrDisplayNameUtils.getUserName(config, null),
        'Test Anon');
  });

  test('getAccountDisplayName - account with name only', () => {
    assert.equal(
        GrDisplayNameUtils.getAccountDisplayName(config,
            {name: 'Some user name'}),
        'Some user name');
  });

  test('getAccountDisplayName - account with email only', () => {
    assert.equal(
        GrDisplayNameUtils.getAccountDisplayName(config,
            {email: 'my@example.com'}),
        'my@example.com <my@example.com>');
  });

  test('getAccountDisplayName - account with name and status', () => {
    assert.equal(
        GrDisplayNameUtils.getAccountDisplayName(config, {
          name: 'Some name',
          status: 'OOO',
        }),
        'Some name (OOO)');
  });

  test('getAccountDisplayName - account with name and email', () => {
    assert.equal(
        GrDisplayNameUtils.getAccountDisplayName(config, {
          name: 'Some name',
          email: 'my@example.com',
        }),
        'Some name <my@example.com>');
  });

  test('getAccountDisplayName - account with name, email and status', () => {
    assert.equal(
        GrDisplayNameUtils.getAccountDisplayName(config, {
          name: 'Some name',
          email: 'my@example.com',
          status: 'OOO',
        }),
        'Some name <my@example.com> (OOO)');
  });

  test('getGroupDisplayName', () => {
    assert.equal(
        GrDisplayNameUtils.getGroupDisplayName({name: 'Some user name'}),
        'Some user name (group)');
  });

  test('_accountEmail', () => {
    assert.equal(
        GrDisplayNameUtils._accountEmail('email@gerritreview.com'),
        '<email@gerritreview.com>');
    assert.equal(GrDisplayNameUtils._accountEmail(undefined), '');
  });
});

