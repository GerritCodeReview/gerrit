/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../change/gr-reply-dialog/gr-reply-dialog';
import {stubElement} from '../../../test/test-utils';
import {assert, fixture} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import {ChangeReplyPluginApi} from '../../../api/change-reply';
import {GrReplyDialog} from '../../change/gr-reply-dialog/gr-reply-dialog';
import {html} from 'lit';

suite('gr-change-reply-js-api tests', () => {
  let changeReply: ChangeReplyPluginApi;
  let plugin: PluginApi;

  suite('init', () => {
    setup(async () => {
      window.Gerrit.install(
        p => {
          plugin = p;
        },
        '0.1',
        'http://test.com/plugins/testplugin/static/test.js'
      );
      changeReply = plugin.changeReply();
      await fixture<GrReplyDialog>(html`<gr-reply-dialog></gr-reply-dialog>>`);
      assert.ok(changeReply);
    });

    test('works', () => {
      stubElement('gr-reply-dialog', 'getLabelValue').returns('+123');
      assert.ok(changeReply);
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
