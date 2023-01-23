/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {GerritView} from '../../services/router/router-model';
import '../../test/common-test-setup';
import {AdminChildView, PLUGIN_LIST_ROUTE} from './admin';

suite('admin view model', () => {
  suite('routes', () => {
    test('PLUGIN_LIST', () => {
      const {urlPattern: pattern, createState} = PLUGIN_LIST_ROUTE;

      assert.isTrue(pattern.test('/admin/plugins'));
      assert.isTrue(pattern.test('/admin/plugins/'));
      assert.isFalse(pattern.test('admin/plugins'));
      assert.isFalse(pattern.test('//admin/plugins'));
      assert.isFalse(pattern.test('//admin/plugins?'));
      assert.isFalse(pattern.test('/admin/plugins//'));

      assert.deepEqual(createState({}), {
        view: GerritView.ADMIN,
        adminView: AdminChildView.PLUGINS,
      });
    });
  });
});
