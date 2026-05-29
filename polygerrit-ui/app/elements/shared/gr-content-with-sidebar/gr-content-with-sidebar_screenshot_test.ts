/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-content-with-sidebar';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {GrContentWithSidebar} from './gr-content-with-sidebar';

suite('gr-content-with-sidebar screenshot tests', () => {
  let wrapper: HTMLDivElement;

  setup(async () => {
    wrapper = await fixture<HTMLDivElement>(
      html` <div
        style="position: relative; padding-top: 50px; background: pink; width: 800px;"
      >
        <div style="height: 50px; background: orange;">Header / Banner</div>
        <gr-content-with-sidebar style="--sidebar-top: 50px;">
          <div
            slot="main"
            style="height: 500px; background: lightblue; width: 400px; padding: 20px; box-sizing: border-box;"
          >
            Main content area
          </div>
          <div
            slot="side"
            style="height: 600px; background: lightgray; padding: 20px; box-sizing: border-box;"
          >
            Sidebar content
          </div>
        </gr-content-with-sidebar>
      </div>`
    );
    const element = wrapper.querySelector<GrContentWithSidebar>(
      'gr-content-with-sidebar'
    )!;
    element.hideSide = false;
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(wrapper, 'gr-content-with-sidebar');
    await visualDiffDarkTheme(wrapper, 'gr-content-with-sidebar');
  });
});
