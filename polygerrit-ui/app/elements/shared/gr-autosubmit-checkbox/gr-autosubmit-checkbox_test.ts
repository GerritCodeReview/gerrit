/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {userModelToken} from '../../../models/user/user-model';
import '../../../test/common-test-setup';
import './gr-autosubmit-checkbox';
import {
  FlowsModel,
  flowsModelToken,
  SUBMIT_ACTION_NAME,
  SUBMIT_CONDITION,
} from '../../../models/flows/flows-model';
import {AccountId, FlowStageState} from '../../../api/rest-api';
import {GrIcon} from '../../shared/gr-icon/gr-icon';
import {testResolver} from '../../../test/common-test-setup';
import {query, queryAndAssert} from '../../../utils/common-util';
import {isVisible} from '../../../test/test-utils';
import {GrAutosubmitCheckbox} from './gr-autosubmit-checkbox';
import {assert, fixture, html, waitUntil} from '@open-wc/testing';
import {
  createAccountDetailWithId,
  createChange,
  createFlow,
  createParsedChange,
} from '../../../test/test-data-generators';
import {changeModelToken} from '../../../models/change/change-model';
import {ParsedChangeInfo} from '../../../types/types';

suite('gr-autosubmit-checkbox tests', () => {
  let element: GrAutosubmitCheckbox;

  suite('autosubmit checkbox rendering', () => {
    let flowsModel: FlowsModel;

    setup(async () => {
      element = await fixture<GrAutosubmitCheckbox>(html`
        <gr-autosubmit-checkbox></gr-autosubmit-checkbox>
      `);
      const change = createChange();
      const userModel = testResolver(userModelToken);
      userModel.setAccount(createAccountDetailWithId(change.owner._account_id));
      element.getChangeModel().updateState({
        change: change as ParsedChangeInfo,
      });
      element.getFlowsModel().updateState({
        isEnabled: true,
        autosubmitProviders: [
          {
            isAutosubmitEnabled: () => true,
            getSubmitCondition: () => '',
            getSubmitAction: () => undefined,
          },
        ],
      });
      await element.updateComplete;
      flowsModel = testResolver(flowsModelToken);
    });

    test('checkbox rendered when isAutosubmitEnabled is true', async () => {
      element.isAutosubmitEnabled = true;
      await element.updateComplete;
      assert.isTrue(isVisible(queryAndAssert(element, '#autosubmit')));
    });

    test('checkbox not rendered when isAutosubmitEnabled is false', async () => {
      element.isAutosubmitEnabled = false;
      await element.updateComplete;
      assert.isNotOk(query(element, '#autosubmit'));
    });

    test('checkbox not rendered if autosubmit flow is already present', async () => {
      const flow = createFlow({
        stages: [
          {
            expression: {
              condition: SUBMIT_CONDITION,
              action: {name: SUBMIT_ACTION_NAME},
            },
            state: FlowStageState.DONE,
          },
        ],
      });
      flowsModel.setState({...flowsModel.getState(), flows: [flow]});
      await waitUntil(() => flowsModel.getState().flows.length > 0);

      await element.updateComplete;
      assert.isNotOk(query(element, '#autosubmit'));
    });

    test('isAutosubmitEnabled depends on isOwner', async () => {
      const userModel = testResolver(userModelToken);
      const changeModel = testResolver(changeModelToken);

      flowsModel.updateState({
        isEnabled: true,
        autosubmitProviders: [
          {
            isAutosubmitEnabled: () => true,
            getSubmitCondition: () => '',
            getSubmitAction: () => undefined,
          },
        ],
        flows: [],
      });

      // Case 1: user is NOT owner
      userModel.setAccount(createAccountDetailWithId(123 as AccountId));
      changeModel.updateStateChange({
        ...createParsedChange(),
        owner: {_account_id: 456 as AccountId},
      });
      await element.updateComplete;
      assert.isFalse(element.isAutosubmitEnabled);

      // Case 2: user IS owner
      userModel.setAccount(createAccountDetailWithId(456 as AccountId));
      await element.updateComplete;
      assert.isTrue(element.isAutosubmitEnabled);
    });
  });

  suite('autosubmit info message rendering', () => {
    test('info message rendered when showAutosubmitInfoMessage is true', async () => {
      element.showAutosubmitInfoMessage = true;
      await element.updateComplete;
      const autosubmitInfo = queryAndAssert(element, '.autosubmit-info');
      assert.isTrue(isVisible(autosubmitInfo));
      const icon = queryAndAssert<GrIcon>(autosubmitInfo, 'gr-icon');
      assert.equal(icon.icon, 'info');
      const text = queryAndAssert(autosubmitInfo, 'span');
      assert.equal(text.textContent, 'Autosubmit Enabled.');
    });

    test('info message not rendered when showAutosubmitInfoMessage is false', async () => {
      element.showAutosubmitInfoMessage = false;
      await element.updateComplete;
      assert.isNotOk(query(element, '.autosubmit-info'));
    });
  });
});
