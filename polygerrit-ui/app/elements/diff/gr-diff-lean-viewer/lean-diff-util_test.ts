/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {
  alignLeanBlocks,
  alignLeanCodeLines,
  computeLeanSummary,
  formatLineRange,
  highlightLeanLine,
  parseLeanBlocks,
  renderMarkdownContent,
  stripLeanCommentsAndStrings,
  tokenizeLeanDiffWords,
  tokenizeLeanLine,
} from './lean-diff-util';

suite('lean-diff-util tests', () => {
  suite('stripLeanCommentsAndStrings()', () => {
    test('strips line comments, deeply nested block comments, and string literals', () => {
      const code = [
        '-- sorry in line comment',
        '/- outer /- middle /- inner sorry -/ still nested -/ still comment -/',
        'def msg : String := "do not trigger \\"sorry\\" or admit"',
        'def multi : String := "line 1\n  sorry on line 2\n  admit on line 3"',
        'def interp (x : Nat) : String := s!"value {x} sorry"',
        'theorem real : True := by trivial',
      ].join('\n');

      const clean = stripLeanCommentsAndStrings(code);
      assert.isFalse(/\bsorry\b/.test(clean));
      assert.isFalse(/\badmit\b/.test(clean));
      assert.include(clean, 'def msg : String :=');
      assert.include(clean, 'theorem real : True := by trivial');
      // Preserves exact line count so line-number mappings never drift
      assert.equal(clean.split('\n').length, code.split('\n').length);
    });

    test('strips raw string literals (r"...", r#"..."#, r##"..."##) including multi-line raw strings', () => {
      const code = [
        'def r0 : String := r"backslash \\ sorry -- not a comment"',
        'def r1 : String := r#"quoted "sorry" and native_decide"#',
        'def r2 : String := r##"nested #"admit"# inside\n  still raw sorry"##',
        'theorem after_raw : True := by trivial',
      ].join('\n');

      const clean = stripLeanCommentsAndStrings(code);
      assert.isFalse(/\bsorry\b/.test(clean));
      assert.isFalse(/\badmit\b/.test(clean));
      assert.isFalse(/\bnative_decide\b/.test(clean));
      assert.include(clean, 'theorem after_raw : True := by trivial');
    });

    test('handles char literals including double-quote char without swallowing subsequent code', () => {
      const code = [
        "def quoteChar : Char := '\"'",
        "def singleQuote : Char := '\\''",
        "def hexChar : Char := '\\x41'",
        "def unicodeChar : Char := '\\u03bb'",
        'theorem real_sorry : False := by sorry',
      ].join('\n');

      const clean = stripLeanCommentsAndStrings(code);
      // `'"'` must NOT open a string that swallows `theorem real_sorry`!
      assert.isTrue(/\bsorry\b/.test(clean));
      assert.include(clean, 'theorem real_sorry : False := by sorry');
    });

    test('strips guillemet-escaped identifiers so «sorry» does not trigger hygiene false positives', () => {
      const code = [
        'def «sorry» : Nat := 1',
        'def «admit» : Nat := 2',
        'def «native_decide» : Nat := 3',
        'theorem «1 + 1 = 2» : 1 + 1 = 2 := rfl',
      ].join('\n');

      const clean = stripLeanCommentsAndStrings(code);
      assert.isFalse(/\bsorry\b/.test(clean));
      assert.isFalse(/\badmit\b/.test(clean));
      assert.isFalse(/\bnative_decide\b/.test(clean));
      assert.include(clean, ':= rfl');
    });

    test('preserves sorry, admit, and native_decide in executable code', () => {
      const code = [
        'theorem t1 : False := by sorry',
        'theorem t2 : False := by admit',
        'theorem t3 : 1 + 1 = 2 := by native_decide',
      ].join('\n');
      const clean = stripLeanCommentsAndStrings(code);
      assert.isTrue(/\bsorry\b/.test(clean));
      assert.isTrue(/\badmit\b/.test(clean));
      assert.isTrue(/\bnative_decide\b/.test(clean));
    });
  });

  suite('parseLeanBlocks()', () => {
    test('returns empty array for empty source', () => {
      assert.deepEqual(parseLeanBlocks(''), []);
    });

    test('parses module_doc, license header, imports, directive, section_banner, structure, def, theorem', () => {
      const source = [
        'import Init',
        '',
        '/-',
        'Copyright (C) 2026 Example Contributors',
        'Licensed under the Apache License, Version 2.0',
        '-/',
        '',
        '/-!',
        '# Soundness Proof',
        'Zero `sorry` or `axiom` declarations.',
        '-/',
        '',
        'namespace BoundedLattice',
        '',
        '-- ============================================',
        '-- Section 0: Helpers on `Nat`',
        '-- ============================================',
        '',
        '/-- Bitwise subset order. -/',
        '@[simp]',
        'def maskSub (a b : Nat) : Prop :=',
        '  (a &&& b) = a',
        '',
        'def MAX_LEVEL : Nat := 7',
        '',
        '/-- Single-cell state. -/',
        'structure CellState where',
        '  level : Nat -- `[2:0]` bottom = 7',
        '  isPinned : Bool := false -- `[11]`',
        '  deriving DecidableEq, Repr',
        '',
        'theorem maskSub_refl (a : Nat) : maskSub a a := by',
        '  unfold maskSub',
        '  simp',
        '',
        'end BoundedLattice',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      const types = blocks.map(b => b.type);
      assert.deepEqual(types, [
        'imports',
        'comment',
        'module_doc',
        'directive',
        'section_banner',
        'declaration',
        'declaration',
        'declaration',
        'declaration',
        'directive',
      ]);

      // Imports
      assert.equal(blocks[0].startLine, 1);
      assert.equal(blocks[0].endLine, 1);
      assert.equal(blocks[0].importLines?.length, 1);

      // License comment
      assert.isTrue(blocks[1].isLicenseHeader);
      assert.equal(blocks[1].startLine, 3);
      assert.equal(blocks[1].endLine, 6);

      // Module doc
      assert.include(blocks[2].markdownContent!, '# Soundness Proof');
      assert.equal(blocks[2].startLine, 8);
      assert.equal(blocks[2].endLine, 11);

      // Namespace directive
      assert.equal(blocks[3].directiveKind, 'namespace');

      // Section banner
      assert.equal(blocks[4].markdownContent, 'Section 0: Helpers on `Nat`');

      // Declaration: maskSub with attached doc & @[simp]
      const maskSub = blocks[5].decl!;
      assert.equal(maskSub.kind, 'def');
      assert.equal(maskSub.name, 'maskSub');
      assert.equal(maskSub.docComment, 'Bitwise subset order.');
      assert.deepEqual(maskSub.attributes, ['@[simp]']);
      assert.isFalse(maskSub.isCompactDef);
      assert.isFalse(maskSub.hygiene.hasSorry);

      // Compact 1-line def
      const maxLevel = blocks[6].decl!;
      assert.equal(maxLevel.kind, 'def');
      assert.equal(maxLevel.name, 'MAX_LEVEL');
      assert.isTrue(maxLevel.isCompactDef);

      // Structure with fields & deriving
      const cellState = blocks[7].decl!;
      assert.equal(cellState.kind, 'structure');
      assert.equal(cellState.name, 'CellState');
      assert.equal(cellState.fields?.length, 2);
      assert.equal(cellState.fields![0].name, 'level');
      assert.equal(cellState.fields![0].typeText, 'Nat');
      assert.equal(cellState.fields![0].comment, '`[2:0]` bottom = 7');
      assert.equal(cellState.fields![1].name, 'isPinned');
      assert.equal(cellState.fields![1].typeText, 'Bool');
      assert.equal(cellState.fields![1].defaultValue, 'false');
      assert.deepEqual(cellState.derivingClause, ['DecidableEq', 'Repr']);

      // Theorem with tactic proof
      const maskSubRefl = blocks[8].decl!;
      assert.equal(maskSubRefl.kind, 'theorem');
      assert.equal(maskSubRefl.name, 'maskSub_refl');
      assert.isTrue(maskSubRefl.isTacticProof);
      assert.equal(maskSubRefl.proofStrategy, 'tactic');
      assert.equal(maskSubRefl.signatureLines.length, 1);
      assert.equal(maskSubRefl.bodyLines.length, 2);
      assert.isFalse(maskSubRefl.hygiene.hasSorry);
    });

    test('parses equation-compiler definitions without := and inductive constructors', () => {
      const source = [
        'def bitCount : Nat → Nat → Nat',
        '  | 0, _        => 0',
        '  | k + 1, mask => bitCount k mask + (if mask.testBit k then 1 else 0)',
        '',
        'inductive Verdict where',
        '  | ok : Nat → Verdict -- success',
        '  | err : String → Verdict -- failure',
        '  deriving Repr',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 2);

      const bitCount = blocks[0].decl!;
      assert.equal(bitCount.name, 'bitCount');
      assert.equal(bitCount.signatureLines.length, 1);
      assert.equal(bitCount.bodyLines.length, 2);

      const verdict = blocks[1].decl!;
      assert.equal(verdict.kind, 'inductive');
      assert.equal(verdict.constructors?.length, 2);
      assert.equal(verdict.constructors![0].name, 'ok');
      assert.equal(verdict.constructors![0].comment, 'success');
      assert.deepEqual(verdict.derivingClause, ['Repr']);
    });

    test('detects hygiene alerts accurately without false positives in comments or strings', () => {
      const source = [
        '/-- Proves with zero `sorry`, `admit`, or `native_decide`. -/',
        'theorem clean_thm : 1 + 1 = 2 := by decide',
        '',
        'theorem has_sorry : False := by',
        '  sorry',
        '',
        'axiom external_oracle : Nat → Bool',
        '',
        'partial def loop (n : Nat) : Nat := loop (n + 1)',
        '',
        'theorem uses_native : 2 + 2 = 4 := by native_decide',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 5);

      assert.isFalse(blocks[0].decl!.hygiene.hasSorry);
      assert.isFalse(blocks[0].decl!.hygiene.hasAdmit);
      assert.isFalse(blocks[0].decl!.hygiene.hasNativeDecide);
      assert.equal(blocks[0].decl!.proofStrategy, 'decide');

      assert.isTrue(blocks[1].decl!.hygiene.hasSorry);
      assert.isTrue(blocks[2].decl!.hygiene.isAxiom);
      assert.isTrue(blocks[3].decl!.hygiene.isPartial);
      assert.isTrue(blocks[4].decl!.hygiene.hasNativeDecide);
    });

    test('parses Lean 4 module system headers, public/meta/private imports, and modifier directives', () => {
      const source = [
        'module',
        '',
        'public import Init.Core',
        'meta import Lean.Meta',
        'private import Init.Data.Nat.Basic',
        'import all Init.Data.List',
        '',
        'noncomputable section',
        'public section',
        'scoped notation "⟪" a ", " b "⟫" => Prod.mk a b',
        'universe u v',
        'variable {α : Type u}',
        '#guard 1 + 1 == 2',
        'end',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks[0].type, 'directive');
      assert.equal(blocks[0].directiveKind, 'module');

      assert.equal(blocks[1].type, 'imports');
      assert.equal(blocks[1].importLines?.length, 4);
      assert.equal(blocks[1].startLine, 3);
      assert.equal(blocks[1].endLine, 6);

      const dirKinds = blocks.slice(2).map(b => b.directiveKind);
      assert.deepEqual(dirKinds, [
        'section',
        'section',
        'notation',
        'universe',
        'variable',
        '#guard',
        'end',
      ]);
    });

    test('parses universe-polymorphic names, guillemet names, stacked modifiers, and nested-bracket attributes', () => {
      const source = [
        '@[simp [Nat.add_comm], inline]',
        'public noncomputable def id.{u} {α : Sort u} (a : α) : α := a',
        '',
        '@[deprecated old_fn (since := "2026-01-01")] protected theorem Nat.my_id (n : Nat) : n = n := rfl',
        '',
        'theorem «1 + 1 = 2» : 1 + 1 = 2 := by decide',
        '',
        'unsafe def peekPtr (x : Nat) : Nat := x',
        '',
        'opaque secretKey : Nat := 42',
        '',
        'example (p q : Prop) (hp : p) : p ∨ q := Or.inl hp',
        '',
        'def nativeOneLiner : Bool := by native_decide',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 7);

      const idDecl = blocks[0].decl!;
      assert.equal(idDecl.kind, 'def');
      assert.equal(idDecl.name, 'id');
      assert.deepEqual(idDecl.modifiers, ['public', 'noncomputable']);
      assert.deepEqual(idDecl.attributes, ['@[simp [Nat.add_comm], inline]']);

      const natDecl = blocks[1].decl!;
      assert.equal(natDecl.kind, 'theorem');
      assert.equal(natDecl.name, 'Nat.my_id');
      assert.deepEqual(natDecl.modifiers, ['protected']);
      assert.deepEqual(natDecl.attributes, [
        '@[deprecated old_fn (since := "2026-01-01")]',
      ]);
      assert.equal(natDecl.proofStrategy, 'term');

      const guillemetDecl = blocks[2].decl!;
      assert.equal(guillemetDecl.name, '«1 + 1 = 2»');
      assert.equal(guillemetDecl.proofStrategy, 'decide');

      const unsafeDecl = blocks[3].decl!;
      assert.isTrue(unsafeDecl.hygiene.isUnsafe);
      assert.isFalse(unsafeDecl.isCompactDef);

      const opaqueDecl = blocks[4].decl!;
      assert.equal(opaqueDecl.kind, 'opaque');
      assert.equal(opaqueDecl.name, 'secretKey');

      const exDecl = blocks[5].decl!;
      assert.equal(exDecl.kind, 'example');
      assert.equal(exDecl.name, '');

      const nativeDecl = blocks[6].decl!;
      assert.isTrue(nativeDecl.hygiene.hasNativeDecide);
      assert.isFalse(nativeDecl.isCompactDef);
    });

    test('parses structure extends, custom constructor fallback, class, inline inductive enums, coinductive, and instance where', () => {
      const source = [
        'structure Child extends Parent1, Parent2 where',
        '  label : String := "-- not a comment" -- field note',
        "  quote : Char := '\"' -- double-quote literal",
        'deriving DecidableEq, Repr, Hashable',
        '',
        'structure Point (α : Type) where',
        '  mk ::',
        '  x : α',
        '  y : α',
        'deriving Repr',
        '',
        'class Addable (α : Type) where',
        '  add : α → α → α',
        '',
        'inductive Direction where',
        '  | north | south | east | west -- cardinal directions',
        'deriving Repr, BEq',
        '',
        'inductive BoolMask : Type',
        '  | bits : (a ||| b = c) → BoolMask',
        '  | empty : BoolMask',
        '',
        'coinductive Stream (α : Type) where',
        '  | cons (head : α) (tail : Stream α) : Stream α',
        '',
        'instance (priority := high) [Addable α] : Addable (Option α) where',
        '  add',
        '    | some x, some y => some (Addable.add x y)',
        '    | _, _ => none',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 7);

      // structure ... extends ... with string/char defaults & column-0 deriving
      const child = blocks[0].decl!;
      assert.equal(child.kind, 'structure');
      assert.equal(child.name, 'Child');
      assert.equal(child.fields?.length, 2);
      assert.equal(child.fields![0].defaultValue, '"-- not a comment"');
      assert.equal(child.fields![0].comment, 'field note');
      assert.equal(child.fields![1].defaultValue, "'\"'");
      assert.equal(child.fields![1].comment, 'double-quote literal');
      assert.deepEqual(child.derivingClause, [
        'DecidableEq',
        'Repr',
        'Hashable',
      ]);

      // structure with custom `mk ::` gracefully falls back to code lines while keeping deriving
      const point = blocks[1].decl!;
      assert.equal(point.kind, 'structure');
      assert.isUndefined(point.fields);
      assert.equal(point.bodyLines.length, 3);
      assert.deepEqual(point.derivingClause, ['Repr']);

      // class with field table
      const addable = blocks[2].decl!;
      assert.equal(addable.kind, 'class');
      assert.equal(addable.fields?.length, 1);
      assert.equal(addable.fields![0].name, 'add');

      // inline-pipe inductive constructors
      const dir = blocks[3].decl!;
      assert.equal(dir.kind, 'inductive');
      assert.deepEqual(
        dir.constructors?.map(c => c.name),
        ['north', 'south', 'east', 'west']
      );
      assert.equal(dir.constructors![3].comment, 'cardinal directions');
      assert.deepEqual(dir.derivingClause, ['Repr', 'BEq']);

      // inductive without `where`, bitwise `|||` inside type must NOT split constructor
      const mask = blocks[4].decl!;
      assert.equal(mask.constructors?.length, 2);
      assert.equal(mask.constructors![0].name, 'bits');
      assert.equal(
        mask.constructors![0].signatureText,
        ': (a ||| b = c) → BoolMask'
      );

      // coinductive type
      const stream = blocks[5].decl!;
      assert.equal(stream.kind, 'coinductive');
      assert.equal(stream.constructors?.length, 1);
      assert.equal(stream.constructors![0].name, 'cons');

      // anonymous prioritized `instance ... where` splits cleanly at `where`, not at `(priority := high)` or method `:=`
      const instAnon = blocks[6].decl!;
      assert.equal(instAnon.kind, 'instance');
      assert.equal(instAnon.name, '');
      assert.equal(instAnon.signatureLines.length, 1);
      assert.equal(instAnon.bodyLines.length, 3);
    });

    test('parses named instance ... where and term-style instance := cleanly', () => {
      const source = [
        'instance instAddNat : Add Nat where',
        '  add x y := x + y',
        '',
        'instance : Inhabited Nat := ⟨0⟩',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 2);

      const namedInst = blocks[0].decl!;
      assert.equal(namedInst.kind, 'instance');
      assert.equal(namedInst.name, 'instAddNat');
      assert.equal(namedInst.signatureLines.length, 1);
      assert.equal(namedInst.bodyLines.length, 1);
      assert.include(namedInst.bodyLines[0].text, 'add x y := x + y');

      const termInst = blocks[1].decl!;
      assert.equal(termInst.kind, 'instance');
      assert.equal(termInst.name, '');
      assert.equal(termInst.signatureLines[0].text, 'instance : Inhabited Nat');
      assert.equal(termInst.bodyLines[0].text, '⟨0⟩');
    });

    test('keeps termination_by, decreasing_by, where helpers, and partial_fixpoint attached to their declaration', () => {
      const source = [
        'def ackermann (m n : Nat) : Nat :=',
        '  match m, n with',
        '  | 0, y => y + 1',
        '  | x + 1, 0 => ackermann x 1',
        '  | x + 1, y + 1 => ackermann x (ackermann (x + 1) y)',
        'termination_by (m, n)',
        'decreasing_by',
        '  all_goals simp_wf',
        '  sorry',
        '',
        'def sumTo (n : Nat) : Nat :=',
        '  go n 0',
        'where',
        '  go : Nat → Nat → Nat',
        '    | 0, acc => acc',
        '    | k + 1, acc => go k (acc + k + 1)',
        '',
        'def findZero (x : Nat) : Option Nat :=',
        '  if x == 0 then some 0 else findZero (x - 1)',
        'partial_fixpoint',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 3);

      const ack = blocks[0].decl!;
      assert.equal(ack.name, 'ackermann');
      assert.equal(blocks[0].startLine, 1);
      assert.equal(blocks[0].endLine, 9);
      assert.isTrue(ack.hygiene.hasSorry);

      const sumTo = blocks[1].decl!;
      assert.equal(sumTo.name, 'sumTo');
      assert.equal(blocks[1].startLine, 11);
      assert.equal(blocks[1].endLine, 16);
      assert.isFalse(sumTo.hygiene.hasSorry);

      const findZero = blocks[2].decl!;
      assert.equal(findZero.name, 'findZero');
      assert.equal(blocks[2].startLine, 18);
      assert.equal(blocks[2].endLine, 20);
    });

    test('parses mutual ... end blocks into first-class inner declarations with hygiene tracking', () => {
      const source = [
        'mutual',
        '  inductive Tree where',
        '    | node : TreeList → Tree',
        '',
        '  inductive TreeList where',
        '    | nil : TreeList',
        '    | cons : Tree → TreeList → TreeList',
        'end',
        '',
        'mutual',
        '  /-- Even check. -/',
        '  def isEven : Nat → Bool',
        '    | 0 => true',
        '    | n + 1 => isOdd n',
        '',
        '  theorem even_or_odd (n : Nat) : isEven n = true ∨ isOdd n = true := by',
        '    sorry',
        'end',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.deepEqual(
        blocks.map(b =>
          b.type === 'declaration'
            ? `${b.decl!.kind}:${b.decl!.name}`
            : `${b.type}:${b.directiveKind}`
        ),
        [
          'directive:mutual',
          'inductive:Tree',
          'inductive:TreeList',
          'directive:end',
          'directive:mutual',
          'def:isEven',
          'theorem:even_or_odd',
          'directive:end',
        ]
      );
      assert.equal(blocks[1].decl!.constructors?.length, 1);
      assert.equal(blocks[2].decl!.constructors?.length, 2);
      assert.equal(blocks[5].decl!.docComment, 'Even check.');
      assert.isTrue(blocks[6].decl!.hygiene.hasSorry);
    });

    test('parses calc and conv proofs accurately', () => {
      const source = [
        'theorem trans_calc (a b c : Nat) (h1 : a ≤ b) (h2 : b ≤ c) : a ≤ c := by',
        '  calc',
        '    a ≤ b := h1',
        '    _ ≤ c := h2',
        '',
        'theorem conv_rhs (a b c : Nat) : a + (b + c) = a + (c + b) := by',
        '  conv =>',
        '    lhs',
        '    arg 2',
        '    rw [Nat.add_comm]',
        '',
        'theorem term_calc (a b : Nat) (h : a = b) : a = b :=',
        '  calc',
        '    a = b := h',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 3);
      assert.equal(blocks[0].decl!.proofStrategy, 'tactic');
      assert.equal(blocks[0].decl!.bodyLines.length, 3);
      assert.equal(blocks[1].decl!.proofStrategy, 'tactic');
      assert.equal(blocks[1].decl!.bodyLines.length, 4);
      assert.equal(blocks[2].decl!.proofStrategy, 'term');
      assert.equal(blocks[2].decl!.bodyLines.length, 2);
    });

    test('avoids hygiene false positives from raw strings, char quotes, and guillemets inside declarations', () => {
      const source = [
        'def trickyLiterals : String × Char × Nat :=',
        '  (r#"not a "sorry" or admit or native_decide"#, \'"\', «sorry»)',
        'where',
        '  «sorry» : Nat := 7',
      ].join('\n');

      const blocks = parseLeanBlocks(source);
      assert.equal(blocks.length, 1);
      const decl = blocks[0].decl!;
      assert.isFalse(decl.hygiene.hasSorry);
      assert.isFalse(decl.hygiene.hasAdmit);
      assert.isFalse(decl.hygiene.hasNativeDecide);
    });
  });

  suite('tokenizeLeanLine() and highlightLeanLine()', () => {
    test('highlights keywords, tactics, types, operators, bullets, sorry, and prime identifiers', () => {
      const tokens = tokenizeLeanLine(
        "  · have hlt' : i < k := by omega -- comment",
        true
      );
      const kinds = tokens.map(t => `${t.kind}:${t.text}`);
      assert.include(kinds, 'bullet:·');
      assert.include(kinds, 'keyword:have');
      assert.isTrue(
        tokens.some(t => t.kind === 'plain' && t.text.includes("hlt'"))
      );
      assert.isFalse(tokens.some(t => t.kind === 'string'));
      assert.include(kinds, 'tactic:omega');
      assert.include(kinds, 'comment:-- comment');
    });

    test('tokenizes raw strings, interpolated strings, char literals, nested-bracket attributes, radix numbers, and Lean 4 tactics', () => {
      const rawLine =
        '@[simp [Nat.add_comm]] public def demo := (r#"quoted "sorry""#, s!"hi {x}", \'"\', \'\\u03bb\', 0xff_00, 0b1010, 0o755)';
      const tokens = tokenizeLeanLine(rawLine, false);
      const pairs = tokens.map(t => `${t.kind}:${t.text}`);

      assert.include(pairs, 'attribute:@[simp [Nat.add_comm]]');
      assert.include(pairs, 'keyword:public');
      assert.include(pairs, 'keyword:def');
      assert.include(pairs, 'string:r#"quoted "sorry""#');
      assert.include(pairs, 'string:s!"hi {x}"');
      assert.include(pairs, "string:'\"'");
      assert.include(pairs, "string:'\\u03bb'");
      assert.include(pairs, 'number:0xff_00');
      assert.include(pairs, 'number:0b1010');
      assert.include(pairs, 'number:0o755');
      // `sorry` inside raw string must NEVER emit a `sorry` token
      assert.isFalse(tokens.some(t => t.kind === 'sorry'));

      const tacticLine =
        '  conv => lhs; arg 2; simp_wf <;> grind <;> bv_decide';
      const tacticTokens = tokenizeLeanLine(tacticLine, true);
      const tacticPairs = tacticTokens.map(t => `${t.kind}:${t.text}`);
      assert.include(tacticPairs, 'keyword:conv');
      assert.include(tacticPairs, 'tactic:lhs');
      assert.include(tacticPairs, 'tactic:arg');
      assert.include(tacticPairs, 'tactic:simp_wf');
      assert.include(tacticPairs, 'operator:<;>');
      assert.include(tacticPairs, 'tactic:grind');
      assert.include(tacticPairs, 'tactic:bv_decide');
    });

    test('escapes HTML tags safely when rendering highlighted Lean code', async () => {
      const tpl = highlightLeanLine(
        'def xss : String := "<img src=x onerror=alert(1)>"'
      );
      const container = await fixture(html`<div>${tpl}</div>`);
      assert.isNull(container.querySelector('img'));
      assert.include(container.textContent, '<img src=x onerror=alert(1)>');
    });

    test('neutralizes javascript: URLs inside rendered Lean docstrings', async () => {
      const tpl = renderMarkdownContent(
        'See [click](javascript:alert(1)) and <script>alert(1)</script>.'
      );
      const container = await fixture(html`<div>${tpl}</div>`);
      assert.isNull(container.querySelector('script'));
      const link = container.querySelector('a');
      if (link) {
        assert.notInclude(
          (link.getAttribute('href') ?? '').toLowerCase(),
          'javascript:'
        );
      }
    });
  });

  suite('alignLeanBlocks() and computeLeanSummary()', () => {
    test('pairs modified declarations by (kind, name) even when proof body changes heavily', () => {
      const base = [
        'theorem transfer_monotone (x y : Nat) (h : x ≤ y) : x + 1 ≤ y + 1 := by',
        '  sorry',
      ].join('\n');
      const rev = [
        'theorem transfer_monotone (x y : Nat) (h : x ≤ y) : x + 1 ≤ y + 1 := by',
        '  omega',
      ].join('\n');

      const blocksA = parseLeanBlocks(base);
      const blocksB = parseLeanBlocks(rev);
      const rows = alignLeanBlocks(blocksA, blocksB);

      assert.equal(rows.length, 1);
      assert.equal(rows[0].status, 'modified');
      assert.isDefined(rows[0].declDiff);
      assert.isFalse(rows[0].declDiff.signatureChanged);
      assert.isTrue(rows[0].declDiff.bodyChanged);

      const summary = computeLeanSummary(blocksA, blocksB, rows);
      assert.equal(summary.base.sorryCount, 1);
      assert.equal(summary.revision.sorryCount, 0);
      assert.equal(summary.modifiedDeclarations, 1);
    });

    test('aligns structure field additions, deletions, and type/comment modifications', () => {
      const base = [
        'structure Config where',
        '  timeout : Nat -- ms',
        '  legacyFlag : Bool := false -- deprecated',
        '  retries : Nat := 3 -- max retries',
        'deriving Repr',
      ].join('\n');
      const rev = [
        'structure Config where',
        '  timeout : UInt64 -- milliseconds',
        '  retries : Nat := 3 -- max retries',
        '  strictMode : Bool := true -- fail fast',
        'deriving Repr, BEq',
      ].join('\n');

      const rows = alignLeanBlocks(parseLeanBlocks(base), parseLeanBlocks(rev));
      assert.equal(rows.length, 1);
      assert.equal(rows[0].status, 'modified');
      const fields = rows[0].declDiff?.fields;
      assert.isDefined(fields);
      assert.isTrue(rows[0].declDiff?.fieldsChanged);
      assert.isTrue(
        fields.some(
          f => f.status === 'modified' && f.rightField?.name === 'timeout'
        )
      );
      assert.isTrue(
        fields.some(
          f => f.status === 'deleted' && f.leftField?.name === 'legacyFlag'
        )
      );
      assert.isTrue(
        fields.some(
          f => f.status === 'unchanged' && f.rightField?.name === 'retries'
        )
      );
      assert.isTrue(
        fields.some(
          f => f.status === 'added' && f.rightField?.name === 'strictMode'
        )
      );
    });

    test('pairs declaration across kind rename (lemma -> theorem) and diffs module_doc markdown', () => {
      const base = [
        '/-!',
        '# Spec v1',
        'Initial proof.',
        '-/',
        '',
        'lemma helper (n : Nat) : n + 0 = n := by',
        '  sorry',
      ].join('\n');
      const rev = [
        '/-!',
        '# Spec v2',
        'Verified proof with zero `sorry`.',
        '-/',
        '',
        'theorem helper (n : Nat) : n + 0 = n := by',
        '  simp',
      ].join('\n');

      const blocksA = parseLeanBlocks(base);
      const blocksB = parseLeanBlocks(rev);
      const rows = alignLeanBlocks(blocksA, blocksB);
      assert.equal(rows.length, 2);
      assert.equal(rows[0].status, 'modified');
      assert.isDefined(rows[0].moduleDocRows);
      assert.equal(rows[1].status, 'modified');
      assert.equal(rows[1].leftBlock?.decl?.kind, 'lemma');
      assert.equal(rows[1].rightBlock?.decl?.kind, 'theorem');
    });

    test('handles added-only, deleted-only, and large 250-declaration files with fast prefix/suffix trimming', () => {
      const makeDecl = (k: number, proof: string) =>
        `theorem thm_${k} (n : Nat) : n + ${k} = ${k} + n := by\n  ${proof}`;
      const baseLines: string[] = [];
      const revLines: string[] = [];
      for (let k = 0; k < 250; k++) {
        baseLines.push(makeDecl(k, k === 125 ? 'sorry' : 'omega'));
        revLines.push(makeDecl(k, 'omega'));
      }
      const baseSrc = baseLines.join('\n\n');
      const revSrc = revLines.join('\n\n');

      const blocksA = parseLeanBlocks(baseSrc);
      const blocksB = parseLeanBlocks(revSrc);
      assert.equal(blocksA.length, 250);
      assert.equal(blocksB.length, 250);

      const rows = alignLeanBlocks(blocksA, blocksB);
      assert.equal(rows.length, 250);
      const modified = rows.filter(r => r.status === 'modified');
      assert.equal(modified.length, 1);
      assert.equal(modified[0].rightBlock?.decl?.name, 'thm_125');

      const addedOnlySummary = computeLeanSummary(
        [],
        blocksB,
        alignLeanBlocks([], blocksB)
      );
      assert.isFalse(addedOnlySummary.hasBase);
      assert.isTrue(addedOnlySummary.hasRevision);
      assert.equal(addedOnlySummary.addedDeclarations, 250);
      assert.equal(addedOnlySummary.revision.theorems, 250);

      const deletedOnlySummary = computeLeanSummary(
        blocksA,
        [],
        alignLeanBlocks(blocksA, [])
      );
      assert.isTrue(deletedOnlySummary.hasBase);
      assert.isFalse(deletedOnlySummary.hasRevision);
      assert.equal(deletedOnlySummary.deletedDeclarations, 250);
      assert.equal(deletedOnlySummary.base.sorryCount, 1);
    });

    test('computes aligned code lines with intra-line diff highlights', () => {
      const aligned = alignLeanCodeLines(
        [{lineNum: 1, text: '  have h : x ≤ 31 := by omega'}],
        [{lineNum: 1, text: '  have h : x ≤ MAX_WEIGHT := by omega'}],
        true
      );
      assert.equal(aligned.length, 1);
      assert.equal(aligned[0].status, 'modified');
      assert.isDefined(aligned[0].leftContent);
      assert.isDefined(aligned[0].rightContent);
    });

    test('formats line ranges cleanly', () => {
      assert.equal(formatLineRange(undefined, undefined), '');
      assert.equal(formatLineRange(12, 12), 'L12');
      assert.equal(formatLineRange(95, 111), 'L95–L111');
    });
  });

  suite('adversarial XSS and injection hardening', () => {
    test('renderMarkdownContent strips raw HTML, mXSS, unsafe links, and class injection', async () => {
      const maliciousMd = [
        '<script>window.__xss = 1</script>',
        '<svg onload="window.__xss = 2"></svg>',
        '<iframe src="javascript:alert(1)"></iframe>',
        '<math><mtext></mtext><img src=x onerror="window.__xss=3"></math>',
        '<style>body { display: none; }</style>',
        '<noscript><p title="</noscript><img src=x onerror=alert(1)>">',
        '![img](javascript:alert(1))',
        '[js](javascript:alert(1)) [proto](//evil.com) [bs](/\\evil.com) [entity](&#106;avascript:alert(1))',
        '[safe](https://lean-lang.org/doc/reference/latest/)',
        '```lean selection-action-box diff-cell hygiene-pill verified',
        'theorem p : True := trivial',
        '```',
      ].join('\n\n');

      const el = await fixture<HTMLDivElement>(
        html`<div>${renderMarkdownContent(maliciousMd)}</div>`
      );

      assert.isNull(el.querySelector('script'));
      assert.isNull(el.querySelector('svg'));
      assert.isNull(el.querySelector('iframe'));
      assert.isNull(el.querySelector('img'));
      assert.isNull(el.querySelector('math'));
      assert.isNull(el.querySelector('style'));

      const links = Array.from(el.querySelectorAll('a'));
      assert.isAbove(links.length, 0);
      for (const a of links) {
        const href = a.getAttribute('href') ?? '';
        assert.isFalse(
          /^(javascript|data|vbscript|file):/i.test(href),
          `Unsafe href leaked: ${href}`
        );
        assert.isFalse(
          href.startsWith('//'),
          `Protocol-relative href: ${href}`
        );
        assert.isFalse(href.includes('\\'), `Backslash href: ${href}`);
        assert.equal(a.getAttribute('rel'), 'noopener noreferrer');
        if (href.startsWith('https://')) {
          assert.equal(a.getAttribute('target'), '_blank');
        }
      }

      const codeEl = el.querySelector('pre code');
      assert.isNotNull(codeEl);
      assert.equal(codeEl.className, 'language-lean');
    });

    test('diff-interleaved split HTML payloads stay inert in rendered code lines', async () => {
      const aligned = alignLeanCodeLines(
        [{lineNum: 1, text: 'def x := "<scr" ++ "ipt>alert(1)</script>"'}],
        [
          {
            lineNum: 1,
            text: 'def x := "<script>alert(1)</script><img src=x onerror=alert(1)>"',
          },
        ],
        true
      );
      const el = await fixture<HTMLDivElement>(
        html`<div>${aligned[0].leftContent}${aligned[0].rightContent}</div>`
      );
      assert.isNull(el.querySelector('script'));
      assert.isNull(el.querySelector('img'));
      assert.include(el.textContent ?? '', '<script>alert(1)</script>');
    });

    test('Object.prototype declaration names do not corrupt block parsing, alignment, or summary', () => {
      const srcA = [
        'def __proto__ : Nat := 1',
        'def constructor : Nat := 2',
        'theorem toString : True := by sorry',
        'theorem hasOwnProperty : True := by trivial',
      ].join('\n\n');
      const srcB = [
        'def __proto__ : Nat := 10',
        'def constructor : Nat := 2',
        'theorem toString : True := by trivial',
        'theorem hasOwnProperty : True := by trivial',
      ].join('\n\n');

      const blocksA = parseLeanBlocks(srcA);
      const blocksB = parseLeanBlocks(srcB);
      const rows = alignLeanBlocks(blocksA, blocksB);
      const summary = computeLeanSummary(blocksA, blocksB, rows);

      assert.equal(blocksA.length, 4);
      assert.equal(blocksB.length, 4);
      assert.equal(summary.base.sorryCount, 1);
      assert.equal(summary.revision.sorryCount, 0);
      assert.equal(summary.modifiedDeclarations, 2);
    });
  });

  suite('adversarial soundness hygiene smuggling and parser edge cases', () => {
    test('standalone divider comments terminate immediately without infinite loop', () => {
      const start = performance.now();
      const blocks = parseLeanBlocks(
        [
          '-- ============================================================',
          '-- ============================================================',
          '-- ------------------------------------------------------------',
          'theorem after_dividers : True := by sorry',
        ].join('\n')
      );
      assert.isBelow(performance.now() - start, 200);
      const decl = blocks.find(b => b.type === 'declaration');
      assert.isDefined(decl);
      assert.equal(decl.decl?.name, 'after_dividers');
      assert.isTrue(decl.decl?.hygiene.hasSorry);
    });

    test('detects declarations prefixed by in-scoping directives (open/set_option/variable/universe)', () => {
      const src = [
        'open Nat in theorem sneaky_open : False := by sorry',
        '',
        'set_option maxHeartbeats 0 in',
        '  axiom sneaky_axiom : False',
        '',
        'variable (x : Nat) in',
        'theorem sneaky_var : x = x := by native_decide',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.equal(summary.revision.sorryCount, 1);
      assert.equal(summary.revision.axioms, 1);
      assert.equal(summary.revision.nativeDecideCount, 1);
      assert.isDefined(blocks.find(b => b.decl?.name === 'sneaky_open'));
      assert.isDefined(blocks.find(b => b.decl?.name === 'sneaky_axiom'));
      assert.isDefined(blocks.find(b => b.decl?.name === 'sneaky_var'));
    });

    test('parses indented declarations inside namespace and section blocks', () => {
      const src = [
        'namespace Outer',
        '  section Inner',
        '    theorem indented_bad : False := by',
        '      sorry',
        '    axiom indented_ax : False',
        '  end Inner',
        'end Outer',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.equal(summary.revision.theorems, 1);
      assert.equal(summary.revision.sorryCount, 1);
      assert.equal(summary.revision.axioms, 1);
    });

    test('fail-closed summary catches sorry/admit/native_decide inside macro, elab, initialize, and #eval directives', () => {
      const src = [
        'macro "cheat" : tactic => `(tactic| sorry)',
        'elab "cheat2" : tactic => admit',
        'initialize x := native_decide',
        '#eval sorryAx False false',
      ].join('\n\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.isAtLeast(summary.revision.sorryCount, 2);
      assert.isAtLeast(summary.revision.admitCount, 1);
      assert.isAtLeast(summary.revision.nativeDecideCount, 1);
    });

    test('handles column-0 continuations, -- comment lookahead, blank lines before |, and extends on next line', () => {
      const src = [
        'theorem col0_sorry : False :=',
        'sorry',
        '',
        'theorem comment_before_sorry : False := by',
        '-- harmless comment',
        '  sorry',
        '',
        'def pattern_match : Nat → Nat',
        '',
        '| 0 => 0',
        '| n + 1 => sorry',
        '',
        'structure Child',
        'extends Parent where',
        '  x : Nat',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const col0 = blocks.find(b => b.decl?.name === 'col0_sorry');
      const commentBefore = blocks.find(
        b => b.decl?.name === 'comment_before_sorry'
      );
      const pat = blocks.find(b => b.decl?.name === 'pattern_match');
      const child = blocks.find(b => b.decl?.name === 'Child');

      assert.isTrue(col0?.decl?.hygiene.hasSorry);
      assert.isTrue(commentBefore?.decl?.hygiene.hasSorry);
      assert.isTrue(pat?.decl?.hygiene.hasSorry);
      assert.equal(child?.decl?.kind, 'structure');
      assert.equal(child?.decl?.fields?.length, 1);
    });

    test('detects direct kernel soundness axioms (sorryAx, «sorryAx», ofReduceBool, ofReduceNat)', () => {
      const src = [
        'theorem t1 : False := sorryAx False false',
        'theorem t2 : False := «sorryAx» False false',
        'theorem t3 : True := Lean.ofReduceBool true rfl',
        'theorem t4 : True := Lean.«ofReduceNat» 0 rfl',
      ].join('\n\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.equal(summary.revision.sorryCount, 2);
      assert.equal(summary.revision.nativeDecideCount, 2);

      // Syntax highlighter also tags kernel escapes as sorry tokens
      const tokens1 = tokenizeLeanLine('  exact sorryAx False false');
      assert.isTrue(
        tokens1.some(t => t.kind === 'sorry' && t.text === 'sorryAx')
      );
      const tokens2 = tokenizeLeanLine('  exact «sorryAx» False false');
      assert.isTrue(
        tokens2.some(t => t.kind === 'sorry' && t.text === '«sorryAx»')
      );
      const tokens3 = tokenizeLeanLine('  exact Lean.ofReduceBool true rfl');
      assert.isTrue(
        tokens3.some(t => t.kind === 'sorry' && t.text === 'ofReduceBool')
      );
    });

    test('interpolated string quote-parity and brace expressions cannot hide subsequent sorry', () => {
      const src = [
        'def a : String := s!"{\'"\'}"',
        'theorem after_char_in_interp : False := by sorry',
        '',
        'def b : String := s!"{r#"""#}"',
        'theorem after_raw_in_interp : False := by sorry',
        '',
        'def c : String := s!"{(sorry : String)}"',
        'theorem after_expr_in_interp : True := by trivial',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const tA = blocks.find(b => b.decl?.name === 'after_char_in_interp');
      const tB = blocks.find(b => b.decl?.name === 'after_raw_in_interp');
      const dC = blocks.find(b => b.decl?.name === 'c');

      assert.isTrue(tA?.decl?.hygiene.hasSorry);
      assert.isTrue(tB?.decl?.hygiene.hasSorry);
      assert.isTrue(dC?.decl?.hygiene.hasSorry);
    });

    test('multi-line guillemets and Unicode/! identifier boundaries cannot desync quote parity or cause false positives', () => {
      const src = [
        'def «multi\n"» : Nat := 1',
        'theorem after_guillemet : False := by sorry',
        '',
        'def x1 := f!\'"\'"',
        'theorem after_bang_char : False := by sorry',
        '',
        'def x2 := f!r"\\""',
        'theorem after_bang_raw : False := by sorry',
        '',
        'def x3 := fé\'"\'"',
        'theorem after_unicode_char : False := by sorry',
        '',
        '-- Non-false-positive identifiers with Lean ident continuations',
        "def sorry' : Nat := 1",
        'def sorry? : Nat := 2',
        'def sorry₀ : Nat := 3',
        'def αsorry : Nat := 4',
        'def «no sorry allowed» : Nat := 5',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.equal(summary.revision.sorryCount, 4);
      assert.isFalse(
        blocks.find(b => b.decl?.name === "sorry'")?.decl?.hygiene.hasSorry
      );
      assert.isFalse(
        blocks.find(b => b.decl?.name === 'sorry?')?.decl?.hygiene.hasSorry
      );
      assert.isFalse(
        blocks.find(b => b.decl?.name === 'sorry₀')?.decl?.hygiene.hasSorry
      );
      assert.isFalse(
        blocks.find(b => b.decl?.name === 'αsorry')?.decl?.hygiene.hasSorry
      );
      assert.isFalse(
        blocks.find(b => b.decl?.name === '«no sorry allowed»')?.decl?.hygiene
          .hasSorry
      );
    });

    test('block comment trailing declarations, dual block comments, and unclosed EOF comments parse safely', () => {
      const src = [
        '/- Copyright 2026 -/ axiom hidden_after_block : False',
        '/-- Doc on same line -/ theorem hidden_after_doc : False := by sorry',
        '/- first -/ /-',
        'theorem fake_inside_second : False := by sorry',
        '-/',
        '/- unclosed eof comment',
        '',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.equal(summary.revision.axioms, 1);
      assert.equal(summary.revision.sorryCount, 1);
      assert.isUndefined(
        blocks.find(b => b.decl?.name === 'fake_inside_second')
      );
    });

    test('multi-line attributes, multi-line modifiers, class inductive/abbrev, BOM, and CRLF are handled cleanly', () => {
      const src =
        '\uFEFF' +
        [
          '@[simp,',
          '  inline]',
          'theorem attr_multiline : True := by trivial',
          '',
          '@[simp /- ] -/] theorem attr_with_comment : False := by sorry',
          '',
          '@[simp] /- c -/ theorem attr_then_comment : True := by trivial',
          '',
          'unsafe',
          'def sneaky_unsafe : Nat := 0',
          '',
          'partial',
          'def sneaky_partial (n : Nat) : Nat := sneaky_partial n',
          '',
          'private /- c -/ axiom sneaky_private_ax : False',
          '',
          'class inductive MyClassInd : Type where',
          '  | mk : MyClassInd',
          '',
          'class abbrev MyClassAbbrev := MyClassInd',
        ].join('\r\n');

      const blocks = parseLeanBlocks(src);
      const summary = computeLeanSummary(
        [],
        blocks,
        alignLeanBlocks([], blocks)
      );
      assert.equal(summary.revision.sorryCount, 1);
      assert.equal(summary.revision.unsafeCount, 1);
      assert.equal(summary.revision.partialCount, 1);
      assert.equal(summary.revision.axioms, 1);
      assert.equal(
        blocks.find(b => b.decl?.name === 'MyClassInd')?.decl?.kind,
        'inductive'
      );
      assert.equal(
        blocks.find(b => b.decl?.name === 'MyClassAbbrev')?.decl?.kind,
        'abbrev'
      );
    });

    test('detectProofStrategy rejects dirty decide proofs and splits signature/body accurately around comments', () => {
      const src = [
        'theorem dirty_decide : False := by decide <;> sorry',
        '',
        "theorem prime_decide : True := by decide'",
        '',
        'theorem tab_decide : 1 + 1 = 2 := by\tdecide',
        '',
        'theorem comment_in_sig (x : Nat /- := fake -/) : x = x := by/- c -/ rfl',
      ].join('\n');

      const blocks = parseLeanBlocks(src);
      const dirty = blocks.find(b => b.decl?.name === 'dirty_decide')!.decl!;
      const prime = blocks.find(b => b.decl?.name === 'prime_decide')!.decl!;
      const tab = blocks.find(b => b.decl?.name === 'tab_decide')!.decl!;
      const commentSig = blocks.find(b => b.decl?.name === 'comment_in_sig')!
        .decl!;

      assert.equal(dirty.proofStrategy, 'tactic');
      assert.isTrue(dirty.hygiene.hasSorry);
      assert.equal(prime.proofStrategy, 'tactic');
      assert.equal(tab.proofStrategy, 'decide');
      assert.isTrue(tab.isTacticProof);
      assert.isTrue(commentSig.isTacticProof);
      assert.include(
        commentSig.signatureLines.map(l => l.text).join('\n'),
        'x = x'
      );
    });
  });

  suite('DoS resilience and lossless diff tokenization', () => {
    test('tokenizeLeanDiffWords has no ReDoS on 50k unclosed guillemets and is strictly lossless', () => {
      const start = performance.now();
      const tokens = tokenizeLeanDiffWords('«'.repeat(50_000));
      assert.isBelow(performance.now() - start, 200);
      assert.equal(tokens.length, 50_000);

      const samples = [
        'def x := 0x7ff + 3.14159 + 0b1010 + 1_000_000',
        'theorem ∀ α β, «spaced ident» → ∃ γ, x ≠ y := by simp',
        'def emoji := "😀🚀" -- trailing\tspace  ',
        'unclosed «guillemet and \u0007 control',
      ];
      for (const sample of samples) {
        assert.equal(
          tokenizeLeanDiffWords(sample).join(''),
          sample,
          `Lossy tokenization on: ${sample}`
        );
      }
    });

    test('alignLeanCodeLines preserves and diffs numeric literals without dropping digits', async () => {
      const aligned = alignLeanCodeLines(
        [{lineNum: 1, text: 'def limit : Nat := 100'}],
        [{lineNum: 1, text: 'def limit : Nat := 0'}],
        true
      );
      assert.equal(aligned.length, 1);
      assert.equal(aligned[0].status, 'modified');

      const leftEl = await fixture<HTMLDivElement>(
        html`<div>${aligned[0].leftContent}</div>`
      );
      const rightEl = await fixture<HTMLDivElement>(
        html`<div>${aligned[0].rightContent}</div>`
      );
      assert.equal(leftEl.querySelector('del')?.textContent, '100');
      assert.equal(rightEl.querySelector('ins')?.textContent, '0');
    });

    test('handles 100KB Lean line and 200-block disjoint diff within latency budget', () => {
      const start = performance.now();
      const hugeLine = 'def big : Nat := ' + '1 + '.repeat(25_000) + '0';
      const tokens = tokenizeLeanLine(hugeLine);
      assert.isAbove(tokens.length, 25_000);

      const baseBlocks = parseLeanBlocks(
        Array.from({length: 200}, (_, i) => `def a_${i} : Nat := ${i}`).join(
          '\n\n'
        )
      );
      const revBlocks = parseLeanBlocks(
        Array.from({length: 200}, (_, i) => `def b_${i} : Nat := ${i}`).join(
          '\n\n'
        )
      );
      const rows = alignLeanBlocks(baseBlocks, revBlocks);
      assert.equal(rows.length, 400);
      assert.isBelow(performance.now() - start, 1500);
    });
  });
});
