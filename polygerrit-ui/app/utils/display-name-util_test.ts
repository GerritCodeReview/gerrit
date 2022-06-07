/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountInfo,
  DefaultDisplayNameConfig,
  EmailAddress,
  GroupName,
  ServerInfo,
} from '../api/rest-api';
import '../test/common-test-setup-karma';
import {
  getDisplayName,
  getUserName,
  getGroupDisplayName,
  getAccountDisplayName,
  _testOnly_accountEmail,
} from './display-name-util';
import {
  createAccountsConfig,
  createGroupInfo,
  createServerInfo,
} from '../test/test-data-generators';

suite('display-name-utils tests', () => {
  const config: ServerInfo = {
    ...createServerInfo(),
    user: {
      anonymous_coward_name: 'Anonymous Coward',
    },
  };

  test('getDisplayName name only', () => {
    const account = {
      name: 'test-name',
    };
    assert.equal(getDisplayName(config, account), 'test-name');
  });

  test('getDisplayName prefer displayName', () => {
    const account = {
      name: 'test-name',
      display_name: 'better-name',
    };
    assert.equal(getDisplayName(config, account), 'better-name');
  });

  test('getDisplayName prefer username default', () => {
    const account = {
      name: 'test-name',
      username: 'user-name',
    };
    const config: ServerInfo = {
      ...createServerInfo(),
      accounts: {
        ...createAccountsConfig(),
        default_display_name: DefaultDisplayNameConfig.USERNAME,
      },
    };
    assert.equal(getDisplayName(config, account), 'user-name');
  });

  test('getDisplayName firstNameOnly', () => {
    const account = {
      name: 'firstname lastname',
    };
    assert.equal(getDisplayName(config, account, true), 'firstname');
  });

  test('getDisplayName prefer first name default', () => {
    const account = {
      name: 'firstname lastname',
    };
    const config: ServerInfo = {
      ...createServerInfo(),
      accounts: {
        ...createAccountsConfig(),
        default_display_name: DefaultDisplayNameConfig.FIRST_NAME,
      },
    };
    assert.equal(getDisplayName(config, account), 'firstname');
  });

  test('getDisplayName ignore leading whitespace for first name', () => {
    const account = {
      name: '   firstname lastname',
    };
    const config: ServerInfo = {
      ...createServerInfo(),
      accounts: {
        ...createAccountsConfig(),
        default_display_name: DefaultDisplayNameConfig.FIRST_NAME,
      },
    };
    assert.equal(getDisplayName(config, account), 'firstname');
  });

  test('getDisplayName full name default', () => {
    const account = {
      name: 'firstname lastname',
    };
    const config: ServerInfo = {
      ...createServerInfo(),
      accounts: {
        ...createAccountsConfig(),
        default_display_name: DefaultDisplayNameConfig.FULL_NAME,
      },
    };
    assert.equal(getDisplayName(config, account), 'firstname lastname');
  });

  test('getDisplayName name only', () => {
    const account = {
      name: 'test-name',
    };
    assert.deepEqual(getUserName(config, account), 'test-name');
  });

  test('getUserName username only', () => {
    const account = {
      username: 'test-user',
    };
    assert.deepEqual(getUserName(config, account), 'test-user');
  });

  test('getUserName email only', () => {
    const account: AccountInfo = {
      email: 'test-user@test-url.com' as EmailAddress,
    };
    assert.deepEqual(getUserName(config, account), 'test-user@test-url.com');
  });

  test('getUserName returns not Anonymous Coward as the anon name', () => {
    assert.deepEqual(getUserName(config, undefined), 'Anonymous');
  });

  test('getUserName for the config returning the anon name', () => {
    const config: ServerInfo = {
      ...createServerInfo(),
      user: {
        anonymous_coward_name: 'Test Anon',
      },
    };
    assert.deepEqual(getUserName(config, undefined), 'Test Anon');
  });

  test('getAccountDisplayName - account with name only', () => {
    assert.equal(
      getAccountDisplayName(config, {name: 'Some user name'}),
      'Some user name'
    );
  });

  test('getAccountDisplayName - account with email only', () => {
    assert.equal(
      getAccountDisplayName(config, {
        email: 'my@example.com' as EmailAddress,
      }),
      'my@example.com <my@example.com>'
    );
  });

  test('getAccountDisplayName - account with name and status', () => {
    assert.equal(
      getAccountDisplayName(config, {
        name: 'Some name',
        status: 'OOO',
      }),
      'Some name (OOO)'
    );
  });

  test('getAccountDisplayName - account with name and email', () => {
    assert.equal(
      getAccountDisplayName(config, {
        name: 'Some name',
        email: 'my@example.com' as EmailAddress,
      }),
      'Some name <my@example.com>'
    );
  });

  test('getAccountDisplayName - account with name, email and status', () => {
    assert.equal(
      getAccountDisplayName(config, {
        name: 'Some name',
        email: 'my@example.com' as EmailAddress,
        status: 'OOO',
      }),
      'Some name <my@example.com> (OOO)'
    );
  });

  test('getGroupDisplayName', () => {
    assert.equal(
      getGroupDisplayName({
        ...createGroupInfo(),
        name: 'Some user name' as GroupName,
      }),
      'Some user name (group)'
    );
  });

  test('_accountEmail', () => {
    assert.equal(
      _testOnly_accountEmail('email@gerritreview.com'),
      '<email@gerritreview.com>'
    );
    assert.equal(_testOnly_accountEmail(undefined), '');
  });
});
