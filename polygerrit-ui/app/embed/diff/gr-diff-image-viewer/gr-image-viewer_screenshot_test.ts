/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-image-viewer';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrImageViewer} from './gr-image-viewer';

suite('gr-image-viewer screenshot tests', () => {
  let element: GrImageViewer;

  setup(async () => {
    element = await fixture<GrImageViewer>(
      html`<gr-image-viewer
        .baseUrl=${'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='}
        .revisionUrl=${'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='}
      ></gr-image-viewer>`
    );
    await element.updateComplete;
  });

  test('screenshot', async () => {
    await visualDiff(element, 'gr-image-viewer');
  });
}); 