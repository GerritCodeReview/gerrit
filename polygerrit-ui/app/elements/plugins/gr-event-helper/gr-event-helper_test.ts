/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';

import {fixture, html, assert} from '@open-wc/testing';
import {PluginApi} from '../../../api/plugin';
import {EventHelperPluginApi} from '../../../api/event-helper';

suite('gr-event-helper tests', () => {
  let element: HTMLDivElement;
  let eventHelper: EventHelperPluginApi;

  setup(async () => {
    let plugin: PluginApi;
    window.Gerrit.install(
      p => (plugin = p),
      '0.1',
      'http://test.com/plugins/testplugin/static/test.js'
    );
    element = await fixture(html`<div></div>`);
    eventHelper = plugin!.eventHelper(element);
  });

  test('listens via onTap', async () => {
    let parentReceivedClick = false;
    element.parentElement!.addEventListener(
      'click',
      () => (parentReceivedClick = true)
    );
    let helperReceivedClick = false;

    eventHelper.onTap(() => {
      helperReceivedClick = true;
      return true;
    });
    element.click();

    assert.isTrue(helperReceivedClick);
    assert.isTrue(parentReceivedClick);
  });

  test('listens via onClick', async () => {
    let parentReceivedClick = false;
    element.parentElement!.addEventListener(
      'click',
      () => (parentReceivedClick = true)
    );
    let helperReceivedClick = false;

    eventHelper.onClick(() => {
      helperReceivedClick = true;
      return true;
    });
    element.click();

    assert.isTrue(helperReceivedClick);
    assert.isTrue(parentReceivedClick);
  });

  test('onTap false blocks event to parent', async () => {
    let parentReceivedTap = false;
    element.parentElement!.addEventListener(
      'tap',
      () => (parentReceivedTap = true)
    );

    eventHelper.onTap(() => false);
    element.click();

    assert.isFalse(parentReceivedTap);
  });

  test('onClick false blocks event to parent', async () => {
    let parentReceivedTap = false;
    element.parentElement!.addEventListener(
      'tap',
      () => (parentReceivedTap = true)
    );

    eventHelper.onClick(() => false);
    element.click();

    assert.isFalse(parentReceivedTap);
  });
});
