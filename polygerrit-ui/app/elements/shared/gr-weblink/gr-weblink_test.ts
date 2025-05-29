/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {assert, fixture} from '@open-wc/testing';
import {html} from 'lit';
import './gr-weblink';
import {GrWeblink} from './gr-weblink';
import {WebLinkInfo} from '../../../api/rest-api';

suite('gr-weblink tests', () => {
  test('renders with image', async () => {
    const info: WebLinkInfo = {
      name: 'gitiles',
      url: 'https://www.google.com',
      image_url: 'https://www.google.com/favicon.ico',
      tooltip: 'Open in Gitiles',
    };
    const element = await fixture<GrWeblink>(
      html`<gr-weblink .info=${info}></gr-weblink>`
    );
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <a
          href="https://www.google.com"
          rel="noopener noreferrer"
          target="_blank"
        >
          <gr-tooltip-content title="Open in Gitiles" has-tooltip>
            <img src="https://www.google.com/favicon.ico" />
          </gr-tooltip-content>
        </a>
      `
    );
  });

  test('renders with text', async () => {
    const info: WebLinkInfo = {
      name: 'gitiles',
      url: 'https://www.google.com',
      tooltip: 'Open in Gitiles',
    };
    const element = await fixture<GrWeblink>(
      html`<gr-weblink .info=${info}></gr-weblink>`
    );
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <a
          href="https://www.google.com"
          rel="noopener noreferrer"
          target="_blank"
        >
          <gr-tooltip-content title="Open in Gitiles" has-tooltip>
            <span>gitiles</span>
          </gr-tooltip-content>
        </a>
      `
    );
  });
});
