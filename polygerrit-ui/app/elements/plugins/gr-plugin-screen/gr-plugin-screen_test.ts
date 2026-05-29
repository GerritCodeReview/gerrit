/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../../gr-app';
import {assert, fixture, html} from '@open-wc/testing';
import {queryAndAssert} from '../../../utils/common-util';
import {screenName} from '../../../models/views/plugin';
import {GrEndpointDecorator} from '../gr-endpoint-decorator/gr-endpoint-decorator';
import {GrPluginScreen} from './gr-plugin-screen';

suite('gr-plugin-screen', () => {
  let element: GrPluginScreen;

  setup(async () => {
    element = await fixture<GrPluginScreen>(
      html`<gr-plugin-screen></gr-plugin-screen>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-endpoint-decorator>
          <gr-endpoint-param name="token"></gr-endpoint-param>
        </gr-endpoint-decorator>
      `
    );
  });

  test('renders plugin screen, changes endpoint instance', async () => {
    element.screen = 'test-screen-1';
    element.screenName = screenName('test-plugin', element.screen);
    await element.updateComplete;

    const endpoint1 = queryAndAssert<GrEndpointDecorator>(
      element,
      'gr-endpoint-decorator'
    );
    assert.equal(endpoint1.name, 'test-plugin-screen-test-screen-1');

    element.screen = 'test-screen-2';
    element.screenName = screenName('test-plugin', element.screen);
    await element.updateComplete;

    const endpoint2 = queryAndAssert<GrEndpointDecorator>(
      element,
      'gr-endpoint-decorator'
    );
    assert.equal(endpoint2.name, 'test-plugin-screen-test-screen-2');

    // Plugin screen endpoints have a variable name. Lit must not re-use the
    // same element instance. (Issue 16884)
    assert.isFalse(endpoint1 === endpoint2);
  });
});
