/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-suggestion-diff-preview';
import {assert, fixture, html} from '@open-wc/testing';
import {
  CommentModel,
  commentModelToken,
} from '../gr-comment-model/gr-comment-model';
import {wrapInProvider} from '../../../models/di-provider-element';
import {
  createComment,
  createFixSuggestionInfo,
} from '../../../test/test-data-generators';
import {getAppContext} from '../../../services/app-context';
import {GrSuggestionDiffPreview} from './gr-suggestion-diff-preview';
import {stubFlags} from '../../../test/test-utils';

suite('gr-suggestion-diff-preview tests', () => {
  let element: GrSuggestionDiffPreview;

  setup(async () => {
    const commentModel = new CommentModel(getAppContext().restApiService);
    commentModel.updateState({
      comment: createComment(),
    });
    element = (
      await fixture<GrSuggestionDiffPreview>(
        wrapInProvider(
          html`
            <gr-suggestion-diff-preview
              .codeText=${'Hello World'}
              .fixSuggestionInfo=${createFixSuggestionInfo()}
            ></gr-suggestion-diff-preview>
          `,
          commentModelToken,
          commentModel
        )
      )
    ).querySelector<GrSuggestionDiffPreview>('gr-suggestion-diff-preview')!;
    await element.updateComplete;
  });

  test('render', async () => {
    await element.updateComplete;

    assert.shadowDom.equal(element, /* HTML */ '<code>Hello World</code>');
  });

  test('render diff', async () => {
    stubFlags('isEnabled').returns(true);
    element.previewLoadedFor = createFixSuggestionInfo();
    element.codeText =
      '  private handleClick(e: MouseEvent) {\ne.stopPropagation();\ne.preventDefault();';
    element.preview = {
      filepath:
        'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip_test.ts',
      preview: {
        meta_a: {
          name: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip_test.ts',
          content_type: 'application/typescript',
          lines: 6,
        },
        meta_b: {
          name: 'polygerrit-ui/app/elements/change/gr-change-summary/gr-summary-chip_test.ts',
          content_type: 'application/typescript',
          lines: 6,
        },
        intraline_status: 'OK',
        change_type: 'MODIFIED',
        content: [
          {
            ab: ['export class SummaryChip {'],
          },
          {
            a: [
              '  private handleClick(event: MouseEvent) {',
              '    event.stopPropagation();',
              '    event.preventDefault();',
              '  }',
            ],
            b: [
              '  private handleClick(evt: MouseEvent) {',
              '    evt.stopPropagation();',
              '    evt.preventDefault();',
              '  }',
            ],
            edit_a: [
              [24, 2],
              [23, 2],
              [27, 2],
            ],
            edit_b: [],
          },
          {
            ab: ['}'],
          },
        ],
      },
    };
    await element.updateComplete;

    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="diff-container">
          <gr-diff
            class="disable-context-control-buttons hide-line-length-indicator"
          >
          </gr-diff>
        </div>
      `,
      {ignoreAttributes: ['style']}
    );
  });
});
