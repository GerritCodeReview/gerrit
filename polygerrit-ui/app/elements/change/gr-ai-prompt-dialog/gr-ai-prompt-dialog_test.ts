/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-ai-prompt-dialog';
import {assert, fixture, html} from '@open-wc/testing';
import {GrAiPromptDialog} from './gr-ai-prompt-dialog';
import {createParsedChange} from '../../../test/test-data-generators';
import {PatchSetNum} from '../../../api/rest-api';
import {stubRestApi} from '../../../test/test-utils';

suite('gr-ai-prompt-dialog test', () => {
  let element: GrAiPromptDialog;
  setup(async () => {
    stubRestApi('getPatchContent').returns(Promise.resolve('<patch>'));
    element = await fixture(html`<gr-ai-prompt-dialog></gr-ai-prompt-dialog>`);
    element.change = createParsedChange();
    element.patchNum = 1 as PatchSetNum;
    await element.updateComplete;
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ ` <section>
          <h3 class="heading-3">Create AI Prompt (experimental)</h3>
        </section>
        <section class="flexContainer">
          <div class="content">
            <div class="template-selector">
              <div class="template-options">
                <label class="template-option">
                  <input
                    checked=""
                    name="template"
                    type="radio"
                    value="HELP_REVIEW"
                  />
                  Help me with review
                </label>
                <label class="template-option">
                  <input
                    name="template"
                    type="radio"
                    value="IMPROVE_COMMIT_MESSAGE"
                  />
                  Improve commit message
                </label>
                <label class="template-option">
                  <input name="template" type="radio" value="PATCH_ONLY" />
                  Just patch content
                </label>
              </div>
            </div>
            <textarea
              placeholder="Patch content will appear here..."
              readonly=""
            >
            </textarea>
            <div class="toolbar">
              <gr-button>
                <gr-icon icon="content_copy" small=""> </gr-icon>
                Copy Prompt
              </gr-button>
            </div>
          </div>
        </section>
        <section class="footer">
          <span class="closeButtonContainer">
            <gr-button id="closeButton" link=""> Close </gr-button>
          </span>
        </section>`
    );
  });
});
