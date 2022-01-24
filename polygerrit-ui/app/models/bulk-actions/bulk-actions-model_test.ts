/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {createChange} from '../../test/test-data-generators';
import {ChangeId} from '../../api/rest-api';
import {BulkActionsModel} from './bulk-actions-model';
import {getAppContext} from '../../services/app-context';
import '../../test/common-test-setup-karma';

test('add and remove selected changes', () => {
  const c1 = createChange();
  c1.change_id = '1' as ChangeId;
  const c2 = createChange();
  c2.change_id = '2' as ChangeId;

  const bulkActionsModel = new BulkActionsModel(getAppContext().restApiService);

  assert.deepEqual(bulkActionsModel.getState().selectedChanges, []);

  bulkActionsModel.addSelectedChange(c1);
  assert.deepEqual(bulkActionsModel.getState().selectedChanges, [{...c1}]);

  bulkActionsModel.addSelectedChange(c2);
  assert.deepEqual(bulkActionsModel.getState().selectedChanges, [
    {
      ...c1,
    },
    {...c2},
  ]);

  bulkActionsModel.removeSelectedChange(c1);
  assert.deepEqual(bulkActionsModel.getState().selectedChanges, [{...c2}]);

  bulkActionsModel.removeSelectedChange(c2);
  assert.deepEqual(bulkActionsModel.getState().selectedChanges, []);
});
