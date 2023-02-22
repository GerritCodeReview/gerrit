/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-reply-dialog';
import {
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
} from '../../../test/test-utils';

import {GrReplyDialog} from './gr-reply-dialog';
import {fixture, html, assert} from '@open-wc/testing';
import {
  AccountId,
  NumericChangeId,
  PatchSetNumber,
  Timestamp,
} from '../../../types/common';
import {createChange} from '../../../test/test-data-generators';
import {GrButton} from '../../shared/gr-button/gr-button';
import {testResolver} from '../../../test/common-test-setup';
import {pluginLoaderToken} from '../../shared/gr-js-api-interface/gr-plugin-loader';

suite('gr-reply-dialog-it tests', () => {
  let element: GrReplyDialog;
  let changeNum: NumericChangeId;
  let latestPatchNum: PatchSetNumber;

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
    element.latestPatchNum = latestPatchNum;
    element.permittedLabels = {
      'Code-Review': ['-1', ' 0', '+1'],
      Verified: ['-1', ' 0', '+1'],
    };
  };

  setup(async () => {
    changeNum = 42 as NumericChangeId;
    latestPatchNum = 1 as PatchSetNumber;

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

  test('submit blocked when invalid email is supplied to ccs', async () => {
    const sendStub = sinon.stub(element, 'send').returns(Promise.resolve());

    element.ccsList!.entry!.setText('test');
    queryAndAssert<GrButton>(element, 'gr-button.send').click();
    assert.isFalse(element.ccsList!.submitEntryText());
    assert.isFalse(sendStub.called);
    await waitEventLoop();

    element.ccsList!.entry!.setText('test@test.test');
    queryAndAssert<GrButton>(element, 'gr-button.send').click();
    assert.isTrue(sendStub.called);
  });

  test('lgtm plugin', async () => {
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
    element = await fixture(html`<gr-reply-dialog></gr-reply-dialog>`);
    setupElement(element);
    const pluginLoader = testResolver(pluginLoaderToken);
    pluginLoader.loadPlugins([]);
    await pluginLoader.awaitPluginsLoaded();
    await waitEventLoop();
    await waitEventLoop();
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
