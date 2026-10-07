/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-create-repo-dialog';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrCreateRepoDialog} from './gr-create-repo-dialog';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-create-repo-dialog screenshot tests', () => {
  let element: GrCreateRepoDialog;

  setup(async () => {
    element = await fixture<GrCreateRepoDialog>(
      html`<gr-create-repo-dialog></gr-create-repo-dialog>`
    );
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-create-repo-dialog');
    await visualDiffDarkTheme(element, 'gr-create-repo-dialog');
  });
});
