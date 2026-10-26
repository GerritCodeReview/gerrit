/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

export interface CommitMessage {
  subject: string;
  body: string[];
  footer: string[];
  hasTrailingBlankLine: boolean;
}

export interface FormattingError {
  type: ErrorType;
  line?: number;
  message: string;
}

export enum ErrorType {
  SUBJECT_TOO_LONG,
  LINE_TOO_LONG,
  MISSING_BLANK_LINE,
  EXTRA_BLANK_LINE,
  INVALID_INDENTATION,
  TRAILING_SPACES,
  COMMENT_LINE,
  LEADING_SPACES,
}

const MAX_SUBJECT_LENGTH = 72;
const MAX_LINE_LENGTH = 72;
const INDENTATION_THRESHOLD = 4;
const COMMENT_LINE_REGEX = /^\s*#\s/;
const LIST_ITEM_REGEX = /^(\s*(?:[-+*]|\d{1,3}[.)])\s+)(\S.*)$/;
const FOOTER_REGEX = /^([\w-]+)(?::[ \t]+|=)(.*)$/;

interface ListItem {
  prefix: string;
  lines: string[];
  lastRawLine: string;
}

function getLeadingSpacesCount(line: string): number {
  return line.length - line.trimStart().length;
}

function getListIndentThreshold(prefixLength: number): number {
  return Math.max(INDENTATION_THRESHOLD, prefixLength + 1);
}

/*
 * Check if last line of "Body" follows the "footer" format and if yes, then transfer it to the "footer section"
 * So if the commit message is
 * Foo
 *
 * Footer1: val1
 *
 * Footer2: val2
 *
 * Current message.footer will only contain val2 and the rest will be considered in the body
 * Note: This does not support multi-line footers.
 */

function portFootersFromBody(message: CommitMessage): CommitMessage {
  const footersToPortOver = [];
  for (let i = message.body.length - 1; i >= 0; i--) {
    const line = message.body[i];
    const match = line.match(FOOTER_REGEX);
    // If it's a footer line or an empty blank line, then we remove it from the body
    if (match || line.trim() === '') {
      if (line.trim() !== '') {
        footersToPortOver.push(line);
      }
      message.body.pop();
    } else {
      break;
    }
  }
  if (footersToPortOver.length > 0) {
    message.footer.unshift(...footersToPortOver.reverse());
  }
  return message;
}

function formatCommitMessage(message: CommitMessage): CommitMessage {
  const formattedSubject = formatSubject(message.subject);
  message = portFootersFromBody(message);
  const formattedBody = formatBody(message.body);
  const formattedFooter = formatFooter(message.footer);

  return {
    subject: formattedSubject,
    body: formattedBody,
    footer: formattedFooter,
    hasTrailingBlankLine: message.hasTrailingBlankLine,
  };
}

function formatSubject(subject: string): string {
  return subject.trim();
}

function formatBody(body: string[]): string[] {
  let inCodeBlock = false;
  let paragraphLines: string[] = [];
  let currentListItem: ListItem | null = null;
  const formattedBody: string[] = [];
  let previousWasEmpty = true; // Track if previous line was empty

  const flushParagraph = () => {
    if (paragraphLines.length > 0) {
      formattedBody.push(...splitParagraph(paragraphLines.join(' ')));
      paragraphLines = [];
    }
  };

  const flushListItem = () => {
    if (currentListItem !== null) {
      if (
        currentListItem.lines.length === 1 &&
        currentListItem.lastRawLine.length <= MAX_LINE_LENGTH
      ) {
        formattedBody.push(currentListItem.lastRawLine);
      } else {
        formattedBody.push(
          ...splitLinesWithIndent(
            currentListItem.lines.join(' '),
            currentListItem.prefix,
            ' '.repeat(currentListItem.prefix.length)
          )
        );
      }
      currentListItem = null;
    }
  };

  for (let i = 0; i < body.length; i++) {
    const line = body[i];
    if (line.trim().startsWith('```')) {
      flushParagraph();
      flushListItem();
      inCodeBlock = !inCodeBlock;
      formattedBody.push(line.trimEnd());
      previousWasEmpty = false;
      continue;
    }

    if (inCodeBlock) {
      formattedBody.push(line.trimEnd());
      previousWasEmpty = false;
      continue;
    }

    if (line.trim() === '') {
      flushParagraph();
      flushListItem();
      formattedBody.push('');
      previousWasEmpty = true;
      continue;
    }

    const indentThreshold: number =
      currentListItem !== null
        ? getListIndentThreshold(currentListItem.prefix.length)
        : INDENTATION_THRESHOLD;

    const leadingSpaces = getLeadingSpacesCount(line);
    const listMatch =
      leadingSpaces < indentThreshold &&
      canStartListItem(paragraphLines, leadingSpaces, line, body[i + 1])
        ? line.trimEnd().match(LIST_ITEM_REGEX)
        : null;
    if (listMatch) {
      flushParagraph();
      flushListItem();
      currentListItem = {
        prefix: listMatch[1],
        lines: [listMatch[2].trim()],
        lastRawLine: line.trimEnd(),
      };
      previousWasEmpty = false;
      continue;
    }

    if (isUntouchedCodeOrQuoteLine(line, indentThreshold)) {
      flushParagraph();
      flushListItem();
      formattedBody.push(line.trimEnd());
      previousWasEmpty = false;
      continue;
    }

    if (currentListItem !== null) {
      if (
        leadingSpaces > 0 ||
        isUnindentedListContinuation(currentListItem, line)
      ) {
        currentListItem.lines.push(line.trim());
        currentListItem.lastRawLine = line.trimEnd();
        previousWasEmpty = false;
        continue;
      }
      flushListItem();
    }

    const nextLine = body[i + 1];
    const nextLineIsIndented =
      nextLine !== undefined &&
      nextLine.trim() !== '' &&
      getLeadingSpacesCount(nextLine) > 0;

    if (
      leadingSpaces > 0 &&
      leadingSpaces < INDENTATION_THRESHOLD &&
      (!previousWasEmpty || nextLineIsIndented)
    ) {
      flushParagraph();
      formattedBody.push(...formatIndentedLine(line, leadingSpaces));
      previousWasEmpty = false;
      continue;
    }

    paragraphLines.push(line.trim());
    previousWasEmpty = false;
  }

  flushParagraph();
  flushListItem();

  return removeConsecutiveBlankLines(formattedBody);
}

function canStartListItem(
  paragraphLines: string[],
  leadingSpaces: number,
  line: string,
  nextLine: string | undefined
): boolean {
  if (paragraphLines.length === 0 || leadingSpaces > 0) {
    return true;
  }
  const backtickCount = (paragraphLines.join(' ').match(/`/g) || []).length;
  if (backtickCount % 2 !== 0) {
    return false;
  }
  const lastParagraphLine = paragraphLines[paragraphLines.length - 1];
  const firstWord = line.trim().split(/\s+/)[0] || '';
  if (
    lastParagraphLine.length + 1 + firstWord.length > 60 &&
    !/[.:;!?)]$/.test(lastParagraphLine)
  ) {
    const nextIsUnindentedNonList =
      nextLine !== undefined &&
      nextLine.trim() !== '' &&
      getLeadingSpacesCount(nextLine) === 0 &&
      !LIST_ITEM_REGEX.test(nextLine.trimEnd());
    if (nextIsUnindentedNonList) {
      return false;
    }
  }
  return true;
}

function isUnindentedListContinuation(
  listItem: ListItem,
  line: string
): boolean {
  if (getLeadingSpacesCount(listItem.prefix) > 0) {
    return false;
  }
  const trimmedLine = line.trim();
  if (
    (trimmedLine.endsWith(':') && /^[A-Z]/.test(trimmedLine)) ||
    FOOTER_REGEX.test(trimmedLine)
  ) {
    return false;
  }
  const lastRawLine = listItem.lastRawLine;
  if (lastRawLine.includes('://')) {
    return false;
  }
  const firstWord = trimmedLine.split(/\s+/)[0] || '';
  return (
    lastRawLine.length + 1 + firstWord.length > 60 &&
    !/[.:;!?)\]"'`]$/.test(lastRawLine)
  );
}

function formatIndentedLine(line: string, leadingSpaces: number): string[] {
  const trimmedEnd = line.trimEnd();
  if (
    trimmedEnd.length <= MAX_LINE_LENGTH ||
    trimmedEnd.includes('://') ||
    /^[\w-]+:\s+\S+$/.test(line.trim())
  ) {
    return [trimmedEnd];
  }
  const indent = ' '.repeat(leadingSpaces);
  return splitLinesWithIndent(line.trim(), indent, indent);
}

function formatFooter(footer: string[]): string[] {
  const formattedFooter = footer.map(line => line.trim());
  return removeConsecutiveBlankLines(formattedFooter);
}

/**
 * Returns true if the line is a quote, comment, or indented code line that
 * should not be modified by the formatter.
 */
function isUntouchedCodeOrQuoteLine(
  line: string,
  indentThreshold = INDENTATION_THRESHOLD
): boolean {
  if (line.trimStart().startsWith('> ') || COMMENT_LINE_REGEX.test(line)) {
    return true;
  }
  return getLeadingSpacesCount(line) >= indentThreshold;
}

function splitParagraph(paragraph: string): string[] {
  return splitLinesWithIndent(paragraph);
}

function splitLinesWithIndent(
  text: string,
  firstLinePrefix = '',
  continuationPrefix = firstLinePrefix
): string[] {
  const words = text.trim().split(/\s+/);
  if (words.length === 0 || (words.length === 1 && words[0] === '')) {
    return [];
  }
  const lines: string[] = [];
  let currentPrefix = firstLinePrefix;
  let currentContent = '';

  for (const word of words) {
    const maxContentLength = Math.max(
      1,
      MAX_LINE_LENGTH - currentPrefix.length
    );
    if (word.length > maxContentLength) {
      if (currentContent.length > 0) {
        lines.push(currentPrefix + currentContent);
        currentPrefix = continuationPrefix;
        currentContent = '';
      }
      lines.push(currentPrefix + word);
      currentPrefix = continuationPrefix;
    } else if (
      currentContent.length > 0 &&
      currentContent.length + word.length + 1 > maxContentLength
    ) {
      lines.push(currentPrefix + currentContent);
      currentPrefix = continuationPrefix;
      currentContent = word;
    } else {
      currentContent += (currentContent ? ' ' : '') + word;
    }
  }

  if (currentContent) {
    lines.push(currentPrefix + currentContent);
  }

  return lines;
}

function removeConsecutiveBlankLines(lines: string[]): string[] {
  const filteredLines: string[] = [];
  let previousLineBlank = false;

  for (const line of lines) {
    const isBlank = line.trim() === '';
    if (!isBlank || !previousLineBlank) {
      filteredLines.push(line);
    }
    previousLineBlank = isBlank;
  }

  // Remove leading and trailing blank lines
  while (filteredLines.length > 0 && filteredLines[0].trim() === '') {
    filteredLines.shift();
  }
  while (
    filteredLines.length > 0 &&
    filteredLines[filteredLines.length - 1].trim() === ''
  ) {
    filteredLines.pop();
  }
  return filteredLines;
}

function detectFormattingErrors(
  message: CommitMessage,
  messageString: string
): FormattingError[] {
  const errors: FormattingError[] = [];

  // Check subject
  if (message.subject.length > MAX_SUBJECT_LENGTH) {
    errors.push({
      type: ErrorType.SUBJECT_TOO_LONG,
      line: 1,
      message: `Subject exceeds ${MAX_SUBJECT_LENGTH} characters`,
    });
  }

  if (message.subject.startsWith(' ')) {
    errors.push({
      type: ErrorType.LEADING_SPACES,
      line: 1,
      message: 'Subject should not start with spaces',
    });
  }

  if (message.subject.endsWith(' ')) {
    errors.push({
      type: ErrorType.TRAILING_SPACES,
      line: 1,
      message: 'Subject should not end with spaces',
    });
  }

  const lines = messageString.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].trim().startsWith('#')) {
      errors.push({
        type: ErrorType.COMMENT_LINE,
        line: i + 1, // Line numbers are 1-based
        message:
          "'#' at line start is a comment marker in Git. Line will be ignored",
      });
    }
  }

  // Check for extra blank lines using the raw messageString
  for (let i = 0; i < lines.length; i++) {
    if (i > 0 && lines[i].trim() === '' && lines[i - 1].trim() === '') {
      const isBetweenSubjectAndBody = i === 1 && message.body.length > 0;
      const isBetweenBodyAndFooter =
        i === message.body.length + (message.subject ? 1 : 0) &&
        message.footer.length > 0;
      const isAtEndOfFooter =
        i === lines.length - 1 && message.footer.length > 0;

      if (
        !isBetweenSubjectAndBody &&
        !isBetweenBodyAndFooter &&
        !isAtEndOfFooter
      ) {
        errors.push({
          type: ErrorType.EXTRA_BLANK_LINE,
          line: i + 1, // Line numbers are 1-based
          message: 'Consecutive blank lines are not allowed',
        });
      }
    }
  }

  // Check body
  let lineNumber = 3;
  let inCodeBlock = false;
  let currentListPrefixLength: number | null = null;

  for (const line of message.body) {
    if (line.trim().startsWith('```')) {
      inCodeBlock = !inCodeBlock;
      currentListPrefixLength = null;
    } else if (line.trim() === '') {
      currentListPrefixLength = null;
    } else if (!inCodeBlock) {
      const prevIndentThreshold: number =
        currentListPrefixLength !== null
          ? getListIndentThreshold(currentListPrefixLength)
          : INDENTATION_THRESHOLD;
      const listMatch =
        getLeadingSpacesCount(line) < prevIndentThreshold
          ? line.trimEnd().match(LIST_ITEM_REGEX)
          : null;
      if (listMatch) {
        currentListPrefixLength = listMatch[1].length;
      } else if (
        currentListPrefixLength !== null &&
        isUntouchedCodeOrQuoteLine(line, prevIndentThreshold)
      ) {
        currentListPrefixLength = null;
      } else if (getLeadingSpacesCount(line) === 0) {
        currentListPrefixLength = null;
      }
    }

    const indentThreshold =
      currentListPrefixLength !== null
        ? getListIndentThreshold(currentListPrefixLength)
        : INDENTATION_THRESHOLD;

    if (
      !inCodeBlock &&
      !isUntouchedCodeOrQuoteLine(line, indentThreshold) &&
      line.length > MAX_LINE_LENGTH &&
      !line.includes('://') // Don't flag long URLs
    ) {
      errors.push({
        type: ErrorType.LINE_TOO_LONG,
        line: lineNumber,
        message: `Line exceeds ${MAX_LINE_LENGTH} characters`,
      });
    }
    if (line.endsWith(' ')) {
      errors.push({
        type: ErrorType.TRAILING_SPACES,
        line: lineNumber,
        message: 'Line should not end with spaces',
      });
    }
    lineNumber++;
  }

  // Check footer
  lineNumber = message.body.length + 4;
  for (const line of message.footer) {
    if (line.trim().startsWith('```')) {
      inCodeBlock = !inCodeBlock;
    }

    if (
      !inCodeBlock &&
      !isUntouchedCodeOrQuoteLine(line) &&
      line.length > MAX_LINE_LENGTH &&
      !line.includes('://') // Don't flag long URLs
    ) {
      errors.push({
        type: ErrorType.LINE_TOO_LONG,
        line: lineNumber,
        message: `Line exceeds ${MAX_LINE_LENGTH} characters`,
      });
    }
    if (line.endsWith(' ')) {
      errors.push({
        type: ErrorType.TRAILING_SPACES,
        line: lineNumber,
        message: 'Line should not end with spaces',
      });
    }
    if (line.startsWith(' ')) {
      errors.push({
        type: ErrorType.LEADING_SPACES,
        line: lineNumber,
        message: 'Line should not start with spaces',
      });
    }
    lineNumber++;
  }

  return errors;
}

export function parseCommitMessageString(messageString: string): CommitMessage {
  const lines = messageString.split('\n');
  // Remove leading blank lines
  while (lines.length > 0 && lines[0].trim() === '') {
    lines.shift();
  }

  let subject = '';
  let body: string[] = [];
  let footer: string[] = [];
  let hasTrailingBlankLine = false;

  if (lines.length === 0) {
    return {subject, body, footer, hasTrailingBlankLine}; // Handle empty input
  }

  if (lines.length === 1) {
    subject = lines[0];
    return {subject, body, footer, hasTrailingBlankLine}; // Single line case
  }

  subject = lines[0]; // Subject is always the first line
  hasTrailingBlankLine =
    lines.length > 0 && lines[lines.length - 1].trim() === '';

  const footerStartIndex = findStartOfParagraph(
    lines,
    hasTrailingBlankLine ? lines.length - 2 : lines.length - 1
  );

  footer = lines.slice(footerStartIndex, lines.length);
  if (hasTrailingBlankLine) {
    footer.pop();
  }

  // Extract body lines, removing all leading/trailing blank lines
  body = lines.slice(
    firstNonEmptyLineIndex(lines, 1, /* direction */ 1),
    firstNonEmptyLineIndex(lines, footerStartIndex - 1, /* direction */ -1) + 1
  );

  // Check if footer contains any lines in the format "key: value"
  // If not, move footer lines to body and make footer empty
  // This is typically the case when creating a new commit message and the footer is not yet formatted
  const isListOrIndentedBlock =
    footer.some(
      line =>
        getLeadingSpacesCount(line) < INDENTATION_THRESHOLD &&
        LIST_ITEM_REGEX.test(line.trimEnd())
    ) ||
    footer.every(line => isUntouchedCodeOrQuoteLine(line)) ||
    (footer.length > 1 &&
      footer.every(line => getLeadingSpacesCount(line) > 0)) ||
    (footer.length > 1 &&
      footer[0].trim().endsWith(':') &&
      !FOOTER_REGEX.test(footer[0].trim()));
  const hasFormattedFooterLine = footer.some(line =>
    isListOrIndentedBlock
      ? FOOTER_REGEX.test(line.trimEnd())
      : FOOTER_REGEX.test(line.trim())
  );
  if (!hasFormattedFooterLine && footer.length > 0) {
    // If body is not empty, add a blank line before appending footer
    if (body.length > 0) {
      body.push('');
    }
    body = body.concat(footer);
    footer = [];
  }

  return {subject, body, footer, hasTrailingBlankLine};
}

function findStartOfParagraph(
  lines: string[],
  lastLineInParagraph: number
): number {
  for (let i = lastLineInParagraph; i >= 0; i--) {
    if (lines[i].trim() === '') {
      return i + 1;
    }
  }
  // on line 0 is subject
  return 1;
}

/**
 * Returns the index of the first non-empty line in the given direction.
 *
 * @param lines The lines of the commit message.
 * @param index The starting index.
 * @param direction The direction to search in (1 for forward, -1 for backward).
 * @return The index of the first non-empty line, or the index of the last line if no non-empty lines are found.
 */
function firstNonEmptyLineIndex(
  lines: string[],
  index: number,
  direction: number
): number {
  while (index >= 0 && index < lines.length && lines[index].trim() === '') {
    index += direction;
  }
  return index;
}

function formatCommitMessageToString(message: CommitMessage): string {
  let result = message.subject;
  if (message.body.length > 0) {
    result += '\n\n' + message.body.join('\n');
  }
  if (message.footer.length > 0) {
    result += '\n\n' + message.footer.join('\n');
  }
  if (message.hasTrailingBlankLine) {
    result += '\n';
  }
  return result;
}

export function formatCommitMessageString(messageString: string): string {
  const commitMessage = parseCommitMessageString(messageString);
  const formattedMessage = formatCommitMessage(commitMessage);
  return formatCommitMessageToString(formattedMessage);
}

export function detectFormattingErrorsInString(
  messageString: string
): FormattingError[] {
  const commitMessage = parseCommitMessageString(messageString);
  return detectFormattingErrors(commitMessage, messageString);
}

export const TEST_ONLY = {parseCommitMessageString};
