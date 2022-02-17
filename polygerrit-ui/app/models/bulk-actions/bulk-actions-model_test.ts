/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../test/test-data-generators';
import {ChangeInfoId} from '../../api/rest-api';
import {BulkActionsModel} from './bulk-actions-model';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup-karma';

suite('bulk actions model test', () => {
  test('add and remove selected changes', () => {
    const c1 = createChange();
    c1.id = '1' as ChangeInfoId;
    const c2 = createChange();
    c2.id = '2' as ChangeInfoId;

    const bulkActionsModel = new BulkActionsModel(
      getAppContext().restApiService
    );

    assert.deepEqual(bulkActionsModel.getState().selectedChangeIds, []);

    bulkActionsModel.addSelectedChange(c1);
    assert.deepEqual(bulkActionsModel.getState().selectedChangeIds, [c1.id]);

    bulkActionsModel.addSelectedChange(c2);
    assert.deepEqual(bulkActionsModel.getState().selectedChangeIds, [
      c1.id,
      c2.id,
    ]);

    bulkActionsModel.removeSelectedChange(c1);
    assert.deepEqual(bulkActionsModel.getState().selectedChangeIds, [c2.id]);

    bulkActionsModel.removeSelectedChange(c2);
    assert.deepEqual(bulkActionsModel.getState().selectedChangeIds, []);
  });
});
