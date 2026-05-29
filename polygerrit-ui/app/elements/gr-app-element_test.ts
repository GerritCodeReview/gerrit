/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../test/common-test-setup';
import './gr-app';
import {assert, fixture, html} from '@open-wc/testing';
import {GrAppElement} from './gr-app-element';

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
});
