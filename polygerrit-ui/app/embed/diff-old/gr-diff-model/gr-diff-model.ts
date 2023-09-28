/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {filter} from 'rxjs/operators';
import {
  DiffInfo,
  DiffPreferencesInfo,
  RenderPreferences,
} from '../../../api/diff';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/base/model';
import {isDefined} from '../../../types/types';
import {select} from '../../../utils/observable-util';

export interface DiffState {
  diff: DiffInfo;
  path?: string;
  renderPrefs: RenderPreferences;
  diffPrefs: DiffPreferencesInfo;
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
}
