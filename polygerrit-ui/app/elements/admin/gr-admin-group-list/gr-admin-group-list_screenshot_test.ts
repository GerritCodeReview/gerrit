/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-admin-group-list';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAdminGroupList} from './gr-admin-group-list';
import {createGroupInfo} from '../../../test/test-data-generators';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GroupInfo, GroupName} from '../../../types/common';

suite('gr-admin-group-list screenshot tests', () => {
  let element: GrAdminGroupList;

  setup(async () => {
    const groups: GroupInfo[] = [
      {
        ...createGroupInfo('group-1'),
        name: 'Admins' as GroupName,
        description: 'Gerrit Site Administrators',
        options: {visible_to_all: true},
      },
      {
        ...createGroupInfo('group-2'),
        name: 'Project Owners' as GroupName,
        description: 'Owners of a particular project',
        options: {visible_to_all: true},
      },
      {
        ...createGroupInfo('group-3'),
        name: 'Service Users' as GroupName,
        description: 'Users that perform automated tasks',
        options: {visible_to_all: false},
      },
    ];

    element = await fixture<GrAdminGroupList>(
      html`<gr-admin-group-list></gr-admin-group-list>`
    );
    element.groups = groups;
    element.createNewCapability = true;
    element.loading = false;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-admin-group-list');
    await visualDiffDarkTheme(element, 'gr-admin-group-list');
  });
});
