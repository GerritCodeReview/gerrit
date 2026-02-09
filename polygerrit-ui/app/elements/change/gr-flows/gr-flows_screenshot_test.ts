/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-flows';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrFlows} from './gr-flows';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {
  AccountId,
  FlowInfo,
  FlowStageState,
  NumericChangeId,
  Timestamp,
} from '../../../api/rest-api';

suite('gr-flows screenshot tests', () => {
  let element: GrFlows;

  setup(async () => {
    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);

    (element as any).flows = [
      {
        uuid: 'flow-12345678-90ab-cdef-1234-567890abcdef',
        change_id: 123 as NumericChangeId,
        created: '2025-02-09 10:00:00.000000000' as Timestamp,
        last_evaluated: '2025-02-09 10:05:00.000000000' as Timestamp,
        stages: [
          {
            state: FlowStageState.DONE,
            expression: {
              condition: 'status:open',
              action: {name: 'review', parameters: ['Code-Review+1']},
            },
            message: 'Condition met, added Code-Review+1.',
          },
          {
            state: FlowStageState.PENDING,
            expression: {
              condition: 'status:merged',
              action: {name: 'submit'},
            },
            message: 'Waiting for merge status.',
          },
          {
            state: FlowStageState.FAILED,
            expression: {
              condition: 'status:abandoned',
              action: {name: 'review', parameters: ['Code-Review-2']},
            },
            message: 'Condition failed.',
          },
          {
            state: FlowStageState.TERMINATED,
            expression: {
              condition: 'status:terminated',
              action: {name: 'review', parameters: ['Code-Review-2']},
            },
            message: 'Condition terminated.',
          },
        ],
      },
    ];
    (element as any).changeNum = 123 as NumericChangeId;
    (element as any).changeUploader = 1 as AccountId;
    (element as any).account = {_account_id: 1 as AccountId, name: 'Uploader'};
    (element as any).loading = false;
    await element.updateComplete;
  });

  test('flows list', async () => {
    await visualDiff(element, 'gr-flows');
    await visualDiffDarkTheme(element, 'gr-flows');
  });

  test('flows list not matching filter', async () => {
    element.statusFilter = FlowStageState.DONE;
    await element.updateComplete;
    await visualDiff(element, 'gr-flows-filter-done');
    await visualDiffDarkTheme(element, 'gr-flows-filter-done');
  });

  test('flows empty state', async () => {
    (element as any).flows = [];
    await element.updateComplete;
    await visualDiff(element, 'gr-flows-empty');
    await visualDiffDarkTheme(element, 'gr-flows-empty');
  });

  test('flows loading state', async () => {
    (element as any).flows = [];
    (element as any).loading = true;
    await element.updateComplete;
    await visualDiff(element, 'gr-flows-loading');
    await visualDiffDarkTheme(element, 'gr-flows-loading');
  });

  test('cannot create flow (not uploader)', async () => {
    (element as any).account = {_account_id: 2 as AccountId, name: 'Someone'};
    await element.updateComplete;
    await visualDiff(element, 'gr-flows-not-uploader');
    await visualDiffDarkTheme(element, 'gr-flows-not-uploader');
  });

  test('multiple flows', async () => {
    const originalFlows = (element as any).flows;
    (element as any).flows = [
      ...originalFlows,
      {
        uuid: 'flow-87654321-cdef-90ab-5678-abcdef123456',
        change_id: 123 as NumericChangeId,
        created: '2025-02-10 10:00:00.000000000' as Timestamp,
        last_evaluated: undefined,
        stages: [
          {
            state: FlowStageState.PENDING,
            expression: {
              condition: 'status:merged',
              action: {name: 'submit'},
            },
          },
        ],
      },
    ];
    await element.updateComplete;
    await visualDiff(element, 'gr-flows-multiple');
    await visualDiffDarkTheme(element, 'gr-flows-multiple');
  });
});
