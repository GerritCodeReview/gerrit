/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../../test/common-test-setup-karma.js';
import './gr-reply-dialog.js';

import {queryAndAssert, resetPlugins, stubRestApi} from '../../../test/test-utils.js';
import {_testOnly_initGerritPluginApi} from '../../shared/gr-js-api-interface/gr-gerrit.js';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';

const basicFixture = fixtureFromElement('gr-reply-dialog');
const pluginApi = _testOnly_initGerritPluginApi();

suite('gr-reply-dialog-it tests', () => {
  let element;
  let changeNum;
  let patchNum;

  const setupElement = element => {
    element.change = {
      _number: changeNum,
      labels: {
        'Verified': {
          values: {
            '-1': 'Fails',
            ' 0': 'No score',
            '+1': 'Verified',
          },
          default_value: 0,
        },
        'Code-Review': {
          values: {
            '-2': 'Do not submit',
            '-1': 'I would prefer that you didn\'t submit this',
            ' 0': 'No score',
            '+1': 'Looks good to me, but someone else must approve',
            '+2': 'Looks good to me, approved',
          },
          all: [{_account_id: 42, value: 0}],
          default_value: 0,
        },
      },
    };
    element.patchNum = patchNum;
    element.permittedLabels = {
      'Code-Review': [
        '-1',
        ' 0',
        '+1',
      ],
      'Verified': [
        '-1',
        ' 0',
        '+1',
      ],
    };
  };

  setup(() => {
    changeNum = 42;
    patchNum = 1;

    stubRestApi('getAccount').returns(Promise.resolve({_account_id: 42}));

    element = basicFixture.instantiate();
    setupElement(element);
    // Allow the elements created by dom-repeat to be stamped.
    flush();
  });

  teardown(() => {
    resetPlugins();
  });

  test('_submit blocked when invalid email is supplied to ccs', () => {
    const sendStub = sinon.stub(element, 'send').returns(Promise.resolve());

    element.$.ccs.$.entry.setText('test');
    MockInteractions.tap(element.shadowRoot.querySelector('gr-button.send'));
    assert.isFalse(sendStub.called);
    flush();

    element.$.ccs.$.entry.setText('test@test.test');
    MockInteractions.tap(element.shadowRoot.querySelector('gr-button.send'));
    assert.isTrue(sendStub.called);
  });

  test('lgtm plugin', async () => {
    resetPlugins();
    pluginApi.install(plugin => {
      const replyApi = plugin.changeReply();
      replyApi.addReplyTextChangedCallback(text => {
        const label = 'Code-Review';
        const labelValue = replyApi.getLabelValue(label);
        if (labelValue && labelValue === ' 0' && text.indexOf('LGTM') === 0) {
          replyApi.setLabelValue(label, '+1');
        }
      });
    }, null, 'http://test.com/plugins/lgtm.js');
    element = basicFixture.instantiate();
    setupElement(element);
    getPluginLoader().loadPlugins([]);
    await getPluginLoader().awaitPluginsLoaded();
    await flush();
    const textarea = queryAndAssert(element, 'gr-textarea').getNativeTextarea();
    textarea.value = 'LGTM';
    textarea.dispatchEvent(
        new CustomEvent('input', {bubbles: true, composed: true}));
    await flush();
    const labelScoreRows = element.getLabelScores().shadowRoot.querySelector(
        'gr-label-score-row[name="Code-Review"]');
    const selectedBtn =
        labelScoreRows.shadowRoot.querySelector('gr-button[data-value="+1"]');
    assert.isOk(selectedBtn);
  });
});
