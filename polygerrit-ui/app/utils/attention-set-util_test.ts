/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import {
  createAccountDetailWithIdNameAndEmail,
  createChange,
  createComment,
  createCommentThread,
  createServerInfo,
} from '../test/test-data-generators';
import {
  AccountId,
  AccountInfo,
  ChangeInfo,
  EmailAddress,
  ServerInfo,
} from '../types/common';
import {
  getMentionedReason,
  getReason,
  hasAttention,
} from './attention-set-util';
import {DefaultDisplayNameConfig} from '../api/rest-api';
import {AccountsVisibility} from '../constants/constants';
import {assert} from '@open-wc/testing';

const KERMIT: AccountInfo = {
  email: 'kermit@gmail.com' as EmailAddress,
  username: 'kermit',
  name: 'Kermit The Frog',
  _account_id: 31415926535 as AccountId,
};

const OTHER_ACCOUNT: AccountInfo = {
  email: 'other@gmail.com' as EmailAddress,
  username: 'other',
  name: 'Other User',
  _account_id: 31415926536 as AccountId,
};

const MENTION_ACCOUNT: AccountInfo = {
  email: 'mention@gmail.com' as EmailAddress,
  username: 'mention',
  name: 'Mention User',
  _account_id: 31415926537 as AccountId,
};

const MENTION_ACCOUNT_2: AccountInfo = {
  email: 'mention2@gmail.com' as EmailAddress,
  username: 'mention2',
  name: 'Mention2 User',
  _account_id: 31415926538 as AccountId,
};

const change: ChangeInfo = {
  ...createChange(),
  attention_set: {
    '31415926535': {
      account: KERMIT,
      reason: 'a good reason',
    },
    '31415926536': {
      account: OTHER_ACCOUNT,
      reason: 'Added by <GERRIT_ACCOUNT_31415926535>',
      reason_account: KERMIT,
    },
    '31415926537': {
      account: MENTION_ACCOUNT,
      reason: '<GERRIT_ACCOUNT_31415926535> replied on the change',
      reason_account: KERMIT,
    },
    '31415926538': {
      account: MENTION_ACCOUNT_2,
      reason: 'Bot voted negatively on the change',
      reason_account: KERMIT,
    },
  },
};

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

suite('attention-set-util', () => {
  test('hasAttention', () => {
    assert.isTrue(hasAttention(KERMIT, change));
  });

  test('getReason', () => {
    assert.equal(getReason(config, KERMIT, change), 'a good reason');
    assert.equal(getReason(config, OTHER_ACCOUNT, change), 'Added by kermit');
  });

  test('getMentionReason', () => {
    let comment = {
      ...createComment(),
      message: `hey @${MENTION_ACCOUNT.email} take a look at this`,
      unresolved: true,
      author: {
        ...createAccountDetailWithIdNameAndEmail(1),
      },
    };

    assert.equal(
      getMentionedReason(
        [createCommentThread([comment])],
        KERMIT,
        KERMIT,
        config
      ),
      '<GERRIT_ACCOUNT_31415926535> replied on the change'
    );

    assert.equal(
      getMentionedReason(
        [createCommentThread([comment])],
        KERMIT,
        MENTION_ACCOUNT,
        config
      ),
      '<GERRIT_ACCOUNT_31415926535> mentioned you in a comment'
    );

    // resolved mention hence does not change reason
    comment = {
      ...createComment(),
      message: `hey @${MENTION_ACCOUNT.email} take a look at this`,
      unresolved: false,
      author: {
        ...createAccountDetailWithIdNameAndEmail(1),
      },
    };
    assert.equal(
      getMentionedReason(
        [createCommentThread([comment])],
        KERMIT,
        MENTION_ACCOUNT,
        config
      ),
      '<GERRIT_ACCOUNT_31415926535> replied on the change'
    );
  });
});
