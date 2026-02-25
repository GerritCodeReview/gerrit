/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-reply-dialog';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrReplyDialog} from './gr-reply-dialog';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {createChange} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {PatchSetNumber} from '../../../api/rest-api';

suite('gr-reply-dialog screenshot tests', () => {
  let element: GrReplyDialog;

  setup(async () => {
    testResolver(commentsModelToken);

    element = await fixture<GrReplyDialog>(
      html`<gr-reply-dialog></gr-reply-dialog>`
    );
    element.change = createChange();
    element.latestPatchNum = 1 as PatchSetNumber;
    await element.updateComplete;
  });

  test('autosubmit checkbox rendered', async () => {
    element.autosubmitChecked = true;
    await element.updateComplete;
    await visualDiff(element, 'gr-reply-dialog-autosubmit');
    await visualDiffDarkTheme(element, 'gr-reply-dialog-autosubmit');
  });
});
