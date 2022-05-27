/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

import '../../../test/common-test-setup-karma';
import './gr-reply-dialog';
import {
  queryAndAssert,
  resetPlugins,
  stubRestApi,
} from '../../../test/test-utils';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {GrReplyDialog} from './gr-reply-dialog';
import {fixture, html} from '@open-wc/testing-helpers';
import {
  AccountId,
  NumericChangeId,
  PatchSetNum,
  Timestamp,
} from '../../../types/common';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {createChange} from '../../../test/test-data-generators';
import {GrTextarea} from '../../shared/gr-textarea/gr-textarea';

const basicFixture = fixtureFromElement('gr-reply-dialog');

suite('gr-reply-dialog-it tests', () => {
  let element: GrReplyDialog;
  let changeNum: NumericChangeId;
  let patchNum: PatchSetNum;

  const setupElement = (element: GrReplyDialog) => {
    element.change = {
      ...createChange(),
      _number: changeNum,
      labels: {
        Verified: {
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
          default_value: 0,
        },
        'Code-Review': {
          values: {
            '-2': 'This shall not be submitted',
            '-1': 'I would prefer this is not submitted as is',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          all: [{_account_id: 42 as AccountId, value: 0}],
          default_value: 0,
        },
      },
    };
    element.patchNum = patchNum;
    element.permittedLabels = {
      'Code-Review': ['-1', ' 0', '+1'],
      Verified: ['-1', ' 0', '+1'],
    };
  };

  setup(async () => {
    changeNum = 42 as NumericChangeId;
    patchNum = 1 as PatchSetNum;

    stubRestApi('getAccount').returns(
      Promise.resolve({
        _account_id: 42 as AccountId,
        registered_on: '' as Timestamp,
      })
    );

    element = await fixture<GrReplyDialog>(html`
      <gr-reply-dialog></gr-reply-dialog>
    `);
    setupElement(element);

    await element.updateComplete;
  });

  teardown(() => {
    resetPlugins();
  });

  test('submit blocked when invalid email is supplied to ccs', () => {
    const sendStub = sinon.stub(element, 'send').returns(Promise.resolve());

    element.ccsList!.entry!.setText('test');
    MockInteractions.tap(queryAndAssert(element, 'gr-button.send'));
    assert.isFalse(element.ccsList!.submitEntryText());
    assert.isFalse(sendStub.called);
    flush();

    element.ccsList!.entry!.setText('test@test.test');
    MockInteractions.tap(queryAndAssert(element, 'gr-button.send'));
    assert.isTrue(sendStub.called);
  });

  test('lgtm plugin', async () => {
    resetPlugins();
    window.Gerrit.install(
      plugin => {
        const replyApi = plugin.changeReply();
        replyApi.addReplyTextChangedCallback(text => {
          const label = 'Code-Review';
          const labelValue = replyApi.getLabelValue(label);
          if (labelValue && labelValue === ' 0' && text.indexOf('LGTM') === 0) {
            replyApi.setLabelValue(label, '+1');
          }
        });
      },
      undefined,
      'http://test.com/plugins/lgtm.js'
    );
    element = basicFixture.instantiate();
    setupElement(element);
    getPluginLoader().loadPlugins([]);
    await getPluginLoader().awaitPluginsLoaded();
    await flush();
    const textarea = queryAndAssert<GrTextarea>(
      element,
      'gr-textarea'
    ).getNativeTextarea();
    textarea.value = 'LGTM';
    textarea.dispatchEvent(
      new CustomEvent('input', {bubbles: true, composed: true})
    );
    await flush();
    const labelScoreRows = queryAndAssert(
      element.getLabelScores(),
      'gr-label-score-row[name="Code-Review"]'
    );
    const selectedBtn = queryAndAssert(
      labelScoreRows,
      'gr-button[data-value="+1"]'
    );
    assert.isOk(selectedBtn);
  });
});
