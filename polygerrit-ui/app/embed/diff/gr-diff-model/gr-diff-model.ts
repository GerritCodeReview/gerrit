/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest} from 'rxjs';
import {filter} from 'rxjs/operators';
import {
  DiffInfo,
  DiffPreferencesInfo,
  DisplayLine,
  RenderPreferences,
} from '../../../api/diff';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/model';
import {isDefined} from '../../../types/types';
import {select} from '../../../utils/observable-util';
import {
  GrDiffCommentThread,
  computeKeyLocations,
} from '../gr-diff/gr-diff-utils';
import {KeyLocations} from '../gr-diff-processor/gr-diff-processor';

export interface DiffState {
  diff: DiffInfo;
  path?: string;
  renderPrefs: RenderPreferences;
  diffPrefs: DiffPreferencesInfo;
  lineOfInterest?: DisplayLine;
  comments: GrDiffCommentThread[];
}

export const diffModelToken = define<DiffModel>('diff-model');

export class DiffModel extends Model<DiffState | undefined> {
  readonly diff$: Observable<DiffInfo> = select(
    this.state$.pipe(filter(isDefined)),
    diffState => diffState.diff
  );

  readonly path$: Observable<string | undefined> = select(
    this.state$.pipe(filter(isDefined)),
    diffState => diffState.path
  );

  readonly renderPrefs$: Observable<RenderPreferences> = select(
    this.state$.pipe(filter(isDefined)),
    diffState => diffState.renderPrefs
  );

  readonly diffPrefs$: Observable<DiffPreferencesInfo> = select(
    this.state$.pipe(filter(isDefined)),
    diffState => diffState.diffPrefs
  );

  readonly lineOfInterest$: Observable<DisplayLine | undefined> = select(
    this.state$,
    diffState => diffState?.lineOfInterest
  );

  readonly comments$: Observable<GrDiffCommentThread[]> = select(
    this.state$,
    diffState => diffState?.comments ?? []
  );

  readonly keyLocations$: Observable<KeyLocations> = select(
    combineLatest([this.lineOfInterest$, this.comments$]),
    ([lineOfInterest, comments]) =>
      computeKeyLocations(lineOfInterest, comments)
  );
}
