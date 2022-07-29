/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Timestamp} from '../api/rest-api';
import '../test/common-test-setup-karma';
import {
  createAccountDetailWithId,
  createParsedChange,
} from '../test/test-data-generators';
import {ParsedChangeInfo} from '../types/types';
import {parseDate} from './date-util';
import {filterAttentionChangesAfter} from './service-worker-util';

suite('service worker util tests', () => {
  test('filterAttentionChangesAfter', () => {
    const account = createAccountDetailWithId();
    const changeBefore: ParsedChangeInfo = {
      ...createParsedChange(),
      attention_set: {
        [`${account._account_id}`]: {
          account,
          last_update: '2016-01-12 20:24:49.000000000' as Timestamp,
        },
      },
    };
    const changeAfter: ParsedChangeInfo = {
      ...createParsedChange(),
      attention_set: {
        [`${account._account_id}`]: {
          account,
          last_update: '2016-01-12 20:24:51.000000000' as Timestamp,
        },
      },
    };
    const changes = [changeBefore, changeAfter];

    const filteredChanges = filterAttentionChangesAfter(
      changes,
      account,
      parseDate('2016-01-12 20:24:50.000000000' as Timestamp).valueOf()
    );

    assert.equal(filteredChanges.length, 1);
    assert.equal(filteredChanges[0], changeAfter);
  });
});
