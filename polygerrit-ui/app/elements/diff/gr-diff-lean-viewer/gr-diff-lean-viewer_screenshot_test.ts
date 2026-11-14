/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-lean-viewer';
import {GrDiffLeanViewer} from './gr-diff-lean-viewer';
import {fixture, html} from '@open-wc/testing';
// Until https://github.com/modernweb-dev/web/issues/2804 is fixed
// @ts-expect-error
import {visualDiff} from '@web/test-runner-visual-regression';
import {visualDiffDarkTheme} from '../../../test/test-utils';
import {
  createAccountWithIdNameAndEmail,
  createComment,
  createCommentThread,
  createDiff,
  createPatchRange,
} from '../../../test/test-data-generators';
import {DiffInfo, DiffViewMode} from '../../../api/diff';
import {CommentSide} from '../../../constants/constants';
import {
  CommentThread,
  RevisionPatchSetNum,
  Timestamp,
} from '../../../types/common';

suite('gr-diff-lean-viewer screenshot tests', () => {
  let element: GrDiffLeanViewer;

  const latticeModifiedDiff: DiffInfo = {
    ...createDiff(),
    meta_a: {
      name: 'formal/BoundedLattice.lean',
      content_type: 'text/x-lean',
      lines: 34,
    },
    meta_b: {
      name: 'formal/BoundedLattice.lean',
      content_type: 'text/x-lean',
      lines: 45,
    },
    content: [
      {
        ab: [
          'import Init',
          '',
          '/-!',
          '# Mechanized Soundness Proof of Bounded Lattice Engine',
          '',
        ],
      },
      {
        a: [
          'This Lean 4 module mechanizes the lattice height (`H = 38`) and monotonicity of `BoundedLattice`.',
        ],
        b: [
          'This self-contained Lean 4 module (zero `sorry` / `axiom` declarations) mechanizes the packed `CellState` bounded join-semilattice (`H = 54`) and monotonicity of **`BoundedLattice`**.',
        ],
      },
      {
        ab: [
          '-/',
          '',
          'namespace BoundedLattice',
          '',
          '-- ============================================================================',
          '-- Section 1: Packed `CellState` Bounded Join-Semilattice (`L`)',
          '-- ============================================================================',
          '',
          '/-- Single-cell state in `BoundedLattice` packed across bits `[19:0]` of a `UInt32`. -/',
          'structure CellState where',
          '  level    : Nat   -- `[2:0]` (`0..7`, `7 = MAX_LEVEL` is bottom)',
        ],
      },
      {
        a: ['  weight   : Nat   -- `[6:3]` (`0..15`, `15 = MAX_WEIGHT`)'],
        b: [
          '  weight   : Nat   -- `[7:3]` (`0..31`, `31 = MAX_WEIGHT` is bottom)',
        ],
      },
      {
        ab: [
          '  priority : Nat   -- `[10:8]` (`0..7`, `7 = MAX_PRIORITY` is top)',
        ],
      },
      {
        b: ['  isPinned : Bool  -- `[11]` (`false` is bottom, `true` is top)'],
      },
      {
        ab: [
          '  flagBits : Nat   -- `[19:12]` (`0..0xff`, `0` is bottom)',
          '  deriving DecidableEq, Repr',
          '',
          '/-- Bitwise subset characterization via `Nat.testBit`. -/',
          'theorem maskSub_iff_testBit (a b : Nat) :',
          '    maskSub a b ↔ ∀ i : Nat, a.testBit i = true → b.testBit i = true := by',
        ],
      },
      {
        a: ['  sorry'],
        b: [
          '  unfold maskSub',
          '  constructor',
          '  · intro h i ha',
          '    have htb : (a &&& b).testBit i = a.testBit i := by rw [h]',
          '    simp [ha] at htb',
          '    exact htb',
          '  · intro h',
          '    apply Nat.eq_of_testBit_eq',
          '    intro i',
          '    simp only [Nat.testBit_and]',
          '    cases ha : a.testBit i with',
          '    | false => simp',
          '    | true => exact h i ha',
        ],
      },
    ],
  };

  const reviewerThread: CommentThread = createCommentThread([
    {
      ...createComment(),
      author: createAccountWithIdNameAndEmail(2),
      updated: '2026-11-13 18:30:00.000000000' as Timestamp,
      line: 25,
      message:
        'Replacing `sorry` with `Nat.eq_of_testBit_eq` + `cases ha` is super clean and kernel-verifies instantly.',
      patch_set: 1 as RevisionPatchSetNum,
      side: CommentSide.REVISION,
      unresolved: false,
    },
  ]);

  setup(async () => {
    element = await fixture<GrDiffLeanViewer>(
      html`<gr-diff-lean-viewer
        style="display: block; width: 1280px;"
        .diff=${latticeModifiedDiff}
        .path=${'formal/BoundedLattice.lean'}
        .patchRange=${createPatchRange()}
        .threads=${[reviewerThread]}
      ></gr-diff-lean-viewer>`
    );
    await element.updateComplete;
    await document.fonts?.ready;
  });

  test('side-by-side rich Lean 4 proof diff with structure table, sorry elimination, and inline comment', async () => {
    await visualDiff(element, 'gr-diff-lean-viewer-proof-diff');
    await visualDiffDarkTheme(element, 'gr-diff-lean-viewer-proof-diff');
  });

  test('unified added Lean 4 file excerpt from BoundedLattice.lean', async () => {
    const addedLatticeDiff: DiffInfo = {
      ...createDiff(),
      meta_b: {
        name: 'formal/BoundedLattice.lean',
        content_type: 'text/x-lean',
        lines: 34,
      },
      content: [
        {
          b: [
            'import Init',
            '',
            '/-!',
            '# Mechanized Soundness Proof of a Bounded Join-Semilattice Engine',
            '',
            '1. **Packed `CellState` Bounded Join-Semilattice (`Section 1`)**: models the 5-coordinate cell state (`H = 54`) and proves partial-order and join-semilattice laws.',
            '2. **Monotone Weight Table (`Section 2`)**: kernel-checked via `decide` and `omega`.',
            '-/',
            '',
            'namespace BoundedLattice',
            '',
            '-- ============================================================================',
            '-- Section 1: Packed `CellState` Bounded Join-Semilattice (`L`)',
            '-- ============================================================================',
            '',
            'def MAX_LEVEL : Nat := 7',
            'def MAX_WEIGHT : Nat := 31',
            'def MAX_HEIGHT : Nat := 54',
            '',
            '/-- Single-cell state in `BoundedLattice` packed across bits `[19:0]` of a `UInt32`. -/',
            'structure CellState where',
            '  level    : Nat   -- `[2:0]`   (`0..7`, `7 = MAX_LEVEL` is bottom, `0 = TOP_LEVEL` is top)',
            '  weight   : Nat   -- `[7:3]`   (`0..31`, `31 = MAX_WEIGHT` is bottom, `0 = MIN_WEIGHT` is top)',
            '  priority : Nat   -- `[10:8]`  (`0..7`, `0 = MIN_PRIORITY` is bottom, `7 = MAX_PRIORITY` is top)',
            '  isPinned : Bool  -- `[11]`    (`false` is bottom, `true` is top)',
            '  flagBits : Nat   -- `[19:12]` (`0..0xff`, `0` is bottom, `0xff` is top)',
            '  deriving DecidableEq, Repr',
            '',
            '/-- Every `CellState` has lattice height at most `MAX_HEIGHT` (`54`). -/',
            'theorem height_le_bound (s : CellState) : s.height ≤ MAX_HEIGHT := by',
            '  unfold CellState.height MAX_HEIGHT MAX_LEVEL MAX_WEIGHT MAX_PRIORITY',
            '  have h1 : 7 - Nat.min s.level 7 ≤ 7 := Nat.sub_le _ _',
            '  have h2 : 31 - Nat.min s.weight 31 ≤ 31 := Nat.sub_le _ _',
            '  have h3 : Nat.min s.priority 7 ≤ 7 := Nat.min_le_right _ _',
            '  have h5 : bitCount 8 s.flagBits ≤ 8 := bitCount_le 8 s.flagBits',
            '  cases s.isPinned <;> simp <;> omega',
          ],
        },
      ],
    };

    element.diff = addedLatticeDiff;
    element.viewMode = DiffViewMode.UNIFIED;
    element.threads = [
      createCommentThread([
        {
          ...createComment(),
          author: createAccountWithIdNameAndEmail(2),
          updated: '2026-11-13 18:35:00.000000000' as Timestamp,
          line: 30,
          message:
            'Love that `height_le_bound` closes directly with `omega` after bounding each coordinate.',
          patch_set: 1 as RevisionPatchSetNum,
          side: CommentSide.REVISION,
        },
      ]),
    ];
    await element.updateComplete;
    await document.fonts?.ready;

    await visualDiff(element, 'gr-diff-lean-viewer-unified');
    await visualDiffDarkTheme(element, 'gr-diff-lean-viewer-unified');
  });

  test('collapsed proofs specification view with soundness hygiene alerts', async () => {
    element.diff = {
      ...createDiff(),
      meta_a: {name: 'Spec.lean', content_type: 'text/x-lean', lines: 20},
      meta_b: {name: 'Spec.lean', content_type: 'text/x-lean', lines: 25},
      content: [
        {
          ab: [
            '/-- Monotonicity of the lookup `weightTable` function. -/',
            'theorem weightTable_monotone (c x y : Nat) (h : x ≤ y) :',
            '    weightTable c x ≤ weightTable c y := by',
            '  decide',
            '',
            '/-- Unreachable subgraph vertices remain at lattice bottom under fixpoint iteration. -/',
            'theorem unreachable_fixpoint_bot (G : DirectedGraph n) (S : Fin n → Bool) :',
            '    Unreachable G S → ∀ v, S v = true → lfp G v = CellState.bot := by',
          ],
        },
        {
          a: ['  intro hunreach v hv', '  sorry'],
          b: [
            '  intro hunreach v hv',
            '  apply iterStep_preserves_unreachable G S hunreach',
            '  exact hv',
          ],
        },
        {
          b: [
            '',
            '/-- Draft lemma still awaiting mechanization. -/',
            'theorem incremental_fixpoint_sound (G : DirectedGraph n) :',
            '    fastFixpoint G = lfp G := by',
            '  sorry',
            '',
            'axiom oracle_bound : ∀ n : Nat, n ≤ MAX_HEIGHT',
          ],
        },
      ],
    };
    element.threads = [];
    element.viewMode = DiffViewMode.SIDE_BY_SIDE;
    element.collapseProofs = true;
    await element.updateComplete;
    await document.fonts?.ready;

    await visualDiff(element, 'gr-diff-lean-viewer-collapsed-proofs');
    await visualDiffDarkTheme(element, 'gr-diff-lean-viewer-collapsed-proofs');
  });

  test('official Lean 4 language constructs (extends, inductive, instance, mutual, termination_by, calc, raw strings)', async () => {
    element.diff = {
      ...createDiff(),
      meta_a: {name: 'LangRef.lean', content_type: 'text/x-lean', lines: 28},
      meta_b: {name: 'LangRef.lean', content_type: 'text/x-lean', lines: 42},
      content: [
        {
          ab: [
            'module',
            '',
            'public import Init.Core',
            '',
            'structure LabeledPoint extends Point where',
            '  label : String := r#"origin "default""# -- raw string literal',
            "  delim : Char := '\"' -- char literal containing double quote",
            'deriving DecidableEq, Repr',
            '',
            'inductive Direction where',
            '  | north | south | east | west -- inline enum constructors',
            'deriving Repr, BEq',
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
            'instance (priority := high) : Add Point where',
            '  add p q := ⟨p.x + q.x, p.y + q.y⟩',
            '',
          ],
        },
        {
          a: [
            'def ackermann (m n : Nat) : Nat :=',
            '  match m, n with',
            '  | 0, y => y + 1',
            '  | x + 1, 0 => ackermann x 1',
            '  | x + 1, y + 1 => ackermann x (ackermann (x + 1) y)',
            'termination_by (m, n)',
            'decreasing_by',
            '  sorry',
          ],
          b: [
            'def ackermann (m n : Nat) : Nat :=',
            '  match m, n with',
            '  | 0, y => y + 1',
            '  | x + 1, 0 => ackermann x 1',
            '  | x + 1, y + 1 => ackermann x (ackermann (x + 1) y)',
            'termination_by (m, n)',
            'decreasing_by',
            '  all_goals simp_wf',
            '  all_goals omega',
            '',
            '/-- Equational chain + targeted `conv` rewrite from the Lean 4 reference. -/',
            'theorem add_comm_rhs (a b c : Nat) : a + (b + c) = a + (c + b) := by',
            '  conv =>',
            '    lhs',
            '    arg 2',
            '    rw [Nat.add_comm]',
          ],
        },
      ],
    };
    element.threads = [];
    element.viewMode = DiffViewMode.SIDE_BY_SIDE;
    element.collapseProofs = false;
    await element.updateComplete;
    await document.fonts?.ready;

    await visualDiff(element, 'gr-diff-lean-viewer-lean-lang-ref');
    await visualDiffDarkTheme(element, 'gr-diff-lean-viewer-lean-lang-ref');
  });
});
