/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest, from} from 'rxjs';
import {switchMap, withLatestFrom} from 'rxjs/operators';
import {
  DiffInfo,
  DiffPreferencesInfo,
  DisplayLine,
  RenderPreferences,
} from '../../../api/diff';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/model';
import {select} from '../../../utils/observable-util';
import {
  FullContext,
  GrDiffCommentThread,
  KeyLocations,
  computeContext,
  computeKeyLocations,
} from '../gr-diff/gr-diff-utils';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  GrDiffProcessor,
  ProcessingOptions,
} from '../gr-diff-processor/gr-diff-processor';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';

export interface DiffState {
  diff: DiffInfo;
  path?: string;
  renderPrefs: RenderPreferences;
  diffPrefs: DiffPreferencesInfo;
  lineOfInterest?: DisplayLine;
  comments: GrDiffCommentThread[];
  groups: GrDiffGroup[];
  /** how much context to show for large files */
  showFullContext: FullContext;
  isImageDiff: boolean;
}

export const diffModelToken = define<DiffModel>('diff-model');

export class DiffModel extends Model<DiffState> {
  readonly diff$: Observable<DiffInfo> = select(
    this.state$,
    diffState => diffState.diff
  );

  readonly path$: Observable<string | undefined> = select(
    this.state$,
    diffState => diffState.path
  );

  readonly renderPrefs$: Observable<RenderPreferences> = select(
    this.state$,
    diffState => diffState.renderPrefs
  );

  readonly diffPrefs$: Observable<DiffPreferencesInfo> = select(
    this.state$,
    diffState => diffState.diffPrefs
  );

  readonly context$: Observable<number> = select(this.state$, state =>
    computeContext(
      state.diffPrefs.context,
      state.showFullContext,
      createDefaultDiffPrefs().context
    )
  );

  readonly isImageDiff$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.isImageDiff
  );

  readonly keyLocations$: Observable<KeyLocations> = select(
    this.state$,
    diffState =>
      computeKeyLocations(diffState.lineOfInterest, diffState.comments ?? [])
  );

  constructor() {
    super({
      diff: {content: [], change_type: 'MODIFIED', intraline_status: 'OK'},
      diffPrefs: createDefaultDiffPrefs(),
      renderPrefs: {},
      comments: [],
      groups: [],
      showFullContext: FullContext.UNDECIDED,
      isImageDiff: false,
    });
    this.subscriptions = [this.processDiff()];
  }

  processDiff() {
    return combineLatest([
      this.diff$,
      this.context$,
      this.renderPrefs$,
      this.isImageDiff$,
    ])
      .pipe(
        withLatestFrom(this.keyLocations$),
        switchMap(
          ([[diff, context, renderPrefs, isImageDiff], keyLocations]) => {
            const options: ProcessingOptions = {
              context,
              keyLocations,
              isBinary: !!(isImageDiff || diff.binary),
            };
            const processor = new GrDiffProcessor(undefined, options);
            if (renderPrefs?.num_lines_rendered_at_once) {
              options.asyncThreshold = renderPrefs.num_lines_rendered_at_once;
            }
            return from(processor.process(diff.content));
          }
        )
      )
      .subscribe(groups => {
        this.updateState({groups});
      });
  }
}
