/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  BasePatchSetNum,
  FileInfo,
  FileNameToFileInfoMap,
  PARENT,
  PatchRange,
  PatchSetNumber,
  RevisionPatchSetNum,
} from '../../types/common';
import {combineLatest, Subscription, of, from} from 'rxjs';
import {switchMap, map} from 'rxjs/operators';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../services/registry';
import {select} from '../../utils/observable-util';
import {FileInfoStatus, SpecialFilePath} from '../../constants/constants';
import {specialFilePathCompare} from '../../utils/path-list-util';
import {Model} from '../model';
import {define} from '../dependency';
import {ChangeModel} from './change-model';
import {CommentsModel} from '../comments/comments-model';

export interface NormalizedFileInfo extends FileInfo {
  __path: string;
  // Compared to `FileInfo` these four props are required here.
  lines_inserted: number;
  lines_deleted: number;
  size_delta: number; // in bytes
  size: number; // in bytes
}

export function normalize(file: FileInfo, path: string): NormalizedFileInfo {
  return {
    ...file,
    __path: path,
    lines_inserted: file.lines_inserted ?? 0,
    lines_deleted: file.lines_deleted ?? 0,
    size_delta: file.size_delta ?? 0,
    size: file.size ?? 0,
  };
}

function mapToList(map?: FileNameToFileInfoMap): NormalizedFileInfo[] {
  const list: NormalizedFileInfo[] = [];
  for (const [key, value] of Object.entries(map ?? {})) {
    list.push(normalize(value, key));
  }
  list.sort(fileCompare);
  return list;
}

function fileCompare(f1: NormalizedFileInfo, f2: NormalizedFileInfo) {
  return specialFilePathCompare(f1.__path, f2.__path);
}

export function addUnmodified(
  files: NormalizedFileInfo[],
  commentedPaths: string[]
) {
  const combined = [...files];
  for (const commentedPath of commentedPaths) {
    if (commentedPath === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) continue;
    if (files.some(f => f.__path === commentedPath)) continue;
    if (
      files.some(
        f => f.status === FileInfoStatus.RENAMED && f.old_path === commentedPath
      )
    ) {
      continue;
    }
    combined.push(
      normalize({status: FileInfoStatus.UNMODIFIED}, commentedPath)
    );
  }
  combined.sort(fileCompare);
  return combined;
}

export interface FilesState {
  // TODO: Maybe a loading state??

  // TODO: Maybe move reviewed files from change model into here?

  /**
   * Basic file and diff information of all files for the currently chosen
   * patch range.
   */
  files: NormalizedFileInfo[];

  /**
   * Basic file and diff information of all files for the left chosen patchset
   * compared against its base (aka parent).
   *
   * Empty if the left chosen patchset is PARENT.
   */
  filesLeftBase: NormalizedFileInfo[];

  /**
   * Basic file and diff information of all files for the right chosen patchset
   * compared against its base (aka parent).
   *
   * Empty if the left chosen patchset is PARENT.
   */
  filesRightBase: NormalizedFileInfo[];
}

const initialState: FilesState = {
  files: [],
  filesLeftBase: [],
  filesRightBase: [],
};

export const filesModelToken = define<FilesModel>('files-model');

export class FilesModel extends Model<FilesState> implements Finalizable {
  public readonly files$ = select(this.state$, state => state.files);

  public readonly filesWithUnmodified$ = select(
    combineLatest([this.files$, this.commentsModel.commentedPaths$]),
    ([files, commentedPaths]) => addUnmodified(files, commentedPaths)
  );

  private subscriptions: Subscription[] = [];

  constructor(
    readonly changeModel: ChangeModel,
    readonly commentsModel: CommentsModel,
    readonly restApiService: RestApiService
  ) {
    super(initialState);
    this.subscriptions = [
      this.subscribeToFiles(
        (psLeft, psRight) => {
          return {basePatchNum: psLeft, patchNum: psRight};
        },
        files => {
          return {files: [...files]};
        }
      ),
      this.subscribeToFiles(
        (psLeft, _) => {
          if (psLeft === PARENT || psLeft <= 0) return undefined;
          return {basePatchNum: PARENT, patchNum: psLeft as PatchSetNumber};
        },
        files => {
          return {filesLeftBase: [...files]};
        }
      ),
      this.subscribeToFiles(
        (psLeft, psRight) => {
          if (psLeft === PARENT || psLeft <= 0) return undefined;
          return {basePatchNum: PARENT, patchNum: psRight as PatchSetNumber};
        },
        files => {
          return {filesRightBase: [...files]};
        }
      ),
    ];
  }

  private subscribeToFiles(
    rangeChooser: (
      basePatchNum: BasePatchSetNum,
      patchNum: RevisionPatchSetNum
    ) => PatchRange | undefined,
    filesToState: (files: NormalizedFileInfo[]) => Partial<FilesState>
  ) {
    return combineLatest([
      this.changeModel.reload$,
      this.changeModel.changeNum$,
      this.changeModel.basePatchNum$,
      this.changeModel.patchNum$,
    ])
      .pipe(
        switchMap(([_, changeNum, basePatchNum, patchNum]) => {
          if (!changeNum || !patchNum) return of({});
          const range = rangeChooser(basePatchNum, patchNum);
          if (!range) return of({});
          return from(
            this.restApiService.getChangeOrEditFiles(changeNum, range)
          );
        }),
        map(mapToList),
        map(filesToState)
      )
      .subscribe(state => {
        this.updateFiles(state);
      });
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }

  // visible for testing
  updateFiles(newState: Partial<FilesState>) {
    const current = this.subject$.getValue();
    this.setState({
      ...current,
      ...newState,
    });
  }

  // visible for testing
  setState(state: FilesState) {
    this.subject$.next(state);
  }
}
