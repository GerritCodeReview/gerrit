/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest, from} from 'rxjs';
import {debounceTime, filter, switchMap, withLatestFrom} from 'rxjs/operators';
import {
  DiffInfo,
  DiffPreferencesInfo,
  DiffViewMode,
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
  computeLineLength,
} from '../gr-diff/gr-diff-utils';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  GrDiffProcessor,
  ProcessingOptions,
} from '../gr-diff-processor/gr-diff-processor';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {assert} from '../../../utils/common-util';
import {GrDiff} from '../gr-diff/gr-diff';

export interface DiffState {
  diff?: DiffInfo;
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
    this.state$.pipe(filter(state => state.diff !== undefined)),
    diffState => diffState.diff!
  );

  readonly path$: Observable<string | undefined> = select(
    this.state$,
    diffState => diffState.path
  );

  readonly renderPrefs$: Observable<RenderPreferences> = select(
    this.state$,
    diffState => diffState.renderPrefs
  );

  readonly viewMode$: Observable<DiffViewMode> = select(
    this.renderPrefs$,
    renderPrefs => renderPrefs.view_mode ?? DiffViewMode.SIDE_BY_SIDE
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

  readonly groups$: Observable<GrDiffGroup[]> = select(
    this.state$,
    diffState => diffState.groups ?? []
  );

  readonly lineLength$: Observable<number> = select(this.state$, state =>
    computeLineLength(state.diffPrefs, state.path)
  );

  readonly keyLocations$: Observable<KeyLocations> = select(
    this.state$,
    diffState =>
      computeKeyLocations(diffState.lineOfInterest, diffState.comments ?? [])
  );

  constructor() {
    super({
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
        debounceTime(1),
        switchMap(
          ([[diff, context, renderPrefs, isImageDiff], keyLocations]) => {
            const options: ProcessingOptions = {
              context,
              keyLocations,
              isBinary: !!(isImageDiff || diff.binary),
            };
            if (renderPrefs?.num_lines_rendered_at_once) {
              options.asyncThreshold = renderPrefs.num_lines_rendered_at_once;
            }
            const processor = new GrDiffProcessor(options);
            return from(processor.process(diff.content));
          }
        )
      )
      .subscribe(groups => {
        this.updateState({groups});
      });
  }

  /**
   * Replace a context control group with some expanded groups. Happens when the
   * user clicks "+10" or something similar.
   */
  replaceGroup(group: GrDiffGroup, newGroups: readonly GrDiffGroup[]) {
    assert(
      group.type === GrDiffGroupType.CONTEXT_CONTROL,
      'gr-diff can only replace context control groups'
    );
    const groups = [...this.getState().groups];
    const i = groups.indexOf(group);
    if (i === -1) throw new Error('cannot find context control group');
    groups.splice(i, 1, ...newGroups);
    this.updateState({groups});
  }
}
