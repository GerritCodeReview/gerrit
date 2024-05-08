/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../test/common-test-setup';
import {EmailAddress} from '../../api/rest-api';
import {getAppContext} from '../../services/app-context';
import {stubRestApi} from '../../test/test-utils';
import {AccountsModel} from './accounts-model';
import {assert} from '@open-wc/testing';

suite('accounts-model tests', () => {
  let model: AccountsModel;

  setup(() => {
    model = new AccountsModel(getAppContext().restApiService);
  });

  teardown(() => {
    model.finalize();
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
