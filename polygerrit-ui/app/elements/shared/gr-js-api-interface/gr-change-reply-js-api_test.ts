/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../change/gr-reply-dialog/gr-reply-dialog';
import {stubElement} from '../../../test/test-utils';
import {assert} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import {ChangeReplyPluginApi} from '../../../api/change-reply';
import {assertIsDefined} from '../../../utils/common-util';

suite('gr-change-reply-js-api tests', () => {
  let changeReply: ChangeReplyPluginApi;
  let plugin: PluginApi;

  suite('early init', () => {
    setup(async () => {
      window.Gerrit.install(
        p => {
          plugin = p;
        },
        '0.1',
        'http://test.com/plugins/testplugin/static/test.js'
      );
      changeReply = plugin.changeReply();
      assertIsDefined(changeReply);
    });

    test('works', () => {
      stubElement('gr-reply-dialog', 'getLabelValue').returns('+123');
      assertIsDefined(changeReply);
      assert.equal(changeReply.getLabelValue('My-Label'), '+123');

      const setLabelValueStub = stubElement('gr-reply-dialog', 'setLabelValue');
      changeReply.setLabelValue('My-Label', '+1337');
      assert.isTrue(setLabelValueStub.calledWithExactly('My-Label', '+1337'));

      const setPluginMessageStub = stubElement(
        'gr-reply-dialog',
        'setPluginMessage'
      );
      changeReply.showMessage('foobar');
      assert.isTrue(setPluginMessageStub.calledWithExactly('foobar'));
    });
  });

  suite('normal init', () => {
    setup(async () => {
      window.Gerrit.install(
        p => {
          plugin = p;
        },
        '0.1',
        'http://test.com/plugins/testplugin/static/test.js'
      );
      changeReply = plugin.changeReply();
      assertIsDefined(changeReply);
    });

    test('works', () => {
      stubElement('gr-reply-dialog', 'getLabelValue').returns('+123');
      assertIsDefined(changeReply);
      assert.equal(changeReply.getLabelValue('My-Label'), '+123');

      const setLabelValueStub = stubElement('gr-reply-dialog', 'setLabelValue');
      changeReply.setLabelValue('My-Label', '+1337');
      assert.isTrue(setLabelValueStub.calledWithExactly('My-Label', '+1337'));

      const setPluginMessageStub = stubElement(
        'gr-reply-dialog',
        'setPluginMessage'
      );
      changeReply.showMessage('foobar');
      assert.isTrue(setPluginMessageStub.calledWithExactly('foobar'));
    });
  });
});
