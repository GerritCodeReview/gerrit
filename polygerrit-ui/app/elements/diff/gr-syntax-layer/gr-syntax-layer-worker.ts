/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {FILE, GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {DiffFileMetaInfo, DiffInfo} from '../../../types/diff';
import {DiffLayer, DiffLayerListener} from '../../../types/types';
import {Side} from '../../../constants/constants';
import {createHljsWorker} from '../../../utils/worker-util';

const LANGUAGE_MAP = new Map<string, string>([
  ['application/dart', 'dart'],
  ['application/json', 'json'],
  ['application/x-powershell', 'powershell'],
  ['application/typescript', 'typescript'],
  ['application/xml', 'xml'],
  ['application/xquery', 'xquery'],
  ['application/x-erb', 'erb'],
  ['text/css', 'css'],
  ['text/html', 'html'],
  ['text/javascript', 'js'],
  ['text/jsx', 'jsx'],
  ['text/tsx', 'jsx'],
  ['text/x-c', 'cpp'],
  ['text/x-c++src', 'cpp'],
  ['text/x-clojure', 'clojure'],
  ['text/x-cmake', 'cmake'],
  ['text/x-coffeescript', 'coffeescript'],
  ['text/x-common-lisp', 'lisp'],
  ['text/x-crystal', 'crystal'],
  ['text/x-csharp', 'csharp'],
  ['text/x-csrc', 'cpp'],
  ['text/x-d', 'd'],
  ['text/x-diff', 'diff'],
  ['text/x-django', 'django'],
  ['text/x-dockerfile', 'dockerfile'],
  ['text/x-ebnf', 'ebnf'],
  ['text/x-elm', 'elm'],
  ['text/x-erlang', 'erlang'],
  ['text/x-fortran', 'fortran'],
  ['text/x-fsharp', 'fsharp'],
  ['text/x-gherkin', 'gherkin'],
  ['text/x-go', 'go'],
  ['text/x-groovy', 'groovy'],
  ['text/x-haml', 'haml'],
  ['text/x-handlebars', 'handlebars'],
  ['text/x-haskell', 'haskell'],
  ['text/x-haxe', 'haxe'],
  ['text/x-ini', 'ini'],
  ['text/x-java', 'java'],
  ['text/x-julia', 'julia'],
  ['text/x-kotlin', 'kotlin'],
  ['text/x-latex', 'latex'],
  ['text/x-less', 'less'],
  ['text/x-lua', 'lua'],
  ['text/x-mathematica', 'mathematica'],
  ['text/x-nginx-conf', 'nginx'],
  ['text/x-nsis', 'nsis'],
  ['text/x-objectivec', 'objectivec'],
  ['text/x-ocaml', 'ocaml'],
  ['text/x-perl', 'perl'],
  ['text/x-pgsql', 'pgsql'], // postgresql
  ['text/x-php', 'php'],
  ['text/x-properties', 'properties'],
  ['text/x-protobuf', 'protobuf'],
  ['text/x-puppet', 'puppet'],
  ['text/x-python', 'python'],
  ['text/x-q', 'q'],
  ['text/x-ruby', 'ruby'],
  ['text/x-rustsrc', 'rust'],
  ['text/x-scala', 'scala'],
  ['text/x-scss', 'scss'],
  ['text/x-scheme', 'scheme'],
  ['text/x-shell', 'shell'],
  ['text/x-soy', 'soy'],
  ['text/x-spreadsheet', 'excel'],
  ['text/x-sh', 'bash'],
  ['text/x-sql', 'sql'],
  ['text/x-swift', 'swift'],
  ['text/x-systemverilog', 'sv'],
  ['text/x-tcl', 'tcl'],
  ['text/x-torque', 'torque'],
  ['text/x-twig', 'twig'],
  ['text/x-vb', 'vb'],
  ['text/x-verilog', 'v'],
  ['text/x-vhdl', 'vhdl'],
  ['text/x-yaml', 'yaml'],
  ['text/vbscript', 'vbscript'],
]);

const CLASS_SAFELIST = new Set<string>([
  'gr-diff gr-syntax gr-syntax-attr',
  'gr-diff gr-syntax gr-syntax-attribute',
  'gr-diff gr-syntax gr-syntax-built_in',
  'gr-diff gr-syntax gr-syntax-comment',
  'gr-diff gr-syntax gr-syntax-doctag',
  'gr-diff gr-syntax gr-syntax-function',
  'gr-diff gr-syntax gr-syntax-keyword',
  'gr-diff gr-syntax gr-syntax-link',
  'gr-diff gr-syntax gr-syntax-literal',
  'gr-diff gr-syntax gr-syntax-meta',
  'gr-diff gr-syntax gr-syntax-meta-keyword',
  'gr-diff gr-syntax gr-syntax-name',
  'gr-diff gr-syntax gr-syntax-number',
  'gr-diff gr-syntax gr-syntax-params',
  'gr-diff gr-syntax gr-syntax-property',
  'gr-diff gr-syntax gr-syntax-regexp',
  'gr-diff gr-syntax gr-syntax-selector-attr',
  'gr-diff gr-syntax gr-syntax-selector-class',
  'gr-diff gr-syntax gr-syntax-selector-id',
  'gr-diff gr-syntax gr-syntax-selector-pseudo',
  'gr-diff gr-syntax gr-syntax-selector-tag',
  'gr-diff gr-syntax gr-syntax-string',
  'gr-diff gr-syntax gr-syntax-tag',
  'gr-diff gr-syntax gr-syntax-template-tag',
  'gr-diff gr-syntax gr-syntax-template-variable',
  'gr-diff gr-syntax gr-syntax-title',
  'gr-diff gr-syntax gr-syntax-type',
  'gr-diff gr-syntax gr-syntax-variable',
]);

interface SyntaxLayerRange {
  start: number;
  length: number;
  className: string;
}

export class GrSyntaxLayerWorker implements DiffLayer {
  diff?: DiffInfo;

  enabled = true;

  private baseLines: string[] = [];

  private revisionLines: string[] = [];

  private baseLanguage?: string;

  private revisionLanguage?: string;

  private listeners: DiffLayerListener[] = [];

  init(diff?: DiffInfo) {
    this.baseLines = [];
    this.revisionLines = [];
    this.diff = diff;
  }

  setEnabled(enabled: boolean) {
    this.enabled = enabled;
  }

  addListener(listener: DiffLayerListener) {
    this.listeners.push(listener);
  }

  removeListener(listener: DiffLayerListener) {
    this.listeners = this.listeners.filter(f => f !== listener);
  }

  /**
   * Annotation layer method to add syntax annotations to the given element
   * for the given line.
   */
  annotate(el: HTMLElement, _: HTMLElement, line: GrDiffLine) {
    if (!this.enabled) return;
    if (line.beforeNumber === FILE || line.afterNumber === FILE) return;
    if (line.beforeNumber === 'LOST' || line.afterNumber === 'LOST') return;
    // Determine the side.
    let side;
    if (
      line.type === GrDiffLineType.REMOVE ||
      (line.type === GrDiffLineType.BOTH &&
        el.getAttribute('data-side') !== 'right')
    ) {
      side = 'left';
    } else if (
      line.type === GrDiffLineType.ADD ||
      el.getAttribute('data-side') !== 'left'
    ) {
      side = 'right';
    }

    // Find the relevant syntax ranges, if any.
    let highlightedLine: string | undefined;
    if (side === 'left' && this.baseLines.length >= line.beforeNumber) {
      highlightedLine = this.baseLines[line.beforeNumber - 1];
    }
    if (side === 'right' && this.revisionLines.length >= line.afterNumber) {
      highlightedLine = this.revisionLines[line.afterNumber - 1];
    }
    console.log(`syntax-layer annotate highlighted '${highlightedLine}'`);
    if (!highlightedLine) return;

    const ranges = this._rangesFromString(highlightedLine);
    for (const range of ranges) {
      GrAnnotation.annotateElement(
        el,
        range.start,
        range.length,
        range.className
      );
    }
  }

  _getLanguage(metaInfo: DiffFileMetaInfo) {
    // The Gerrit API provides only content-type, but for other users of
    // gr-diff it may be more convenient to specify the language directly.
    return metaInfo.language ?? LANGUAGE_MAP.get(metaInfo.content_type);
  }

  /**
   * Processing syntax for the loaded diff and notify layer listeners as syntax
   * info comes online.
   */
  process() {
    console.log(
      `syntax-layer process begin ${Date.now()} ${this.enabled} ${
        this.diff?.content.length
      }`
    );
    this.baseLines = [];
    this.revisionLines = [];

    if (!this.enabled || !this.diff?.content.length) {
      return Promise.resolve();
    }

    if (this.diff.meta_a) {
      this.baseLanguage = this._getLanguage(this.diff.meta_a);
    }
    if (this.diff.meta_b) {
      this.revisionLanguage = this._getLanguage(this.diff.meta_b);
    }
    if (!this.baseLanguage && !this.revisionLanguage) {
      console.log('syntax-layer process no language');
      return Promise.resolve();
    }

    let base = '';
    let revi = '';
    for (const dc of this.diff.content) {
      const a = [...(dc.a ?? []), ...(dc.ab ?? [])];
      const b = [...(dc.b ?? []), ...(dc.ab ?? [])];
      for (const aline of a) {
        base += aline + '\n';
      }
      for (const bline of b) {
        revi += bline + '\n';
      }
    }

    console.log(
      `syntax-layer process create workers ${base.length} ${revi.length}`
    );

    const baseWorker = createHljsWorker(base).then(h => {
      this.baseLines = h.split('\n');
    });
    const reviWorker = createHljsWorker(revi).then(h => {
      this.revisionLines = h.split('\n');
    });
    return Promise.all([baseWorker, reviWorker]).then(() => {
      console.log(`syntax-layer process done '${Date.now()}'`);
      this.notify();
    });
  }

  /**
   * Take a string of HTML with the (potentially nested) syntax markers
   * Highlight.js emits and emit a list of text ranges and classes for the
   * markers.
   *
   * @param str The string of HTML.
   * @param rangesCache A map for caching
   * ranges for each string. A cache is read and written by this method.
   * Since diff is mostly comparing same file on two sides, there is good rate
   * of duplication at least for parts that are on left and right parts.
   * @return The list of ranges.
   */
  _rangesFromString(str: string): SyntaxLayerRange[] {
    const div = document.createElement('div');
    div.innerHTML = str;
    const ranges = this._rangesFromElement(div, 0);
    return ranges;
  }

  _rangesFromElement(elem: Element, offset: number): SyntaxLayerRange[] {
    let result: SyntaxLayerRange[] = [];
    for (const node of elem.childNodes) {
      const nodeLength = GrAnnotation.getLength(node);
      // Note: HLJS may emit a span with class undefined when it thinks there
      // may be a syntax error.
      if (
        node instanceof Element &&
        node.tagName === 'SPAN' &&
        node.className !== 'undefined'
      ) {
        if (CLASS_SAFELIST.has(node.className)) {
          result.push({
            start: offset,
            length: nodeLength,
            className: node.className,
          });
        }
        if (node.children.length) {
          result = result.concat(this._rangesFromElement(node, offset));
        }
      }
      offset += nodeLength;
    }
    return result;
  }

  notify() {
    if (this.baseLines?.length > 0) {
      this.notifyRange(1, this.baseLines?.length, Side.LEFT);
    }
    if (this.revisionLines?.length > 0) {
      this.notifyRange(1, this.revisionLines?.length, Side.RIGHT);
    }
  }

  notifyRange(start: number, end: number, side: Side) {
    console.log(
      `syntax-layer notifyRange ${start} ${end} ${side} ${this.listeners.length}`
    );
    for (const listener of this.listeners) {
      listener(start, end, side);
    }
  }
}
