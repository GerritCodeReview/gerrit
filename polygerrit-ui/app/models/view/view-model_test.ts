/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import {ViewModel} from './view-model';
import '../../test/common-test-setup-karma';

suite('view model test', () => {
  let viewModel: ViewModel;
  setup(() => {
    viewModel = new ViewModel();
  });

  test('setSelectedIndexForDashboard', () => {
    assert.isNotOk(viewModel.getState().selectedIndexForDashboard.get('test'));
    viewModel.setSelectedIndexForDashboard('test', 1);
    assert.equal(viewModel.getState().selectedIndexForDashboard.get('test'), 1);
  });
});
