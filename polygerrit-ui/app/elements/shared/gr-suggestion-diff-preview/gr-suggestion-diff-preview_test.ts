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
import * as sinon from 'sinon';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {stubFlags, stubRestApi} from '../../../test/test-utils';
import {
  NumericChangeId,
  RepoName,
  RevisionPatchSetNum,
} from '../../../api/rest-api';
import {changeViewModelToken} from '../../../models/views/change';
import {
  createChangeViewState,
  createDiffViewState,
  createRange,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';

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
    element.previewLoadedFor = {
      fixSuggestionInfo: createFixSuggestionInfo(),
      changeNum: 42 as NumericChangeId,
      patchSet: 1 as RevisionPatchSetNum,
    };
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

  suite('applyFix navigation', () => {
    let setUrlStub: sinon.SinonStub;

    setup(() => {
      setUrlStub = sinon.stub(testResolver(navigationToken), 'setUrl');
      stubRestApi('applyFixSuggestion').returns(
        Promise.resolve(new Response(null, {status: 200}))
      );
      element.changeNum = 42 as NumericChangeId;
      element.repo = 'test-project' as RepoName;
      element.patchSet = 1 as RevisionPatchSetNum;

      element.fixSuggestionInfo = {
        ...createFixSuggestionInfo(),
        replacements: [
          {
            path: 'foo/bar.ts',
            replacement: 'new content',
            range: createRange(),
          },
        ],
      };
    });

    test('navigates to createDiffUrl when in Diff View', async () => {
      testResolver(changeViewModelToken).setState(createDiffViewState());

      await element.applyFix();

      assert.isTrue(setUrlStub.calledOnce);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1..edit/foo/bar.ts?forceReload=true'
      );
    });

    test('navigates to createChangeUrl when in Change View', async () => {
      testResolver(changeViewModelToken).setState(createChangeViewState());

      await element.applyFix();

      assert.isTrue(setUrlStub.calledOnce);
      assert.equal(
        setUrlStub.lastCall.firstArg,
        '/c/test-project/+/42/1..edit?forceReload=true'
      );
    });
  });
});
