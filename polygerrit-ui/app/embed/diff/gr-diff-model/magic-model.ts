/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/model';
import {select} from '../../../utils/observable-util';
import {isMagicPath} from '../../../utils/path-list-util';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiffLine} from '../gr-diff/gr-diff-line';

export interface MagicState {
  search: string;
  ignore: string;
  hideHeaderRow: boolean;
  hideFileNameRow: boolean;
  hideBoth: boolean;
  hideControls: boolean;
  fadedDiff: boolean;
  replacements: Map<string, Replacement>;
  ignoredReplacements: Replacement[];
}

export interface Replacement {
  count?: number;
  left: string;
  right: string;
}

export const magicModelToken = define<MagicModel>('magic-model');

export class MagicModel extends Model<MagicState> {
  constructor() {
    super({
      search: '',
      ignore: '',
      hideHeaderRow: false,
      hideFileNameRow: false,
      hideBoth: false,
      hideControls: false,
      fadedDiff: false,
      replacements: new Map<string, Replacement>(),
      ignoredReplacements: [],
    });
  }

  readonly search$: Observable<string> = select(
    this.state$,
    diffState => diffState.search
  );

  readonly ignore$: Observable<string> = select(
    this.state$,
    diffState => diffState.ignore
  );

  readonly hideHeaderRow$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.hideHeaderRow
  );

  readonly hideFileNameRow$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.hideFileNameRow
  );

  readonly hideBoth$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.hideBoth
  );

  readonly hideControls$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.hideControls
  );

  readonly fadedDiff$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.fadedDiff
  );

  readonly replacements$ = select(
    this.state$,
    diffState => diffState.replacements
  );

  readonly ignoredReplacements$ = select(
    this.state$,
    diffState => diffState.ignoredReplacements
  );

  readonly replacementCounts$: Observable<Replacement[]> = select(
    this.replacements$,
    replacements => {
      const countMap = new Map<string, Replacement>();
      for (const r of replacements.values()) {
        const key = `${r.left}-$#$-${r.right}`;
        if (!countMap.has(key)) {
          countMap.set(key, {...r, count: 0});
        }
        countMap.get(key)!.count! += 1;
      }
      return [...countMap.values()].sort(
        (a, b) => (b.count ?? 0) - (a.count ?? 0)
      );
    }
  );

  toggleIgnoredReplacement(replacement: Replacement) {
    const current = [...this.getState().ignoredReplacements];
    const without = current.filter(
      r => r.left !== replacement.left || r.right !== replacement.right
    );
    if (current.length === without.length) {
      without.push(replacement);
    }
    this.updateState({ignoredReplacements: without});
  }

  setDiffGroup(path: string | undefined, group: GrDiffGroup) {
    if (!path) return;
    if (isMagicPath(path)) return;
    if (group.type === GrDiffGroupType.CONTEXT_CONTROL) return;
    if (group.type === GrDiffGroupType.BOTH) return;
    const map = new Map(this.getState().replacements);
    for (const {
      left: leftLine,
      right: rightLine,
    } of group.getSideBySidePairs()) {
      const replacement = findReplacement(leftLine, rightLine);
      if (!replacement) continue;
      const key = `${path}-${leftLine.beforeNumber}-${rightLine.afterNumber}`;
      map.set(key, replacement);
    }
    this.updateState({replacements: map});
  }
}

export function findReplacement(
  leftLine: GrDiffLine,
  rightLine: GrDiffLine
): Replacement | undefined {
  if (!leftLine.hasIntralineInfo) return undefined;
  if (!rightLine.hasIntralineInfo) return undefined;
  if (leftLine.text === rightLine.text) return undefined;

  const leftStart = leftLine.getIntraStart();
  const rightStart = rightLine.getIntraStart();
  const start = Math.min(leftStart, rightStart);

  const leftEnd = leftLine.getIntraEnd();
  const rightEnd = rightLine.getIntraEnd();
  const end = Math.max(leftEnd, rightEnd);

  const left = leftLine.text.substring(start, leftLine.text.length + end);
  const right = rightLine.text.substring(start, rightLine.text.length + end);

  console.log(`asdf pair "${left}" "${right}"`);
  if (left === right) return undefined;
  return {left, right};
}
