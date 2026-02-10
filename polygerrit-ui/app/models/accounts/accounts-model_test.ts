/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
import {
  AccountDetailInfo,
  AccountId,
  EmailAddress,
  Timestamp,
} from '../../api/rest-api';
import {getAppContext} from '../../services/app-context';
import {stubRestApi} from '../../test/test-utils';
import {AccountsModel} from './accounts-model';
import {assert} from '@open-wc/testing';

const KERMIT: AccountDetailInfo = {
  _account_id: 1 as AccountId,
  name: 'Kermit',
  registered_on: '2015-03-12 18:32:08.000000000' as Timestamp,
};

suite('accounts-model tests', () => {
  let model: AccountsModel;

  setup(() => {
    model = new AccountsModel(getAppContext().restApiService);
  });

  teardown(() => {
    model.finalize();
  });

  test('basic lookup', async () => {
    const stub = stubRestApi('getAccountDetails').returns(
      Promise.resolve(KERMIT)
    );

    let filled = await model.fillDetails({_account_id: 1 as AccountId});
    assert.equal(filled.name, 'Kermit');
    assert.equal(filled, KERMIT);
    assert.equal(stub.callCount, 1);

    filled = await model.fillDetails({_account_id: 1 as AccountId});
    assert.equal(filled.name, 'Kermit');
    // Cache objects are cloned on lookup, so this is a different object.
    assert.notEqual(filled, KERMIT);
    // Did not have to call the REST API again.
    assert.equal(stub.callCount, 1);
  });

  test('invalid account makes only one request', () => {
    const response = {...new Response(), status: 404};
    const getAccountDetails = stubRestApi('getAccountDetails').callsFake(
      (_, errFn) => {
        if (errFn !== undefined) {
          errFn(response);
        }
        return Promise.resolve(undefined);
      }
    );

    model.fillDetails({email: 'Invalid_email@def.com' as EmailAddress});
    assert.equal(getAccountDetails.callCount, 1);

    model.fillDetails({email: 'Invalid_email@def.com' as EmailAddress});
    assert.equal(getAccountDetails.callCount, 1);
  });
});
