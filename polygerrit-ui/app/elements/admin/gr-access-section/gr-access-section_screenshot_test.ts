/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-access-section';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrAccessSection} from './gr-access-section';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {PermissionAccessSection} from '../gr-repo-access/gr-repo-access-interfaces';
import {GitRef} from '../../../types/common';

suite('gr-access-section screenshot tests', () => {
  let element: GrAccessSection;

  setup(async () => {
    const section: PermissionAccessSection = {
      id: 'refs/heads/*' as GitRef,
      value: {
        permissions: {
          read: {
            label: 'Read',
            rules: {},
          },
        },
      },
    };

    element = await fixture<GrAccessSection>(
      html`<gr-access-section></gr-access-section>`
    );
    element.section = section;
    element.capabilities = {};
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-access-section');
    await visualDiffDarkTheme(element, 'gr-access-section');
  });
});
