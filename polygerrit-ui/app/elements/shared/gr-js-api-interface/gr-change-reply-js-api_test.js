/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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

