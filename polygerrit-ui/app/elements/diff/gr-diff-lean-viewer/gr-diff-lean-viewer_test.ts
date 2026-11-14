/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-diff-lean-viewer';
import {GrDiffLeanViewer} from './gr-diff-lean-viewer';
import {DiffInfo} from '../../../types/diff';
import {CommentSide, DiffViewMode, Side} from '../../../constants/constants';
import {
  createAccountDetailWithId,
  createComment,
  createCommentThread,
  createDiff,
  createPatchRange,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {
  CommentThread,
  DraftInfo,
  EDIT,
  PARENT,
  RevisionPatchSetNum,
} from '../../../types/common';
import sinon from 'sinon';

suite('gr-diff-lean-viewer tests', () => {
  let element: GrDiffLeanViewer;
  let commentsModel: CommentsModel;
  let userModel: UserModel;

  const sampleLeanDiff: DiffInfo = {
    ...createDiff(),
    meta_a: {name: 'Soundness.lean', content_type: 'text/x-lean', lines: 16},
    meta_b: {name: 'Soundness.lean', content_type: 'text/x-lean', lines: 18},
    content: [
      {
        ab: [
          '/-!',
          '# Bounded Lattice Soundness',
          'Mechanized proofs with zero `sorry`.',
          '-/',
          '',
          'structure CellState where',
          '  level : Nat -- `[2:0]`',
          '  weight : Nat -- `[7:3]`',
          '  deriving DecidableEq, Repr',
          '',
          '/-- Reflexivity of `maskSub`. -/',
          'theorem maskSub_refl (a : Nat) : (a &&& a) = a := by',
        ],
      },
      {
        a: ['  sorry'],
        b: ['  apply Nat.eq_of_testBit_eq', '  intro i', '  simp'],
      },
    ],
  };

  setup(async () => {
    commentsModel = testResolver(commentsModelToken);
    userModel = testResolver(userModelToken);
    userModel.setAccount(createAccountDetailWithId(1));

    element = await fixture<GrDiffLeanViewer>(
      html`<gr-diff-lean-viewer></gr-diff-lean-viewer>`
    );
  });

  test('renders empty grid and hides summary bar when no diff is provided', () => {
    const grid = element.shadowRoot!.querySelector('.diff-grid');
    assert.isNotNull(grid);
    assert.equal(grid.children.length, 0);
    assert.isNull(element.shadowRoot!.querySelector('.lean-summary-bar'));
  });

  test('renders summary bar, hygiene verified pill, structures, and theorems side-by-side', async () => {
    element.diff = sampleLeanDiff;
    await element.updateComplete;

    const summaryBar = element.shadowRoot!.querySelector('.lean-summary-bar');
    assert.isNotNull(summaryBar);
    assert.include(summaryBar.textContent, 'Lean 4');
    assert.include(summaryBar.textContent, '0 sorry');
    assert.include(summaryBar.textContent, '1 theorem');
    assert.include(summaryBar.textContent, '1 type');

    const verifiedPill = element.shadowRoot!.querySelector(
      '.hygiene-pill.verified'
    );
    assert.isNotNull(verifiedPill);

    const leftHeader = element.shadowRoot!.querySelector('.column-header.left');
    const rightHeader = element.shadowRoot!.querySelector(
      '.column-header.right'
    );
    assert.equal(leftHeader!.textContent.trim(), 'Base');
    assert.equal(rightHeader!.textContent.trim(), 'Revision');

    // Structure renders a structured field table + deriving chips
    const fieldsTable = element.shadowRoot!.querySelector('.lean-fields-table');
    assert.isNotNull(fieldsTable);
    assert.include(fieldsTable.textContent, 'level');
    assert.include(fieldsTable.textContent, 'weight');

    const derivingChips = Array.from(
      element.shadowRoot!.querySelectorAll('.lean-deriving-chip')
    ).map(el => el.textContent.trim());
    assert.include(derivingChips, 'DecidableEq');
    assert.include(derivingChips, 'Repr');

    // Left (base) theorem card displays sorry hygiene warning badge, right does not
    const modifiedLeft = element.shadowRoot!.querySelector(
      '.diff-cell.modified-left'
    );
    const modifiedRight = element.shadowRoot!.querySelector(
      '.diff-cell.modified-right'
    );
    assert.isNotNull(modifiedLeft);
    assert.isNotNull(modifiedRight);
    assert.isNotNull(modifiedLeft.querySelector('.lean-hygiene-badge.error'));
    assert.isNull(modifiedRight.querySelector('.lean-hygiene-badge.error'));
  });

  test('surfaces sorry, axiom, and partial warnings in summary bar when present in revision', async () => {
    element.diff = {
      ...createDiff(),
      content: [
        {
          b: [
            'axiom trust_me : False',
            '',
            'partial def spin (n : Nat) : Nat := spin (n + 1)',
            '',
            'theorem unfinished : True := by',
            '  sorry',
          ],
        },
      ],
    };
    await element.updateComplete;

    assert.isNull(element.shadowRoot!.querySelector('.hygiene-pill.verified'));
    const errorPills = Array.from(
      element.shadowRoot!.querySelectorAll('.hygiene-pill.error')
    ).map(el => el.textContent.trim());
    const warningPills = Array.from(
      element.shadowRoot!.querySelectorAll('.hygiene-pill.warning')
    ).map(el => el.textContent.trim());

    assert.isTrue(errorPills.some(t => t.includes('1 sorry')));
    assert.isTrue(warningPills.some(t => t.includes('1 axiom')));
    assert.isTrue(warningPills.some(t => t.includes('1 partial')));
  });

  test('renders official lean-lang.org constructs cleanly without false sorry alerts on raw strings, char quotes, or guillemets', async () => {
    element.diff = {
      ...createDiff(),
      content: [
        {
          b: [
            'module',
            '',
            'public import Init.Core',
            '',
            'structure Child extends Parent where',
            "  quote : Char := '\"' -- double-quote literal",
            '  rawNote : String := r#"never trigger "sorry" here"#',
            'deriving Repr, BEq',
            '',
            'inductive Direction where',
            '  | north | south | east | west',
            'deriving Repr',
            '',
            'mutual',
            '  def isEven : Nat → Bool',
            '    | 0 => true',
            '    | n + 1 => isOdd n',
            '',
            '  def isOdd : Nat → Bool',
            '    | 0 => false',
            '    | n + 1 => isEven n',
            'end',
            '',
            'instance : Add Nat where',
            '  add x y := x + y',
            '',
            'def ackermann (m n : Nat) : Nat :=',
            '  match m, n with',
            '  | 0, y => y + 1',
            '  | x + 1, 0 => ackermann x 1',
            '  | x + 1, y + 1 => ackermann x (ackermann (x + 1) y)',
            'termination_by (m, n)',
            'decreasing_by',
            '  all_goals simp_wf',
            '',
            'theorem «1 + 1 = 2» : 1 + 1 = 2 := by',
            '  calc',
            '    1 + 1 = 2 := rfl',
            '',
            'def «sorry» : Nat := 0',
          ],
        },
      ],
    };
    await element.updateComplete;

    // Must show green verified `0 sorry` pill and ZERO error/warning hygiene pills
    const verifiedPill = element.shadowRoot!.querySelector(
      '.hygiene-pill.verified'
    );
    assert.isNotNull(verifiedPill);
    assert.include(verifiedPill.textContent, '0 sorry');
    assert.isNull(element.shadowRoot!.querySelector('.hygiene-pill.error'));
    assert.isNull(element.shadowRoot!.querySelector('.hygiene-pill.warning'));

    // Inline-pipe inductive renders all 4 constructors in the constructor table
    const tables = Array.from(
      element.shadowRoot!.querySelectorAll('.lean-fields-table')
    );
    assert.isAtLeast(tables.length, 2);
    const ctorTable = tables[1];
    assert.include(ctorTable.textContent, '| north');
    assert.include(ctorTable.textContent, '| south');
    assert.include(ctorTable.textContent, '| east');
    assert.include(ctorTable.textContent, '| west');

    // Summary stats include theorems, types, defs, and instances
    const summaryText =
      element.shadowRoot!.querySelector('.lean-summary-bar')!.textContent;
    assert.include(summaryText, '1 theorem');
    assert.include(summaryText, '2 types');
    assert.include(summaryText, '1 instance');
  });

  test('surfaces unsafe, admit, and native_decide warnings in summary bar and declaration cards', async () => {
    element.diff = {
      ...createDiff(),
      content: [
        {
          b: [
            'unsafe def readMem (n : Nat) : Nat := n',
            '',
            'theorem skipped : False := by admit',
            '',
            'theorem fastCheck : 2 + 2 = 4 := by native_decide',
          ],
        },
      ],
    };
    await element.updateComplete;

    assert.isNull(element.shadowRoot!.querySelector('.hygiene-pill.verified'));
    const errorPills = Array.from(
      element.shadowRoot!.querySelectorAll('.hygiene-pill.error')
    ).map(el => el.textContent.trim());
    const warningPills = Array.from(
      element.shadowRoot!.querySelectorAll('.hygiene-pill.warning')
    ).map(el => el.textContent.trim());

    assert.isTrue(errorPills.some(t => t.includes('1 sorry')));
    assert.isTrue(errorPills.some(t => t.includes('1 unsafe')));
    assert.isTrue(warningPills.some(t => t.includes('1 native_decide')));
  });

  test('renders unified diff mode correctly and toggles back to side-by-side', async () => {
    element.diff = sampleLeanDiff;
    element.viewMode = DiffViewMode.UNIFIED;
    await element.updateComplete;

    assert.isNotNull(element.shadowRoot!.querySelector('.unified-container'));
    assert.isNull(element.shadowRoot!.querySelector('.column-headers'));
    assert.isNotNull(
      element.shadowRoot!.querySelector('.unified-block.unchanged')
    );
    assert.isNotNull(
      element.shadowRoot!.querySelector('.unified-block.deleted')
    );
    assert.isNotNull(element.shadowRoot!.querySelector('.unified-block.added'));

    element.viewMode = DiffViewMode.SIDE_BY_SIDE;
    await element.updateComplete;
    assert.isNull(element.shadowRoot!.querySelector('.unified-container'));
    assert.isNotNull(element.shadowRoot!.querySelector('.diff-grid'));
  });

  test('collapses and expands proof bodies globally and per theorem', async () => {
    element.diff = sampleLeanDiff;
    await element.updateComplete;

    const globalToggle = element.shadowRoot!.querySelector<HTMLButtonElement>(
      '.proof-collapse-btn'
    );
    assert.isNotNull(globalToggle);
    assert.include(globalToggle.textContent, 'Collapse proofs');

    // Proof bodies are initially visible (in both left and right modified cells)
    assert.isNotNull(
      element.shadowRoot!.querySelector(
        '.diff-cell.modified-right .lean-body-section'
      )
    );

    // Collapse all proofs globally
    globalToggle.click();
    await element.updateComplete;
    assert.isTrue(element.collapseProofs);
    assert.include(globalToggle.textContent, 'Expand proofs');
    assert.isNull(
      element.shadowRoot!.querySelector(
        '.diff-cell.modified-right .lean-body-section'
      )
    );
    assert.isNotNull(
      element.shadowRoot!.querySelector('.lean-proof-changed-pill')
    );

    // Click per-theorem proof bar to re-expand just this theorem
    const rowProofBar = element.shadowRoot!.querySelector<HTMLButtonElement>(
      '.diff-cell.modified-right .lean-proof-bar'
    );
    assert.isNotNull(rowProofBar);
    rowProofBar.click();
    await element.updateComplete;
    assert.isNotNull(
      element.shadowRoot!.querySelector(
        '.diff-cell.modified-right .lean-body-section'
      )
    );
  });

  suite('comment threads rendering and creation', () => {
    test('renders file-level and block-level threads in side-by-side mode', async () => {
      const fileThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          line: undefined,
          message: 'Overall formalization looks rock solid!',
          patch_set: 1 as RevisionPatchSetNum,
          side: CommentSide.REVISION,
        },
      ]);
      const lineThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          line: 13,
          message: 'Nice elimination of sorry via Nat.eq_of_testBit_eq.',
          patch_set: 1 as RevisionPatchSetNum,
          side: CommentSide.REVISION,
        },
      ]);

      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.threads = [fileThread, lineThread];
      await element.updateComplete;

      const fileLevelContainer = element.shadowRoot!.querySelector(
        '.file-level-threads'
      );
      assert.isNotNull(fileLevelContainer);
      assert.equal(
        fileLevelContainer.querySelectorAll('gr-comment-thread').length,
        1
      );

      const blockThreads = element.shadowRoot!.querySelectorAll(
        '.diff-grid gr-comment-thread'
      );
      assert.equal(blockThreads.length, 1);
      assert.equal(blockThreads[0].getAttribute('line-num'), '13');
    });

    test('clicking a code line number creates a draft on that exact line', async () => {
      const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.loggedIn = true;
      await element.updateComplete;

      const line14Btn = element.shadowRoot!.querySelector<HTMLButtonElement>(
        '.diff-cell.modified-right .lean-code-line[data-line="14"] .lean-line-num'
      );
      assert.isNotNull(line14Btn);
      line14Btn.click();

      assert.isTrue(addNewDraftSpy.calledOnce);
      const draft: DraftInfo = addNewDraftSpy.firstCall.args[0];
      assert.equal(draft.path, 'Soundness.lean');
      assert.equal(draft.line, 14);
      assert.equal(draft.side, CommentSide.REVISION);
    });

    test('supports commenting on left (PARENT) side and in unified view mode', async () => {
      const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.loggedIn = true;
      await element.updateComplete;

      // Click line 13 on the LEFT (Base) modified cell (`  sorry`)
      const leftLine13Btn =
        element.shadowRoot!.querySelector<HTMLButtonElement>(
          '.diff-cell.modified-left .lean-code-line[data-line="13"] .lean-line-num'
        );
      assert.isNotNull(leftLine13Btn);
      leftLine13Btn.click();

      assert.isTrue(addNewDraftSpy.calledOnce);
      const leftDraft: DraftInfo = addNewDraftSpy.firstCall.args[0];
      assert.equal(leftDraft.line, 13);
      assert.equal(leftDraft.side, CommentSide.PARENT);

      // Switch to UNIFIED view and click a line number on the added block
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;

      const unifiedLine14Btn =
        element.shadowRoot!.querySelector<HTMLButtonElement>(
          '.unified-block.added .lean-code-line[data-line="14"] .lean-line-num'
        );
      assert.isNotNull(unifiedLine14Btn);
      unifiedLine14Btn.click();

      assert.isTrue(addNewDraftSpy.calledTwice);
      const unifiedDraft: DraftInfo = addNewDraftSpy.secondCall.args[0];
      assert.equal(unifiedDraft.line, 14);
      assert.equal(unifiedDraft.side, CommentSide.REVISION);
    });

    test('clicking hover Comment button creates a draft on the block start line', async () => {
      const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.loggedIn = true;
      await element.updateComplete;

      const addBtn = element.shadowRoot!.querySelector<HTMLButtonElement>(
        '.diff-cell.modified-right .add-comment-btn'
      );
      assert.isNotNull(addBtn);
      addBtn.click();

      assert.isTrue(addNewDraftSpy.calledOnce);
      const draft: DraftInfo = addNewDraftSpy.firstCall.args[0];
      assert.equal(draft.path, 'Soundness.lean');
      assert.equal(draft.line, 11);
    });

    test('pressing c while hovering a specific line comments on that exact line and ignores Ctrl+C', async () => {
      const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.loggedIn = true;
      await element.updateComplete;

      const line15 = element.shadowRoot!.querySelector<HTMLElement>(
        '.diff-cell.modified-right .lean-code-line[data-line="15"]'
      );
      assert.isNotNull(line15);
      line15.dispatchEvent(new MouseEvent('mouseenter', {bubbles: false}));

      // Ctrl+C must be ignored (copy shortcut)
      window.dispatchEvent(
        new KeyboardEvent('keydown', {key: 'c', ctrlKey: true, bubbles: true})
      );
      assert.isFalse(addNewDraftSpy.called);

      // Plain `c` creates a draft on hovered line 15
      window.dispatchEvent(
        new KeyboardEvent('keydown', {key: 'c', bubbles: true})
      );
      assert.isTrue(addNewDraftSpy.calledOnce);
      assert.equal(addNewDraftSpy.firstCall.args[0].line, 15);
    });

    test('selecting text inside a multi-line proof creates draft on exact selected line', async () => {
      const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.loggedIn = true;
      await element.updateComplete;

      const line14Text = element.shadowRoot!.querySelector<HTMLElement>(
        '.diff-cell.modified-right .lean-code-line[data-line="14"] .lean-code-text'
      )!;
      assert.isNotNull(line14Text);

      const range = document.createRange();
      range.selectNodeContents(line14Text);
      const sel = window.getSelection()!;
      sel.removeAllRanges();
      sel.addRange(range);
      document.dispatchEvent(new Event('selectionchange'));

      assert.isTrue(element.hasActiveSelection());
      element.createCommentFromSelectionOrHover();

      assert.isTrue(addNewDraftSpy.calledOnce);
      assert.equal(addNewDraftSpy.firstCall.args[0].line, 14);
      assert.equal(addNewDraftSpy.firstCall.args[0].side, CommentSide.REVISION);
    });

    test('fires show-auth-required when logged out user attempts to comment', async () => {
      const authSpy = sinon.spy();
      element.addEventListener('show-auth-required', authSpy);
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.loggedIn = false;
      await element.updateComplete;

      element.createComment(Side.RIGHT, 12);
      assert.isTrue(authSpy.calledOnce);
    });

    test('blocks commenting on EDIT patchset', async () => {
      const alertSpy = sinon.spy();
      element.addEventListener('show-alert', alertSpy);
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = {basePatchNum: PARENT, patchNum: EDIT};
      element.loggedIn = true;
      await element.updateComplete;

      element.createComment(Side.RIGHT, 12);
      assert.isTrue(alertSpy.calledOnce);
      assert.include(alertSpy.firstCall.args[0].detail.message, 'edit');
    });

    test('autoSaveDrafts flushes all rendered gr-comment-thread elements', async () => {
      const lineThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          line: 13,
          message: 'Check lemma',
          patch_set: 1 as RevisionPatchSetNum,
          side: CommentSide.REVISION,
        },
      ]);
      element.diff = sampleLeanDiff;
      element.path = 'Soundness.lean';
      element.patchRange = createPatchRange();
      element.threads = [lineThread];
      await element.updateComplete;

      const threadEl =
        element.shadowRoot!.querySelector<HTMLElement>('gr-comment-thread')!;
      assert.isNotNull(threadEl);
      // @ts-expect-error spying on GrCommentThread.autoSave
      const autoSaveSpy = sinon.spy(threadEl, 'autoSave');
      await element.autoSaveDrafts();
      assert.isTrue(autoSaveSpy.calledOnce);
    });
  });

  suite(
    'adversarial XSS, class-hijacking, hygiene, and interaction hardening',
    () => {
      test('end-to-end Shadow DOM audit blocks XSS across module docs, docstrings, banners, fields, and code diffs', async () => {
        element.diff = {
          ...createDiff(),
          meta_a: {name: 'Evil.lean', content_type: 'text/x-lean', lines: 20},
          meta_b: {name: 'Evil.lean', content_type: 'text/x-lean', lines: 24},
          content: [
            {
              a: [
                '/-!',
                '<script>window.__xss = 1</script>',
                '[js](javascript:alert(1)) [proto](//evil.com) [bs](/\\evil.com)',
                '-/',
                '-- ========================================================',
                '-- <img src=x onerror=alert(1)>',
                '-- ========================================================',
                'structure S where',
                '  x : Nat -- <svg onload=alert(1)>',
                'theorem t : True := by',
                '  -- <scr',
                '  trivial',
              ],
              b: [
                '/-!',
                '<svg onload="alert(1)"></svg>',
                '<iframe src="javascript:alert(1)"></iframe>',
                '<noscript><p title="</noscript><img src=x onerror=alert(1)>">',
                '![img](javascript:alert(1))',
                '[entity](&#106;avascript:alert(1)) [ctrl](java\tscript:alert(1))',
                '[docs](https://lean-lang.org/doc/reference/latest/)',
                '[rel](./Other.lean#L1)',
                '-/',
                '-- ========================================================',
                '-- <script>alert(2)</script> Banner',
                '-- ========================================================',
                '/-- <style>body{display:none}</style> <math><mtext></mtext><img src=x onerror=alert(1)></math> -/',
                'structure S where',
                '  x : Nat -- <img src=x onerror=alert(1)> [bad](javascript:alert(1))',
                'theorem t : True := by',
                '  -- <script>alert(3)</script>',
                '  trivial',
              ],
            },
          ],
        };
        await element.updateComplete;

        const grid = element.shadowRoot!.querySelector('.diff-grid')!;
        assert.isNotNull(grid);

        const forbiddenTags = [
          'script',
          'svg',
          'iframe',
          'img',
          'object',
          'embed',
          'math',
          'style',
        ];
        for (const tag of forbiddenTags) {
          assert.isNull(
            grid.querySelector(tag),
            `Forbidden <${tag}> rendered in Shadow DOM`
          );
        }

        for (const el of Array.from(grid.querySelectorAll('*'))) {
          for (const attr of Array.from(el.attributes)) {
            assert.isFalse(
              attr.name.toLowerCase().startsWith('on'),
              `Forbidden event handler attribute rendered: ${attr.name}`
            );
          }
        }

        const links = Array.from(grid.querySelectorAll('a'));
        assert.isAtLeast(links.length, 4);
        for (const a of links) {
          const href = a.getAttribute('href') ?? '';
          assert.isFalse(/^(javascript|data|vbscript|file):/i.test(href));
          assert.isFalse(href.startsWith('//'));
          assert.isFalse(href.includes('\\'));
          assert.equal(a.getAttribute('rel'), 'noopener noreferrer');
          if (href.startsWith('https://')) {
            assert.equal(a.getAttribute('target'), '_blank');
          }
        }
      });

      test('fenced code block CSS class injection cannot hijack #selection-action-box or diff-cell side resolution', async () => {
        const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
        element.diff = {
          ...createDiff(),
          meta_a: {name: 'Hijack.lean', content_type: 'text/x-lean', lines: 6},
          meta_b: {name: 'Hijack.lean', content_type: 'text/x-lean', lines: 6},
          content: [
            {
              a: [
                '/-!',
                '```lean selection-action-box diff-cell hygiene-pill verified',
                'base_payload',
                '```',
                '-/',
              ],
              b: [
                '/-!',
                '```lean selection-action-box diff-cell hygiene-pill verified',
                'rev_payload',
                '```',
                '-/',
              ],
            },
          ],
        };
        element.path = 'Hijack.lean';
        element.patchRange = createPatchRange();
        element.loggedIn = true;
        await element.updateComplete;

        // `#selection-action-box` must resolve to the real popover div, never a <code> element
        const actionBox = element.shadowRoot!.querySelector(
          '#selection-action-box'
        );
        assert.isNotNull(actionBox);
        assert.equal(actionBox.tagName, 'DIV');

        const codeBlocks = Array.from(
          element.shadowRoot!.querySelectorAll('.lean-module-doc pre code')
        );
        assert.isAtLeast(codeBlocks.length, 2);
        for (const codeEl of codeBlocks) {
          assert.equal(codeEl.className, 'language-lean');
          assert.isFalse(codeEl.classList.contains('selection-action-box'));
          assert.isFalse(codeEl.classList.contains('diff-cell'));
          assert.isFalse(codeEl.classList.contains('hygiene-pill'));
        }

        // Selecting text inside the Base (left) fenced code block must still attribute to Side.LEFT (PARENT)
        const leftCodeEl = element.shadowRoot!.querySelector<HTMLElement>(
          '.diff-cell[data-side="left"] pre code'
        )!;
        assert.isNotNull(leftCodeEl);
        const range = document.createRange();
        range.selectNodeContents(leftCodeEl);
        const sel = window.getSelection()!;
        sel.removeAllRanges();
        sel.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));

        assert.isTrue(element.hasActiveSelection());
        element.createCommentFromSelectionOrHover();
        assert.isTrue(addNewDraftSpy.calledOnce);
        assert.equal(addNewDraftSpy.firstCall.args[0].side, CommentSide.PARENT);
      });

      test('suppresses green verified badge for every soundness hygiene smuggling vector', async () => {
        const smugglingPayloads = [
          ['theorem t : 1 = 1 := by native_decide'],
          ['partial def loop (n : Nat) : Nat := loop n'],
          ['theorem t : False := sorryAx False false'],
          ['theorem t : False := «sorryAx» False false'],
          ['theorem t : True := Lean.ofReduceBool true rfl'],
          ['/- Copyright -/ axiom backdoor : False'],
          ['open Nat in theorem sneaky : False := by sorry'],
          ['def s := s!"{\'"\'}"', 'theorem sneaky : False := by sorry'],
          ['macro "cheat" : tactic => `(tactic| sorry)'],
        ];

        for (const lines of smugglingPayloads) {
          element.diff = {
            ...createDiff(),
            content: [{b: lines}],
          };
          await element.updateComplete;
          assert.isNull(
            element.shadowRoot!.querySelector('.hygiene-pill.verified'),
            `Green verified badge leaked for payload: ${lines.join('\\n')}`
          );
        }
      });

      test('keydown c only fires once when multiple viewers are mounted and ignores repeat/defaultPrevented/dialog/input', async () => {
        const second = await fixture<GrDiffLeanViewer>(
          html`<gr-diff-lean-viewer></gr-diff-lean-viewer>`
        );
        const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
        for (const v of [element, second]) {
          v.diff = sampleLeanDiff;
          v.path = 'Soundness.lean';
          v.patchRange = createPatchRange();
          v.loggedIn = true;
          await v.updateComplete;
        }

        const line15 = element.shadowRoot!.querySelector<HTMLElement>(
          '.diff-cell.modified-right .lean-code-line[data-line="15"]'
        )!;
        line15.dispatchEvent(new MouseEvent('mouseenter', {bubbles: false}));
        assert.isTrue(element.hasHoverTarget());
        assert.isFalse(second.hasHoverTarget());

        // Repeat and defaultPrevented events must be ignored
        window.dispatchEvent(
          new KeyboardEvent('keydown', {key: 'c', repeat: true, bubbles: true})
        );
        const prevented = new KeyboardEvent('keydown', {
          key: 'c',
          cancelable: true,
          bubbles: true,
        });
        prevented.preventDefault();
        window.dispatchEvent(prevented);

        // Events originating inside a <dialog> or <input> must be ignored
        const dialog = document.createElement('dialog');
        document.body.appendChild(dialog);
        try {
          dialog.dispatchEvent(
            new KeyboardEvent('keydown', {key: 'c', bubbles: true})
          );
        } finally {
          dialog.remove();
        }
        assert.isFalse(addNewDraftSpy.called);

        // Plain c fires exactly ONCE on the hovered viewer
        window.dispatchEvent(
          new KeyboardEvent('keydown', {key: 'c', bubbles: true})
        );
        assert.isTrue(addNewDraftSpy.calledOnce);
        assert.equal(addNewDraftSpy.firstCall.args[0].line, 15);
      });

      test('clears stale selection on diff/collapse changes, ignores selections in thread containers, and resolves end line', async () => {
        const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
        const lineThread: CommentThread = createCommentThread([
          {
            ...createComment(),
            line: 13,
            message: 'Existing comment thread text',
            patch_set: 1 as RevisionPatchSetNum,
            side: CommentSide.REVISION,
          },
        ]);
        element.diff = sampleLeanDiff;
        element.path = 'Soundness.lean';
        element.patchRange = createPatchRange();
        element.threads = [lineThread];
        element.loggedIn = true;
        await element.updateComplete;

        // Selection inside .threads-container must be ignored
        const threadsContainer =
          element.shadowRoot!.querySelector<HTMLElement>('.threads-container')!;
        assert.isNotNull(threadsContainer);
        const threadRange = document.createRange();
        threadRange.selectNodeContents(threadsContainer);
        const sel = window.getSelection()!;
        sel.removeAllRanges();
        sel.addRange(threadRange);
        document.dispatchEvent(new Event('selectionchange'));
        assert.isFalse(element.hasActiveSelection());

        // Multi-line selection spanning lines 13..15 resolves to end line 15
        const line13 = element.shadowRoot!.querySelector<HTMLElement>(
          '.diff-cell.modified-right .lean-code-line[data-line="13"] .lean-code-text'
        )!;
        const line15 = element.shadowRoot!.querySelector<HTMLElement>(
          '.diff-cell.modified-right .lean-code-line[data-line="15"] .lean-code-text'
        )!;
        const multiRange = document.createRange();
        multiRange.setStart(line13, 0);
        multiRange.setEnd(line15, line15.childNodes.length);
        sel.removeAllRanges();
        sel.addRange(multiRange);
        document.dispatchEvent(new Event('selectionchange'));
        assert.isTrue(element.hasActiveSelection());

        element.createCommentFromSelectionOrHover();
        assert.isTrue(addNewDraftSpy.calledOnce);
        assert.equal(addNewDraftSpy.firstCall.args[0].line, 15);

        // Re-select then collapse proofs → selection must be cleared
        sel.removeAllRanges();
        sel.addRange(multiRange);
        document.dispatchEvent(new Event('selectionchange'));
        assert.isTrue(element.hasActiveSelection());

        element.collapseProofs = true;
        await element.updateComplete;
        assert.isFalse(element.hasActiveSelection());
      });

      test('passes basePath for renamed files and falls back to Side.LEFT for deleted files in unified mode', async () => {
        const addNewDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
        const getThreadsStub = sinon.stub().returns([]);
        // @ts-expect-error injecting minimal ChangeComments double
        element.changeComments = {getThreadsBySideForFile: getThreadsStub};
        element.patchRange = createPatchRange();
        element.path = 'NewSoundness.lean';
        element.diff = {
          ...createDiff(),
          meta_a: {
            name: 'OldSoundness.lean',
            content_type: 'text/x-lean',
            lines: 2,
          },
          meta_b: {
            name: 'NewSoundness.lean',
            content_type: 'text/x-lean',
            lines: 2,
          },
          content: [{ab: ['theorem t : True := by', '  trivial']}],
        };
        await element.updateComplete;

        assert.isTrue(getThreadsStub.called);
        const lastCallArgs = getThreadsStub.lastCall.args[0];
        assert.equal(lastCallArgs.path, 'NewSoundness.lean');
        assert.equal(lastCallArgs.basePath, 'OldSoundness.lean');

        // Deleted file in unified mode: fallback comment goes to PARENT (Side.LEFT)
        element.diff = {
          ...createDiff(),
          meta_a: {name: 'Deleted.lean', content_type: 'text/x-lean', lines: 2},
          meta_b: undefined,
          content: [{a: ['theorem removed : True := by', '  trivial']}],
        };
        element.path = 'Deleted.lean';
        element.viewMode = DiffViewMode.UNIFIED;
        element.loggedIn = true;
        await element.updateComplete;

        element.createCommentFromSelectionOrHover();
        assert.isTrue(addNewDraftSpy.calledOnce);
        assert.equal(addNewDraftSpy.firstCall.args[0].side, CommentSide.PARENT);
        assert.equal(addNewDraftSpy.firstCall.args[0].line, 1);
      });
    }
  );
});
