/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-app';
import {assert, fixture, html} from '@open-wc/testing';
import * as sinon from 'sinon';
import {GrAppElement} from './gr-app-element';
import {testResolver} from '../test/common-test-setup';
import {pluginLoaderToken} from './shared/gr-js-api-interface/gr-plugin-loader';

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
        <gr-endpoint-decorator name="plugin-overlay"> </gr-endpoint-decorator>
        <gr-error-manager id="errorManager"> </gr-error-manager>
        <gr-plugin-host id="plugins"> </gr-plugin-host>
      `
    );
  });

  test('reflects waiting-for-initial-theme and clears when theme settles', async () => {
    const pluginsModel = testResolver(pluginLoaderToken).pluginsModel;
    assert.isFalse(element.hasAttribute('waiting-for-initial-theme'));

    pluginsModel.updateState({initialThemePending: true});
    await element.updateComplete;
    assert.isTrue(element.hasAttribute('waiting-for-initial-theme'));

    pluginsModel.updateState({initialThemePending: false});
    await element.updateComplete;
    assert.isFalse(element.hasAttribute('waiting-for-initial-theme'));
  });

  test('clears waiting-for-initial-theme after 300ms safety timeout', async () => {
    const clock = sinon.useFakeTimers();
    try {
      const pluginsModel = testResolver(pluginLoaderToken).pluginsModel;
      pluginsModel.updateState({initialThemePending: true});
      await element.updateComplete;
      assert.isTrue(element.hasAttribute('waiting-for-initial-theme'));

      clock.tick(300);
      await element.updateComplete;
      assert.isFalse(element.hasAttribute('waiting-for-initial-theme'));
    } finally {
      testResolver(pluginLoaderToken).pluginsModel.updateState({
        initialThemePending: false,
      });
      clock.restore();
    }
  });
});
