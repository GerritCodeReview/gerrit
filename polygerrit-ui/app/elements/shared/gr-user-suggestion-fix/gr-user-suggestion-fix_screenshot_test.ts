/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-user-suggestion-fix';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrUserSuggestionsFix} from './gr-user-suggestion-fix';
import {
  createComment,
  createFixSuggestionInfo,
} from '../../../test/test-data-generators';
import {wrapInProvider} from '../../../models/di-provider-element';
import {commentModelToken} from '../gr-comment-model/gr-comment-model';
import {CommentModel} from '../gr-comment-model/gr-comment-model';
import {getAppContext} from '../../../services/app-context';
import {stubFlags} from '../../../test/test-utils';

suite('gr-user-suggestion-fix screenshot tests', () => {
  let element: GrUserSuggestionsFix;

  setup(async () => {
    stubFlags('isEnabled').returns(true);
    const commentModel = new CommentModel(getAppContext().restApiService);
    commentModel.updateState({
      comment: createComment(),
      commentedText: 'const result = data.map(item => item.value);',
    });
    element = (
      await fixture<GrUserSuggestionsFix>(
        wrapInProvider(
          html`<gr-user-suggestion-fix
            >const result = data.map(item =>
            item.value).filter(Boolean);</gr-user-suggestion-fix
          >`,
          commentModelToken,
          commentModel
        )
      )
    ).querySelector<GrUserSuggestionsFix>('gr-user-suggestion-fix')!;
  });

  test('screenshot', async () => {
    await element.updateComplete;
    // mock preview because it's calculated on backend
    element.suggestionDiffPreview!.previewLoadedFor = createFixSuggestionInfo();
    element.suggestionDiffPreview!.preview = {
      filepath: 'test.ts',
      preview: {
        meta_a: {
          name: 'test.ts',
          content_type: 'application/typescript',
          lines: 6,
        },
        meta_b: {
          name: 'test.ts',
          content_type: 'application/typescript',
          lines: 6,
        },
        intraline_status: 'OK',
        change_type: 'MODIFIED',
        content: [
          {
            ab: ['export class Test {'],
          },
          {
            a: ['  private oldMethod() {', '    console.log("old");', '  }'],
            b: ['  private newMethod() {', '    console.log("new");', '  }'],
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
    await visualDiff(element, 'gr-user-suggestion-fix');
  });
});
