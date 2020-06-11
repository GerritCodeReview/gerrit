import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrKeyboardShortcutsDialog} from '../../../../elements/core/gr-keyboard-shortcuts-dialog/gr-keyboard-shortcuts-dialog';

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

export class GrKeyboardShortcutsDialogCheck extends GrKeyboardShortcutsDialog
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['header'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['h3'] = null!;
      useVars(el);
      el.setAttribute('class', `heading-3`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.link = true;
      el.addEventListener('click', this._handleCloseTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['main'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `column`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._left!)
      {
        {
          const el: HTMLElementTagNameMap['table'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['caption'] = null!;
          useVars(el);
        }
        setTextContent(`
            ${__f(item)!.section}
          `);

        {
          const el: HTMLElementTagNameMap['thead'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['th'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['th'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['tbody'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['dom-repeat'] = null!;
          useVars(el);
        }
        {
          const index = 0;
          const itemsIndexAs = 0;
          useVars(index, itemsIndexAs);
          for(const shortcut of __f(item)!.shortcuts!)
          {
            {
              const el: HTMLElementTagNameMap['tr'] = null!;
              useVars(el);
            }
            {
              const el: HTMLElementTagNameMap['td'] = null!;
              useVars(el);
            }
            {
              const el: HTMLElementTagNameMap['gr-key-binding-display'] = null!;
              useVars(el);
              el.binding = __f(shortcut)!.binding;
            }
            {
              const el: HTMLElementTagNameMap['td'] = null!;
              useVars(el);
            }
            setTextContent(`${__f(shortcut)!.text}`);

          }
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `column`);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._right!)
      {
        {
          const el: HTMLElementTagNameMap['table'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['caption'] = null!;
          useVars(el);
        }
        setTextContent(`
            ${__f(item)!.section}
          `);

        {
          const el: HTMLElementTagNameMap['thead'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['th'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['th'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['tbody'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['dom-repeat'] = null!;
          useVars(el);
        }
        {
          const index = 0;
          const itemsIndexAs = 0;
          useVars(index, itemsIndexAs);
          for(const shortcut of __f(item)!.shortcuts!)
          {
            {
              const el: HTMLElementTagNameMap['tr'] = null!;
              useVars(el);
            }
            {
              const el: HTMLElementTagNameMap['td'] = null!;
              useVars(el);
            }
            {
              const el: HTMLElementTagNameMap['gr-key-binding-display'] = null!;
              useVars(el);
              el.binding = __f(shortcut)!.binding;
            }
            {
              const el: HTMLElementTagNameMap['td'] = null!;
              useVars(el);
            }
            setTextContent(`${__f(shortcut)!.text}`);

          }
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['footer'] = null!;
      useVars(el);
    }
  }
}

