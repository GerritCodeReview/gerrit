/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {GerritView} from '../../services/router/router-model';
import '../../test/common-test-setup';
import {assertRouteFalse, assertRouteState} from '../../test/test-utils';
import {
  AdminChildView,
  AdminViewState,
  createAdminUrl,
  PLUGIN_LIST_ROUTE,
} from './admin';

suite('admin view model', () => {
  suite('routes', () => {
    test('PLUGIN_LIST', () => {
      assertRouteFalse(PLUGIN_LIST_ROUTE, 'admin/plugins');
      assertRouteFalse(PLUGIN_LIST_ROUTE, '//admin/plugins');
      assertRouteFalse(PLUGIN_LIST_ROUTE, '//admin/plugins?');
      assertRouteFalse(PLUGIN_LIST_ROUTE, '/admin/plugins//');

      const state: AdminViewState = {
        view: GerritView.ADMIN,
        adminView: AdminChildView.PLUGINS,
      };
      assertRouteState<AdminViewState>(
        PLUGIN_LIST_ROUTE,
        '/admin/plugins',
        state,
        createAdminUrl
      );
    });
  });
});
