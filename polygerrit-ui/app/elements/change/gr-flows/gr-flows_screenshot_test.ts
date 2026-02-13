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
import {
  stubRestApi,
  visualDiffDarkTheme,
  waitUntil,
} from '../../../test/test-utils';
import {
  AccountId,
  CommitId,
  FlowInfo,
  FlowStageState,
  NumericChangeId,
  Timestamp,
} from '../../../api/rest-api';
import {FlowsModel, flowsModelToken} from '../../../models/flows/flows-model';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {testResolver} from '../../../test/common-test-setup';
import {
  createAccountDetailWithId,
  createParsedChange,
  createRevision,
} from '../../../test/test-data-generators';

function setChangeWithUploader(
  changeModel: ChangeModel,
  uploaderId: AccountId
) {
  changeModel.updateState({
    change: {
      ...createParsedChange(),
      _number: 123 as NumericChangeId,
      revisions: {
        rev1: {
          ...createRevision(1),
          uploader: createAccountDetailWithId(uploaderId),
        },
      },
      current_revision: 'rev1' as CommitId,
    },
  });
}

suite('gr-flows screenshot tests', () => {
  let element: GrFlows;
  let flowsModel: FlowsModel;
  let changeModel: ChangeModel;
  let userModel: UserModel;

  setup(async () => {
    stubRestApi('getIfFlowsIsEnabled').returns(
      Promise.resolve({enabled: true})
    );
    stubRestApi('listFlows').returns(Promise.resolve([]));

    flowsModel = testResolver(flowsModelToken);
    changeModel = testResolver(changeModelToken);
    userModel = testResolver(userModelToken);

    element = await fixture<GrFlows>(html`<gr-flows></gr-flows>`);

    setChangeWithUploader(changeModel, 1 as AccountId);
    userModel.setState({
      account: createAccountDetailWithId(1 as AccountId),
      accountLoaded: true,
    });

    // Wait for the initial loading state to resolve from API mocks
    await waitUntil(() => !flowsModel.getState().loading);

    const flows: FlowInfo[] = [
      {
        uuid: 'flow-12345678-90ab-cdef-1234-567890abcdef',
        owner: createAccountDetailWithId(1 as AccountId),
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

    flowsModel.setState({
      flows,
      loading: false,
      isEnabled: true,
      providers: [],
    });
    await element.updateComplete;
    await waitUntil(
      () => element.shadowRoot!.querySelectorAll('.flow').length === 1
    );
  });

  test('flows list', async () => {
    await visualDiff(element, 'gr-flows');
    await visualDiffDarkTheme(element, 'gr-flows');
  });

  test('flows empty state', async () => {
    flowsModel.setState({
      ...flowsModel.getState(),
      flows: [],
      providers: [],
    });
    await element.updateComplete;
    await waitUntil(
      () =>
        !!element
          .shadowRoot!.querySelector('p')
          ?.textContent?.includes('No flows found')
    );
    await visualDiff(element, 'gr-flows-empty');
    await visualDiffDarkTheme(element, 'gr-flows-empty');
  });

  test('flows loading state', async () => {
    flowsModel.setState({
      ...flowsModel.getState(),
      flows: [],
      loading: true,
      providers: [],
    });
    await element.updateComplete;
    await waitUntil(
      () =>
        !!element
          .shadowRoot!.querySelector('p')
          ?.textContent?.includes('Loading')
    );
    await visualDiff(element, 'gr-flows-loading');
    await visualDiffDarkTheme(element, 'gr-flows-loading');
  });

  test('cannot create flow (not uploader)', async () => {
    userModel.setState({
      account: createAccountDetailWithId(2 as AccountId),
      accountLoaded: true,
    });
    await element.updateComplete;
    await waitUntil(
      () =>
        !!element.shadowRoot!.textContent?.includes(
          'New flows can only be added by change uploader.'
        )
    );
    await visualDiff(element, 'gr-flows-not-uploader');
    await visualDiffDarkTheme(element, 'gr-flows-not-uploader');
  });

  test('multiple flows', async () => {
    const originalFlows = flowsModel.getState().flows;
    flowsModel.setState({
      ...flowsModel.getState(),
      flows: [
        ...originalFlows,
        {
          uuid: 'flow-87654321-cdef-90ab-5678-abcdef123456',
          owner: createAccountDetailWithId(1 as AccountId),
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
      ],
      providers: [],
    });
    await element.updateComplete;
    await waitUntil(
      () => element.shadowRoot!.querySelectorAll('.flow').length === 2
    );
    await visualDiff(element, 'gr-flows-multiple');
    await visualDiffDarkTheme(element, 'gr-flows-multiple');
  });
});
