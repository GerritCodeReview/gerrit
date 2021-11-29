/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../change/gr-reply-dialog/gr-reply-dialog.js';
import {stubRestApi} from '../../../test/test-utils.js';

const basicFixture = fixtureFromElement('gr-reply-dialog');

suite('gr-change-reply-js-api tests', () => {
  let element;
  let changeReply;
  let plugin;

  setup(() => {
    stubRestApi('getAccount').returns(Promise.resolve(null));
  });

  suite('early init', () => {
    setup(() => {
      window.Gerrit.install(p => { plugin = p; }, '0.1',
          'http://test.com/plugins/testplugin/static/test.js');
      changeReply = plugin.changeReply();
      element = basicFixture.instantiate();
    });

    teardown(() => {
      changeReply = null;
    });

    test('works', () => {
      sinon.stub(element, 'getLabelValue').returns('+123');
      assert.equal(changeReply.getLabelValue('My-Label'), '+123');

      sinon.stub(element, 'setLabelValue');
      changeReply.setLabelValue('My-Label', '+1337');
      assert.isTrue(
          element.setLabelValue.calledWithExactly('My-Label', '+1337'));

      sinon.stub(element, 'setPluginMessage');
      changeReply.showMessage('foobar');
      assert.isTrue(element.setPluginMessage.calledWithExactly('foobar'));
    });
  });

  suite('normal init', () => {
    setup(() => {
      element = basicFixture.instantiate();
      window.Gerrit.install(p => { plugin = p; }, '0.1',
          'http://test.com/plugins/testplugin/static/test.js');
      changeReply = plugin.changeReply();
    });

    teardown(() => {
      changeReply = null;
    });

    test('works', () => {
      sinon.stub(element, 'getLabelValue').returns('+123');
      assert.equal(changeReply.getLabelValue('My-Label'), '+123');

      sinon.stub(element, 'setLabelValue');
      changeReply.setLabelValue('My-Label', '+1337');
      assert.isTrue(
          element.setLabelValue.calledWithExactly('My-Label', '+1337'));

      sinon.stub(element, 'setPluginMessage');
      changeReply.showMessage('foobar');
      assert.isTrue(element.setPluginMessage.calledWithExactly('foobar'));
    });
  });
});

