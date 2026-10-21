/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-main-header';
import {fixture, html} from '@open-wc/testing';
import {setViewport} from '@web/test-runner-commands';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrMainHeader} from './gr-main-header';
import {
  stubElement,
  stubRestApi,
  visualDiffDarkTheme,
} from '../../../test/test-utils';

suite('gr-main-header screenshot tests', () => {
  let element: GrMainHeader;

  setup(async () => {
    await setViewport({width: 1200, height: 100});
    stubRestApi('probePath').returns(Promise.resolve(false));
    stubElement('gr-main-header', 'loadAccount').callsFake(() =>
      Promise.resolve()
    );
    await Promise.all(
      ['/diffy.png', '/gerrit_icon.png'].map(
        src =>
          new Promise<void>((resolve, reject) => {
            const img = new Image();
            img.onload = () => resolve();
            img.onerror = () => reject(new Error(`Failed to load ${src}`));
            img.src = src;
          })
      )
    );
    element = await fixture<GrMainHeader>(
      html`<gr-main-header
        style="display: block; background: var(--header-background, var(--header-background-color, #eee));"
      ></gr-main-header>`
    );
    element.loginUrl = '/login';
    await element.updateComplete;
  });

  test('header screenshot', async () => {
    await visualDiff(element, 'gr-main-header');
    await visualDiffDarkTheme(element, 'gr-main-header');
  });

  test('header screenshot with custom icon override', async () => {
    const customThemeEl = document.createElement('style');
    customThemeEl.textContent =
      'html { --header-icon: url("/gerrit_icon.png"); }';
    document.head.appendChild(customThemeEl);
    try {
      await visualDiff(element, 'gr-main-header-custom-icon');
      await visualDiffDarkTheme(element, 'gr-main-header-custom-icon');
    } finally {
      customThemeEl.remove();
    }
  });
});
