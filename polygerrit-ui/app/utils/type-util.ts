/**
 * Gets all properties of a Source that match a given Type. For example:
 *
 *   type BooleansOfHTMLElement = PropertiesOfType<HTMLElement, boolean>;
 *
 * will be 'draggable' | 'autofocus' | etc.
 */
export type PropertiesOfType<Source, Type> = {
  [K in keyof Source]: Source[K] extends Type ? K : never;
}[keyof Source];
