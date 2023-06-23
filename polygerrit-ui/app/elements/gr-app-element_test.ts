/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-app';
import {fixture, html, assert} from '@open-wc/testing';
import {GrAppElement} from './gr-app-element';
import {queryAndAssert} from '../utils/common-util';
import {GerritView} from '../services/router/router-model';
import {PluginViewState} from '../models/views/plugin';
import {GrEndpointDecorator} from './plugins/gr-endpoint-decorator/gr-endpoint-decorator';

suite('gr-app-element tests', () => {
  let element: GrAppElement;

  setup(async () => {
    element = await fixture<GrAppElement>(
      html`<gr-app-element></gr-app-element>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-css-mixins> </gr-css-mixins>
        <gr-endpoint-decorator name="banner"> </gr-endpoint-decorator>
        <gr-main-header loggedin id="mainHeader" role="banner">
        </gr-main-header>
        <main>
          <div class="errorView" id="errorView">
            <div class="errorEmoji"></div>
            <div class="errorText"></div>
            <div class="errorMoreInfo"></div>
          </div>
        </main>
        <footer>
          <div>
            Powered by
            <a
              href="https://www.gerritcodereview.com/"
              rel="noopener noreferrer"
              target="_blank"
            >
              Gerrit Code Review
            </a>
            ()
            <gr-endpoint-decorator name="footer-left"> </gr-endpoint-decorator>
          </div>
          <div>
            Press “?” for keyboard shortcuts
            <gr-endpoint-decorator name="footer-right"> </gr-endpoint-decorator>
          </div>
        </footer>
        <gr-notifications-prompt> </gr-notifications-prompt>
        <gr-endpoint-decorator name="plugin-overlay"> </gr-endpoint-decorator>
        <gr-error-manager id="errorManager"> </gr-error-manager>
        <gr-plugin-host id="plugins"> </gr-plugin-host>
      `
    );
  });

  test('renders plugin screen, changes endpoint instance', async () => {
    element.view = GerritView.PLUGIN_SCREEN;
    element.params = {
      view: GerritView.PLUGIN_SCREEN,
      screen: 'test-screen-1',
      plugin: 'test-plugin',
    } as PluginViewState;
    await element.updateComplete;

    const main1 = queryAndAssert(element, 'main');
    const endpoint1 = queryAndAssert<GrEndpointDecorator>(
      main1,
      'gr-endpoint-decorator'
    );
    assert.equal(endpoint1.name, 'test-plugin-screen-test-screen-1');

    element.params = {
      view: GerritView.PLUGIN_SCREEN,
      screen: 'test-screen-2',
      plugin: 'test-plugin',
    } as PluginViewState;
    await element.updateComplete;

    const main2 = queryAndAssert(element, 'main');
    const endpoint2 = queryAndAssert<GrEndpointDecorator>(
      main2,
      'gr-endpoint-decorator'
    );
    assert.equal(endpoint2.name, 'test-plugin-screen-test-screen-2');

    // Plugin screen endpoints have a variable name. Lit must not re-use the
    // same element instance. (Issue 16884)
    assert.isFalse(endpoint1 === endpoint2);
  });
});
