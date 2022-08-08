/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup-karma';
import {
  computeVoteableText,
  extractMentionedUsers,
  getAccountTemplate,
  isServiceUser,
  removeServiceUsers,
  replaceTemplates,
} from './account-util';
import {
  AccountsVisibility,
  AccountTag,
  DefaultDisplayNameConfig,
} from '../constants/constants';
import {
  AccountId,
  AccountInfo,
  EmailAddress,
  ServerInfo,
} from '../api/rest-api';
import {
  createAccountDetailWithId,
  createChange,
  createDetailedLabelInfo,
  createServerInfo,
} from '../test/test-data-generators';

const EMPTY = {};
const ERNIE = {name: 'Ernie'};
const SERVY = {name: 'Servy', tags: [AccountTag.SERVICE_USER]};
const BOTTY = {name: 'Botty', tags: [AccountTag.SERVICE_USER]};

const config: ServerInfo = {
  ...createServerInfo(),
  user: {
    anonymous_coward_name: 'Unidentified User',
  },
  accounts: {
    visibility: AccountsVisibility.ALL,
    default_display_name: DefaultDisplayNameConfig.USERNAME,
  },
};
const accounts: AccountInfo[] = [
  {
    _account_id: 1 as AccountId,
    name: 'Test User #1',
    username: 'test-username-1',
  },
  {
    _account_id: 2 as AccountId,
    name: 'Test User #2',
  },
];

suite('account-util tests', () => {
  test('isServiceUser', () => {
    assert.isFalse(isServiceUser());
    assert.isFalse(isServiceUser(EMPTY));
    assert.isFalse(isServiceUser(ERNIE));
    assert.isTrue(isServiceUser(SERVY));
    assert.isTrue(isServiceUser(BOTTY));
  });

  test('extractMentionedUsers', () => {
    let text =
      'Hi @kamilm@google.com and @brohlfs@google.com can you take a look at this?';
    assert.deepEqual(extractMentionedUsers(text), [
      {email: 'kamilm@google.com' as EmailAddress},
      {email: 'brohlfs@google.com' as EmailAddress},
    ]);

    // with extra @
    text = '@@abc@google.com';
    assert.deepEqual(extractMentionedUsers(text), []);

    // with spaces in email
    text = '@a bc@google.com';
    assert.deepEqual(extractMentionedUsers(text), []);

    // with invalid email
    text = '@abcgoogle.com';
    assert.deepEqual(extractMentionedUsers(text), []);

    text = '@abc@googlecom';
    assert.deepEqual(extractMentionedUsers(text), [
      {email: 'abc@googlecom' as EmailAddress},
    ]);

    // with newline before email
    text = '\n\n\n random text  \n\n@abc@google.com';
    assert.deepEqual(extractMentionedUsers(text), [
      {email: 'abc@google.com' as EmailAddress},
    ]);

    text = '@abc@google.com please take a look at this';
    assert.deepEqual(extractMentionedUsers(text), [
      {email: 'abc@google.com' as EmailAddress},
    ]);

    text = '@a@google.com @b@google.com @c@google.com';
    assert.deepEqual(extractMentionedUsers(text), [
      {email: 'a@google.com' as EmailAddress},
      {email: 'b@google.com' as EmailAddress},
      {email: 'c@google.com' as EmailAddress},
    ]);
  });

  test('removeServiceUsers', () => {
    assert.sameMembers(removeServiceUsers([]), []);
    assert.sameMembers(removeServiceUsers([EMPTY, ERNIE]), [EMPTY, ERNIE]);
    assert.sameMembers(removeServiceUsers([SERVY, BOTTY]), []);
    assert.sameMembers(removeServiceUsers([EMPTY, SERVY, ERNIE, BOTTY]), [
      EMPTY,
      ERNIE,
    ]);
  });

  test('replaceTemplates with display config', () => {
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000001>',
        accounts,
        config
      ),
      'Text with action by test-username-1'
    );
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000002>',
        accounts,
        config
      ),
      'Text with action by Test User #2'
    );
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_3>',
        accounts,
        config
      ),
      'Text with action by Gerrit Account 3'
    );
    assert.equal(
      replaceTemplates(
        'Text with multiple accounts: <GERRIT_ACCOUNT_0000003>, <GERRIT_ACCOUNT_0000002>, <GERRIT_ACCOUNT_0000001>',
        accounts,
        config
      ),
      'Text with multiple accounts: Gerrit Account 3, Test User #2, test-username-1'
    );
  });

  test('replaceTemplates no display config', () => {
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000001>',
        accounts
      ),
      'Text with action by Test User #1'
    );
    assert.equal(
      replaceTemplates(
        'Text with action by <GERRIT_ACCOUNT_0000002>',
        accounts
      ),
      'Text with action by Test User #2'
    );

    assert.equal(
      replaceTemplates('Text with action by <GERRIT_ACCOUNT_3>', accounts),
      'Text with action by Gerrit Account 3'
    );

    assert.equal(
      replaceTemplates(
        'Text with multiple accounts: <GERRIT_ACCOUNT_0000003>, <GERRIT_ACCOUNT_0000002>, <GERRIT_ACCOUNT_0000001>',
        accounts
      ),
      'Text with multiple accounts: Gerrit Account 3, Test User #2, Test User #1'
    );
  });

  test('getTemplate', () => {
    assert.equal(getAccountTemplate(accounts[0], config), '<GERRIT_ACCOUNT_1>');
    assert.equal(getAccountTemplate({}, config), 'Unidentified User');
    assert.equal(getAccountTemplate(), 'Anonymous');
  });

  test('votable labels', async () => {
    const change = {
      ...createChange(),
      labels: {
        Foo: {
          ...createDetailedLabelInfo(),
          all: [
            {
              _account_id: 7 as AccountId,
              permitted_voting_range: {max: 2, min: 0},
            },
          ],
        },
        Bar: {
          ...createDetailedLabelInfo(),
          all: [
            {
              ...createAccountDetailWithId(1),
              permitted_voting_range: {max: 1, min: 0},
            },
            {
              _account_id: 7 as AccountId,
              permitted_voting_range: {max: 1, min: 0},
            },
          ],
        },
        FooBar: {
          ...createDetailedLabelInfo(),
          all: [{_account_id: 7 as AccountId, value: 0}],
        },
      },
      permitted_labels: {
        Foo: ['-1', ' 0', '+1', '+2'],
        FooBar: ['-1', ' 0'],
      },
    };

    assert.strictEqual(
      computeVoteableText(change, {...createAccountDetailWithId(1)}),
      'Bar: +1'
    );
    assert.strictEqual(
      computeVoteableText(change, {...createAccountDetailWithId(7)}),
      'Foo: +2, Bar: +1, FooBar: 0'
    );
    assert.strictEqual(
      computeVoteableText(change, {...createAccountDetailWithId(2)}),
      ''
    );
  });

  test('fails gracefully when all is not included', async () => {
    const change = {
      ...createChange(),
      labels: {Foo: {}},
      permitted_labels: {
        Foo: ['-1', ' 0', '+1', '+2'],
      },
    };
    assert.strictEqual(
      computeVoteableText(change, {...createAccountDetailWithId(1)}),
      ''
    );
  });
});
