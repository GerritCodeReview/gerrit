/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-fix-suggestions';
import {GrFixSuggestions} from './gr-fix-suggestions';
import {
  createComment,
  createFixSuggestionInfo,
} from '../../../test/test-data-generators';
import {PatchSetNumber} from '../../../types/common';

suite('gr-fix-suggestions', () => {
  let element: GrFixSuggestions;

  setup(async () => {
    element = await fixture<GrFixSuggestions>(
      html`<gr-fix-suggestions
        .generated_fix_suggestions=${[createFixSuggestionInfo()]}
        .comment=${{
          ...createComment(),
          id: '1',
          patch_set: 1 as PatchSetNumber,
        }}
      ></gr-fix-suggestions>`
    );
  });

  test('render', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="header">
          <div class="title">
            <span> Suggested edit </span>
            <a
              href="/Documentation/user-suggest-edits.html"
              rel="noopener noreferrer"
              target="_blank"
            >
              <gr-endpoint-decorator name="fix-suggestion-title-help">
                <gr-endpoint-param name="suggestion"> </gr-endpoint-param>
                <gr-icon icon="help" title="read documentation"> </gr-icon>
              </gr-endpoint-decorator>
            </a>
          </div>
          <div class="headerMiddle">
            <gr-button
              aria-disabled="false"
              class="action show-fix"
              flatten=""
              role="button"
              secondary=""
              tabindex="0"
            >
              Show Edit
            </gr-button>
            <div class="show-hide" tabindex="0">
              <label aria-label="Collapse" class="show-hide">
                <md-checkbox class="show-hide"> </md-checkbox>
                <gr-icon icon="expand_less" id="icon"> </gr-icon>
              </label>
            </div>
          </div>
        </div>
        <gr-suggestion-diff-preview> </gr-suggestion-diff-preview>
      `
    );
  });
});
