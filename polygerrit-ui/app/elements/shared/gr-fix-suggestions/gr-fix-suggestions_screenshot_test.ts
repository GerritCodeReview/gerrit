/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-fix-suggestions';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-ignore
import {visualDiff} from '@web/test-runner-visual-regression';
import {GrFixSuggestions} from './gr-fix-suggestions';
import {
  createComment,
  createFixSuggestionInfo,
} from '../../../test/test-data-generators';
import {NumericChangeId, RevisionPatchSetNum} from '../../../api/rest-api';
import {stubFlags, visualDiffDarkTheme} from '../../../test/test-utils';
import {highlightServiceToken} from '../../../services/highlight/highlight-service';
import {testResolver} from '../../../test/common-test-setup';
import * as sinon from 'sinon';
import {highlightedStringToRanges} from '../../../utils/syntax-util';
import {SyntaxLayerLine} from '../../../types/syntax-worker-api';
import {PatchSetNumber} from '../../../types/common';

suite('gr-fix-suggestions screenshot tests', () => {
  let element: GrFixSuggestions;

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
    await element.updateComplete;
  });

  test('ai fix suggestion with syntax highlighting', async () => {
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

    await visualDiff(element, 'gr-fix-suggestions');
    await visualDiffDarkTheme(element, 'gr-fix-suggestions');
  });
});
