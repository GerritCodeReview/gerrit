import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrDownloadCommands} from '../../../../elements/shared/gr-download-commands/gr-download-commands';

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

export class GrDownloadCommandsCheck extends GrDownloadCommands
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `schemes`);
    }
    {
      const el: HTMLElementTagNameMap['paper-tabs'] = null!;
      useVars(el);
      el.setAttribute('id', `downloadTabs`);
      el.setAttribute('class', `${this._computeShowTabs(this.schemes)}`);
      el.selected = this._computeSelected(this.schemes, this.selectedScheme);
      el.addEventListener('selected-changed', this._handleTabChange.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const scheme of this.schemes!)
      {
        {
          const el: HTMLElementTagNameMap['paper-tab'] = null!;
          useVars(el);
          el.setAttribute('dataScheme', `${scheme}`);
        }
        setTextContent(`${scheme}`);

      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `commands`);
      el.setAttribute('hidden', `${!__f(this.schemes)!.length}`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const command of this.commands!)
      {
        {
          const el: HTMLElementTagNameMap['gr-shell-command'] = null!;
          useVars(el);
          el.setAttribute('class', `${this._computeClass(__f(command)!.title)}`);
          el.label = __f(command)!.title;
          el.command = __f(command)!.command;
          el.tooltip = this._computeTooltip(this.showKeyboardShortcutTooltips, index);
        }
      }
    }
  }
}

