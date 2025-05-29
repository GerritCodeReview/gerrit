/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import '../../../test/common-test-setup';
import './gr-reply-dialog';
import {
  queryAndAssert,
  stubRestApi,
  waitEventLoop,
  waitUntil,
} from '../../../test/test-utils';

import {GrReplyDialog} from './gr-reply-dialog';
import {assert, fixture, html} from '@open-wc/testing';
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
import {GrComment} from '../../shared/gr-comment/gr-comment';
import {createNewPatchsetLevel} from '../../../utils/comment-util';
import {commentsModelToken} from '../../../models/comments/comments-model';

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
    testResolver(commentsModelToken).addNewDraft(
      createNewPatchsetLevel(latestPatchNum, '', false)
    );
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
    const attachStub = sinon.stub();
    const callbackStub = sinon.stub();
    window.Gerrit.install(
      plugin => {
        const replyApi = plugin.changeReply();
        const hook = plugin.hook('reply-text');
        hook.onAttached(attachStub);
        replyApi.addReplyTextChangedCallback(text => {
          callbackStub(text);
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
    // This may seem a bit weird, but we have to somehow make sure that the
    // event listener is actually installed, and apparently a `gr-comment` is
    // attached twice inside the 'reply-text' endpoint. Could not find a better
    // way to make sure that the callback is ready to receive events.
    await waitUntil(() => attachStub.callCount === 2);

    const comment = queryAndAssert<GrComment>(
      element,
      'gr-comment#patchsetLevelComment'
    );
    comment.messageText = 'LGTM';

    await waitUntil(() => callbackStub.calledWith('LGTM'));

    const labelScoreRows = queryAndAssert(
      element.getLabelScores(),
      'gr-label-score-row[name="Code-Review"]'
    );
    const selectedBtn = queryAndAssert(
      labelScoreRows,
      'gr-button[data-value="+1"].iron-selected'
    );
    assert.isOk(selectedBtn);
  });
});
