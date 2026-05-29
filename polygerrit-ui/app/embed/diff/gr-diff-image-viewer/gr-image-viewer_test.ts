/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-image-viewer';
import {assert, fixture, html} from '@open-wc/testing';
import {GrImageViewer} from './gr-image-viewer';

suite('gr-image-viewer tests', () => {
  let element: GrImageViewer;

  setup(async () => {
    // Mock getLibrary to avoid 404s on loading resemblejs
    // 'libLoader' is a private static property on GrImageViewer
    // @ts-expect-error
    const libLoader = GrImageViewer.libLoader;
    sinon.stub(libLoader, 'getLibrary').resolves();

    // Mock window.resemble that is used in GrImageViewer.computeDiffImage
    // @ts-expect-error
    window.resemble = sinon.stub().returns({
      compareTo: sinon.stub().returns({
        ignoreNothing: sinon.stub().returns({
          onComplete: sinon
            .stub()
            .callsFake(cb =>
              cb({getImageDataUrl: () => 'data:image/png;base64,mock'})
            ),
        }),
      }),
    });

    element = await fixture<GrImageViewer>(
      html`<gr-image-viewer></gr-image-viewer>`
    );
    await element.updateComplete;
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="imageArea">
          <gr-zoomed-image class="base" style="cursor:pointer;">
            <div id="source-plus-highlight-container">
              <img
                class="checkerboard"
                id="source-image"
                src=""
                style="background-color:;"
              />
              <img
                id="highlight-image"
                style="opacity:0;pointer-events:none;"
              />
            </div>
          </gr-zoomed-image>
          <div id="spacer" style="width:0px;height:0px;"></div>
        </div>
        <div class="dimensions">0 x 0</div>
        <md-filled-card class="controls">
          <div id="version-explanation">This file is being deleted.</div>
          <gr-overview-image>
            <img class="checkerboard" src="" style="background-color:;" />
          </gr-overview-image>
          <md-filled-select id="zoom-control" label="Zoom">
            <md-select-option
              data-aria-selected="true"
              md-menu-item=""
              selected=""
              tabindex="0"
              value="fit"
            >
              <div slot="headline">Fit</div>
            </md-select-option>
            <md-select-option md-menu-item="" tabindex="-1" value="1">
              <div slot="headline">100%</div>
            </md-select-option>
            <md-select-option md-menu-item="" tabindex="-1" value="1.25">
              <div slot="headline">125%</div>
            </md-select-option>
            <md-select-option md-menu-item="" tabindex="-1" value="1.5">
              <div slot="headline">150%</div>
            </md-select-option>
            <md-select-option md-menu-item="" tabindex="-1" value="1.75">
              <div slot="headline">175%</div>
            </md-select-option>
            <md-select-option md-menu-item="" tabindex="-1" value="2">
              <div slot="headline">200%</div>
            </md-select-option>
          </md-filled-select>
          <div class="color-picker">
            <div class="label">Background</div>
            <div class="options">
              <div class="color-picker-button selected">
                <md-icon-button
                  class="checkerboard color"
                  touch-target="none"
                  value=""
                >
                </md-icon-button>
              </div>
              <div class="color-picker-button">
                <md-icon-button
                  class="color"
                  style="background-color:#fff;"
                  touch-target="none"
                  value=""
                >
                </md-icon-button>
              </div>
              <div class="color-picker-button">
                <md-icon-button
                  class="color"
                  style="background-color:#000;"
                  touch-target="none"
                  value=""
                >
                </md-icon-button>
              </div>
              <div class="color-picker-button">
                <md-icon-button
                  class="color"
                  style="background-color:#aaa;"
                  touch-target="none"
                  value=""
                >
                </md-icon-button>
              </div>
            </div>
          </div>
        </md-filled-card>
      `
    );
  });
});
