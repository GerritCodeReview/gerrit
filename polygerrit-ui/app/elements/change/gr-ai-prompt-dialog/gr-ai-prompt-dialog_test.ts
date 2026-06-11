/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-ai-prompt-dialog';
import {assert, fixture, html} from '@open-wc/testing';
import {GrAiPromptDialog} from './gr-ai-prompt-dialog';
import {
  createParsedChange,
  createThread,
} from '../../../test/test-data-generators';
import {AccountInfo, CommitId, PatchSetNum} from '../../../api/rest-api';
import {stubRestApi, waitUntil} from '../../../test/test-utils';
import {testResolver} from '../../../test/common-test-setup';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {
  ChangeModel,
  changeModelToken,
} from '../../../models/change/change-model';
import {of} from 'rxjs';

suite('gr-ai-prompt-dialog test', () => {
  let element: GrAiPromptDialog;
  let getPatchContentStub: sinon.SinonStub;
  setup(async () => {
    getPatchContentStub = stubRestApi('getPatchContent');
    getPatchContentStub.resolves('test code');
    const commentsModel = testResolver(commentsModelToken);
    Object.defineProperty(commentsModel, 'threads$', {
      value: of([]),
      writable: true,
    });

    element = await fixture(html`<gr-ai-prompt-dialog></gr-ai-prompt-dialog>`);
    element.change = createParsedChange();
    element.change.current_revision = 'abc' as CommitId;
    element.change.revisions['abc'].commit!.parents = [
      {
        commit: 'def' as CommitId,
        subject: 'Parent commit subject',
      },
    ];
    element.patchNum = 1 as PatchSetNum;
    element.selectedTemplate = 'PATCH_ONLY';
    element.open();
    await waitUntil(() => !!element.patchContent);
  });

  test('renders', async () => {
    assert.shadowDom.equal(
      element,
      ` <section>
         <h3 class="heading-3">
           Copy AI Prompt (experimental)
         </h3>
       </section>
       <section class="flexContainer">
         <div class="content">
           <div class="options-bar">
             <div class="template-selector">
               <div class="template-options">
                 <label class="template-option">
                   <md-radio
                     name="template"
                     tabindex="-1"
                   >
                   </md-radio>
                   Help me with review
                 </label>
                 <label class="template-option">
                   <md-radio
                     name="template"
                     tabindex="-1"
                   >
                   </md-radio>
                   Improve commit message
                 </label>
                 <label class="template-option">
                   <md-radio
                     checked=""
                     name="template"
                     tabindex="0"
                   >
                   </md-radio>
                   Just patch content
                 </label>
                 <label class="template-option">
                   <md-radio
                     name="template"
                     tabindex="-1"
                   >
                   </md-radio>
                   Unresolved Comments
                 </label>
               </div>
             </div>
             <div class="context-selector">
               <md-outlined-select
                 label="Context"
                 value="3"
               >
                 <md-select-option
                   md-menu-item=""
                   tabindex="0"
                 >
                   <div slot="headline">
                     3 lines (default)
                   </div>
                 </md-select-option>
                 <md-select-option
                   md-menu-item=""
                   tabindex="-1"
                 >
                   <div slot="headline">
                     10 lines
                   </div>
                 </md-select-option>
                 <md-select-option
                   md-menu-item=""
                   tabindex="-1"
                 >
                   <div slot="headline">
                     25 lines
                   </div>
                 </md-select-option>
                 <md-select-option
                   md-menu-item=""
                   tabindex="-1"
                 >
                   <div slot="headline">
                     50 lines
                   </div>
                 </md-select-option>
                 <md-select-option
                   md-menu-item=""
                   tabindex="-1"
                 >
                   <div slot="headline">
                     100 lines
                   </div>
                 </md-select-option>
               </md-outlined-select>
             </div>
           </div>
           <textarea
             placeholder="Patch content will appear here..."
             readonly=""
           >
           </textarea>
           <div class="toolbar">
             <div class="info-text">
               You can paste this prompt in an AI Model if your project
                    code can be shared with AI. We recommend a thinking model.
                    You can also use it for an AI Agent as context (a reference
                    to a git change).
              </div>
              <div class="actions">
                <div class="size">
                  2 words
                </div>
                <gr-button>
                  <gr-icon
                    icon="content_copy"
                    small=""
                  >
                  </gr-icon>
                  Copy Prompt
                </gr-button>
              </div>
            </div>
          </div>
        </section>
        <section class="footer">
          <span class="closeButtonContainer">
            <gr-button
              id="closeButton"
              link=""
            >
              Close
            </gr-button>
          </span>
        </section>`
    );
  });

  test('handles failed patch content fetch', async () => {
    getPatchContentStub.callsFake((_c, _p, _ctx, errFn) => {
      if (errFn) errFn();
      return Promise.resolve(undefined);
    });
    const fireStub = sinon.stub(element, 'dispatchEvent');

    element.open();

    await waitUntil(() => fireStub.called);

    assert.isTrue(fireStub.called);
    const events = fireStub.args.map(arg => arg[0]);
    assert.isTrue(
      events.some(
        event =>
          event.type === 'show-error' &&
          (event as CustomEvent).detail.message ===
            'Failed to get patch content'
      )
    );
  });
  test('renders help review prompt', async () => {
    element.selectedTemplate = 'HELP_REVIEW';
    await element.updateComplete;
    assert.include(
      Reflect.get(element, 'promptContent') as string,
      'You are a highly experienced code reviewer'
    );
  });

  test('renders resolve comments prompt', async () => {
    element.selectedTemplate = 'RESOLVE_COMMENTS';
    await element.updateComplete;
    assert.include(
      Reflect.get(element, 'promptContent') as string,
      'No unresolved comments.'
    );
  });

  test('renders resolve comments prompt with comments', async () => {
    element.threads = [
      {
        ...createThread({
          message: 'test comment',
          author: {name: 'Tester'} as AccountInfo,
          unresolved: true,
        }),
        path: 'test.txt',
        line: 1,
      },
    ];
    element.selectedTemplate = 'RESOLVE_COMMENTS';
    await element.updateComplete;
    const expected = `* File: test.txt (Line 1)
Tester:
test comment`;
    assert.include(Reflect.get(element, 'promptContent') as string, expected);
  });

  test('preserves dollar signs in patch content', async () => {
    const expected = '+IMAGE="${SCRIPT_NAME}_$$"';
    element.patchContent = expected;
    element.selectedTemplate = 'PATCH_ONLY';
    await element.updateComplete;

    const promptContent = Reflect.get(element, 'promptContent') as string;
    assert.include(promptContent, expected);
  });

  suite('eager loading prevention', () => {
    let changeModel: ChangeModel;

    setup(() => {
      getPatchContentStub.resetHistory();
      changeModel = testResolver(changeModelToken);
    });

    test('does not load patch content on initialization', async () => {
      const change = createParsedChange();
      change.revisions['abc'].commit!.parents = [
        {
          commit: 'def' as CommitId,
          subject: 'Parent',
        },
      ];
      Object.defineProperty(changeModel, 'change$', {
        value: of(change),
        writable: true,
      });
      Object.defineProperty(changeModel, 'patchNum$', {
        value: of(1 as PatchSetNum),
        writable: true,
      });

      const testElement = await fixture<GrAiPromptDialog>(
        html`<gr-ai-prompt-dialog></gr-ai-prompt-dialog>`
      );
      await testElement.updateComplete;

      assert.isFalse(getPatchContentStub.called);
    });

    test('loads patch content when open is called', async () => {
      const change = createParsedChange();
      change.revisions['abc'].commit!.parents = [
        {
          commit: 'def' as CommitId,
          subject: 'Parent',
        },
      ];
      Object.defineProperty(changeModel, 'change$', {
        value: of(change),
        writable: true,
      });
      Object.defineProperty(changeModel, 'patchNum$', {
        value: of(1 as PatchSetNum),
        writable: true,
      });

      const testElement = await fixture<GrAiPromptDialog>(
        html`<gr-ai-prompt-dialog></gr-ai-prompt-dialog>`
      );
      await testElement.updateComplete;

      assert.isFalse(getPatchContentStub.called);

      testElement.open();
      await testElement.updateComplete;

      assert.isTrue(getPatchContentStub.called);
    });
  });
});
