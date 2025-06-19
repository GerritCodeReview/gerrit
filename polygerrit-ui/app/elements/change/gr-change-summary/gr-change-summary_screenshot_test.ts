/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-change-summary';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrChangeSummary} from './gr-change-summary';
import {
  createCheckResult,
  createComment,
  createDraft,
  createRun,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {checksModelToken} from '../../../models/checks/checks-model';
import {Category, RunStatus} from '../../../api/checks';
import {CheckRun} from '../../../models/checks/checks-model';
import {visualDiffDarkTheme} from '../../../test/test-utils';

suite('gr-change-summary screenshot tests', () => {
  let element: GrChangeSummary;

  setup(async () => {
    const commentsModel = testResolver(commentsModelToken);
    testResolver(checksModelToken);

    commentsModel.setState({
      comments: {
        'a.txt': [createComment(), {...createComment(), unresolved: true}],
      },
      drafts: {
        'a.txt': [createDraft(), createDraft(), createDraft()],
      },
      discardedDrafts: [],
      portedComments: {},
      portedDrafts: {},
    });

    const runs: CheckRun[] = [
      createRun({status: RunStatus.RUNNING, checkName: 'runner-1'}),
      createRun({
        status: RunStatus.COMPLETED,
        checkName: 'success-check',
        results: [createCheckResult({category: Category.SUCCESS})],
      }),
      createRun({
        status: RunStatus.COMPLETED,
        checkName: 'info-check',
        results: [createCheckResult({category: Category.INFO})],
      }),
      createRun({
        status: RunStatus.COMPLETED,
        checkName: 'warning-check',
        results: [createCheckResult({category: Category.WARNING})],
      }),
      createRun({
        status: RunStatus.COMPLETED,
        checkName: 'error-check',
        results: [createCheckResult({category: Category.ERROR})],
      }),
    ];

    element = await fixture<GrChangeSummary>(
      html`<gr-change-summary></gr-change-summary>`
    );
    element.runs = runs;
    element.showChecksSummary = true;
    await element.updateComplete;
  });

  test('screenshot with chips', async () => {
    await visualDiff(element, 'gr-change-summary-with-chips');
    await visualDiffDarkTheme(element, 'gr-change-summary-with-chips');
  });
});
