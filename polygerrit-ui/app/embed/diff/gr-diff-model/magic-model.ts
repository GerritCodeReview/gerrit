/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable} from 'rxjs';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/model';
import {select} from '../../../utils/observable-util';

export interface MagicState {
  search: string;
  hideHeaderRow: boolean;
  hideFileNameRow: boolean;
  hideBoth: boolean;
  hideControls: boolean;
}

export const magicModelToken = define<MagicModel>('magic-model');

export class MagicModel extends Model<MagicState> {
  constructor() {
    super({
      search: '',
      hideHeaderRow: false,
      hideFileNameRow: false,
      hideBoth: false,
      hideControls: false,
    });
  }

  readonly search$: Observable<string> = select(
    this.state$,
    diffState => diffState.search
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
}
