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
import {NumericChangeId, RevisionPatchSetNum} from '../../../api/rest-api';
import {getAppContext} from '../../../services/app-context';
import {stubFlags, visualDiffDarkTheme} from '../../../test/test-utils';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {testResolver} from '../../../test/common-test-setup';
import * as sinon from 'sinon';
import {highlightedStringToRanges} from '../../../utils/syntax-util';
import {SyntaxLayerLine} from '../../../types/syntax-worker-api';

suite('gr-user-suggestion-fix screenshot tests', () => {
  let element: GrUserSuggestionsFix;

  setup(async () => {
    stubFlags('isEnabled').returns(true);
    const highlightService = testResolver(highlightServiceToken);
    const leftRanges: SyntaxLayerLine[] = highlightedStringToRanges(
      '<span class="keyword">export</span> <span class="keyword">class</span> <span class="title">Test</span> {\n' +
        '  <span class="keyword">private</span> <span class="title function_">oldMethod</span>() {\n' +
        '    <span class="variable">console</span>.<span class="title function_">log</span>(<span class="string">"old"</span>);\n' +
        '  }\n' +
        '}'
    );
    const rightRanges: SyntaxLayerLine[] = highlightedStringToRanges(
      '<span class="keyword">export</span> <span class="keyword">class</span> <span class="title">Test</span> {\n' +
        '  <span class="keyword">private</span> <span class="title function_">newMethod</span>() {\n' +
        '    <span class="variable">console</span>.<span class="title function_">log</span>(<span class="string">"new"</span>);\n' +
        '  }\n' +
        '}'
    );
    sinon.stub(highlightService, 'highlight').callsFake(async (_lang, code) => {
      if (code?.includes('oldMethod')) return leftRanges;
      if (code?.includes('newMethod')) return rightRanges;
      return [];
    });

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
    element.suggestionDiffPreview!.previewLoadedFor = {
      fixSuggestionInfo: createFixSuggestionInfo(),
      changeNum: 42 as NumericChangeId,
      patchSet: 1 as RevisionPatchSetNum,
    };
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
    element.requestUpdate();
    await element.updateComplete;
    await element.suggestionDiffPreview!.updateComplete;
    // Allow syntax worker promise and notify to apply annotations
    await new Promise(r => setTimeout(r, 100));
    await document.fonts?.ready;

    await visualDiff(element, 'gr-user-suggestion-fix');
    await visualDiffDarkTheme(element, 'gr-user-suggestion-fix');
  });
});
