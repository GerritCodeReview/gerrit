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
        <paper-card aria-label="" class="controls" elevation="1">
          <div id="version-explanation">This file is being deleted.</div>
          <gr-overview-image>
            <img class="checkerboard" src="" style="background-color:;" />
          </gr-overview-image>
          <paper-dropdown-menu
            aria-disabled="false"
            dir="null"
            id="zoom-control"
            label="Zoom"
          >
            <paper-listbox
              aria-expanded="false"
              role="listbox"
              selected="fit"
              slot="dropdown-content"
              tabindex="0"
            >
              <paper-item
                aria-disabled="false"
                aria-selected="true"
                class="iron-selected"
                role="option"
                tabindex="0"
                value="fit"
              >
                Fit
              </paper-item>
              <paper-item
                aria-disabled="false"
                aria-selected="false"
                role="option"
                tabindex="-1"
                value="1"
              >
                100%
              </paper-item>
              <paper-item
                aria-disabled="false"
                aria-selected="false"
                role="option"
                tabindex="-1"
                value="1.25"
              >
                125%
              </paper-item>
              <paper-item
                aria-disabled="false"
                aria-selected="false"
                role="option"
                tabindex="-1"
                value="1.5"
              >
                150%
              </paper-item>
              <paper-item
                aria-disabled="false"
                aria-selected="false"
                role="option"
                tabindex="-1"
                value="1.75"
              >
                175%
              </paper-item>
              <paper-item
                aria-disabled="false"
                aria-selected="false"
                role="option"
                tabindex="-1"
                value="2"
              >
                200%
              </paper-item>
            </paper-listbox>
          </paper-dropdown-menu>
          <div class="color-picker">
            <div class="label">Background</div>
            <div class="options">
              <div class="color-picker-button selected">
                <paper-icon-button
                  aria-disabled="false"
                  class="checkerboard color"
                  role="button"
                  tabindex="0"
                >
                </paper-icon-button>
              </div>
              <div class="color-picker-button">
                <paper-icon-button
                  aria-disabled="false"
                  class="color"
                  role="button"
                  style="background-color:#fff;"
                  tabindex="0"
                >
                </paper-icon-button>
              </div>
              <div class="color-picker-button">
                <paper-icon-button
                  aria-disabled="false"
                  class="color"
                  role="button"
                  style="background-color:#000;"
                  tabindex="0"
                >
                </paper-icon-button>
              </div>
              <div class="color-picker-button">
                <paper-icon-button
                  aria-disabled="false"
                  class="color"
                  role="button"
                  style="background-color:#aaa;"
                  tabindex="0"
                >
                </paper-icon-button>
              </div>
            </div>
          </div>
        </paper-card>
      `
    );
  });
});
