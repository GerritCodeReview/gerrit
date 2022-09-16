/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert} from '@open-wc/testing';
import {GroupId} from '../../api/rest-api';
import {GerritView} from '../../services/router/router-model';
import '../../test/common-test-setup';
import {createGroupUrl, GroupDetailView, GroupViewState} from './group';

suite('group view state tests', () => {
  test('createGroupUrl() info', () => {
    const params: GroupViewState = {
      view: GerritView.GROUP,
      groupId: '1234' as GroupId,
    };
    assert.equal(createGroupUrl(params), '/admin/groups/1234');
  });

  test('createGroupUrl() members', () => {
    const params: GroupViewState = {
      view: GerritView.GROUP,
      groupId: '1234' as GroupId,
      detail: 'members' as GroupDetailView,
    };
    assert.equal(createGroupUrl(params), '/admin/groups/1234,members');
  });

  test('createGroupUrl() audit log', () => {
    const params: GroupViewState = {
      view: GerritView.GROUP,
      groupId: '1234' as GroupId,
      detail: 'log' as GroupDetailView,
    };
    assert.equal(createGroupUrl(params), '/admin/groups/1234,audit-log');
  });
});
