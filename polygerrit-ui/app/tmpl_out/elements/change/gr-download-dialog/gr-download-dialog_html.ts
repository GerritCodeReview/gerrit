import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrDownloadDialog} from '../../../../elements/change/gr-download-dialog/gr-download-dialog';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrDownloadDialogCheck extends GrDownloadDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['h3'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    setTextContent(`
      Patch set ${this.patchNum} of ${this._computePatchSetQuantity(__f(this.change)!.revisions)}
    `);

    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('class', `${this._computeShowDownloadCommands(this._schemes)}`);
    }
    {
      const el: HTMLElementTagNameMap['gr-download-commands'] = null!;
      useVars(el);
      el.setAttribute('id', `downloadCommands`);
      el.commands = this._computeDownloadCommands(this.change, this.patchNum, this._selectedScheme);
      el.schemes = this._schemes;
      el.selectedScheme = this._selectedScheme;
      this._selectedScheme = el.selectedScheme;
      el.showKeyboardShortcutTooltips = true;
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('class', `flexContainer`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `patchFiles`);
      el.hidden = this._computeHidePatchFile(this.change, this.patchNum);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('id', `download`);
      el.setAttribute('href', `${this._computeDownloadLink(this.change, this.patchNum)}`);
    }
    setTextContent(`
          ${this._computeDownloadFilename(this.change, this.patchNum)}
        `);

    {
      const el: HTMLElementTagNameMap['a'] = null!;
      useVars(el);
      el.setAttribute('href', `${this._computeZipDownloadLink(this.change, this.patchNum)}`);
    }
    setTextContent(`
          ${this._computeZipDownloadFilename(this.change, this.patchNum)}
        `);

    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `archivesContainer`);
      el.setAttribute('hidden', `${!__f(__f(this.config)!.archives)!.length}`);
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `archives`);
      el.setAttribute('class', `archives`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const format of __f(this.config)!.archives!)
      {
        {
          const el: HTMLElementTagNameMap['a'] = null!;
          useVars(el);
          el.setAttribute('href', `${this._computeArchiveDownloadLink(this.change, this.patchNum, format)}`);
        }
        setTextContent(`
            ${format}
          `);

      }
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
      el.setAttribute('class', `footer`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `closeButtonContainer`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `closeButton`);
      el.link = true;
      el.addEventListener('click', this._handleCloseTap.bind(this));
    }
  }
}

