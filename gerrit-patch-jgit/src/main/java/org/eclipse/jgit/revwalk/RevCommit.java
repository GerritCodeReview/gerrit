/*
 * Copyright (C) 2008-2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.revwalk;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.MutableObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.util.RawParseUtils;

/** A commit reference to a commit in the DAG. */
public class RevCommit extends RevObject {
	/**
	 * Parse a commit from its canonical format.
	 *
	 * This method constructs a temporary revision pool, parses the commit as
	 * supplied, and returns it to the caller. Since the commit was built inside
	 * of a private revision pool its parent pointers will be initialized, but
	 * will not have their headers loaded.
	 *
	 * Applications are discouraged from using this API. Callers usually need
	 * more than one commit. Use {@link RevWalk#parseCommit(AnyObjectId)} to
	 * obtain a RevCommit from an existing repository.
	 *
	 * @param raw
	 *            the canonical formatted commit to be parsed.
	 * @return the parsed commit, in an isolated revision pool that is not
	 *         available to the caller.
	 */
	public static RevCommit parse(byte[] raw) {
		return parse(new RevWalk((ObjectReader) null), raw);
	}

	/**
	 * Parse a commit from its canonical format.
	 *
	 * This method inserts the commit directly into the caller supplied revision
	 * pool, making it appear as though the commit exists in the repository,
	 * even if it doesn't.  The repository under the pool is not affected.
	 *
	 * @param rw
	 *            the revision pool to allocate the commit within. The commit's
	 *            tree and parent pointers will be obtained from this pool.
	 * @param raw
	 *            the canonical formatted commit to be parsed.
	 * @return the parsed commit, in an isolated revision pool that is not
	 *         available to the caller.
	 */
	public static RevCommit parse(RevWalk rw, byte[] raw) {
		ObjectInserter.Formatter fmt = new ObjectInserter.Formatter();
		boolean retain = rw.isRetainBody();
		rw.setRetainBody(true);
		RevCommit r = rw.lookupCommit(fmt.idFor(Constants.OBJ_COMMIT, raw));
		r.parseCanonical(rw, raw);
		rw.setRetainBody(retain);
		return r;
	}

	static final RevCommit[] NO_PARENTS = {};

	private RevTree tree;

	RevCommit[] parents;

	int commitTime; // An int here for performance, overflows in 2038

	int inDegree;

	private byte[] buffer;

	/**
	 * Create a new commit reference.
	 *
	 * @param id
	 *            object name for the commit.
	 */
	protected RevCommit(final AnyObjectId id) {
		super(id);
	}

	@Override
	void parseHeaders(final RevWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		parseCanonical(walk, walk.getCachedBytes(this));
	}

	@Override
	void parseBody(final RevWalk walk) throws MissingObjectException,
			IncorrectObjectTypeException, IOException {
		if (buffer == null) {
			buffer = walk.getCachedBytes(this);
			if ((flags & PARSED) == 0)
				parseCanonical(walk, buffer);
		}
	}

	void parseCanonical(final RevWalk walk, final byte[] raw) {
		final MutableObjectId idBuffer = walk.idBuffer;
		idBuffer.fromString(raw, 5);
		tree = walk.lookupTree(idBuffer);

		int ptr = 46;
		if (parents == null) {
			RevCommit[] pList = new RevCommit[1];
			int nParents = 0;
			for (;;) {
				if (raw[ptr] != 'p')
					break;
				idBuffer.fromString(raw, ptr + 7);
				final RevCommit p = walk.lookupCommit(idBuffer);
				if (nParents == 0)
					pList[nParents++] = p;
				else if (nParents == 1) {
					pList = new RevCommit[] { pList[0], p };
					nParents = 2;
				} else {
					if (pList.length <= nParents) {
						RevCommit[] old = pList;
						pList = new RevCommit[pList.length + 32];
						System.arraycopy(old, 0, pList, 0, nParents);
					}
					pList[nParents++] = p;
				}
				ptr += 48;
			}
			if (nParents != pList.length) {
				RevCommit[] old = pList;
				pList = new RevCommit[nParents];
				System.arraycopy(old, 0, pList, 0, nParents);
			}
			parents = pList;
		}

		// extract time from "committer "
		ptr = RawParseUtils.committer(raw, ptr);
		if (ptr > 0) {
			ptr = RawParseUtils.nextLF(raw, ptr, '>');

			// In 2038 commitTime will overflow unless it is changed to long.
			commitTime = RawParseUtils.parseBase10(raw, ptr, null);
		}

		if (walk.isRetainBody())
			buffer = raw;
		flags |= PARSED;
	}

	@Override
	public final int getType() {
		return Constants.OBJ_COMMIT;
	}

	static void carryFlags(RevCommit c, final int carry) {
		for (;;) {
			final RevCommit[] pList = c.parents;
			if (pList == null)
				return;
			final int n = pList.length;
			if (n == 0)
				return;

			for (int i = 1; i < n; i++) {
				final RevCommit p = pList[i];
				if ((p.flags & carry) == carry)
					continue;
				p.flags |= carry;
				carryFlags(p, carry);
			}

			c = pList[0];
			if ((c.flags & carry) == carry)
				return;
			c.flags |= carry;
		}
	}

	/**
	 * Carry a RevFlag set on this commit to its parents.
	 * <p>
	 * If this commit is parsed, has parents, and has the supplied flag set on
	 * it we automatically add it to the parents, grand-parents, and so on until
	 * an unparsed commit or a commit with no parents is discovered. This
	 * permits applications to force a flag through the history chain when
	 * necessary.
	 *
	 * @param flag
	 *            the single flag value to carry back onto parents.
	 */
	public void carry(final RevFlag flag) {
		final int carry = flags & flag.mask;
		if (carry != 0)
			carryFlags(this, carry);
	}

	/**
	 * Time from the "committer " line of the buffer.
	 *
	 * @return time, expressed as seconds since the epoch.
	 */
	public final int getCommitTime() {
		return commitTime;
	}

	/**
	 * Get a reference to this commit's tree.
	 *
	 * @return tree of this commit.
	 */
	public final RevTree getTree() {
		return tree;
	}

	/**
	 * Get the number of parent commits listed in this commit.
	 *
	 * @return number of parents; always a positive value but can be 0.
	 */
	public final int getParentCount() {
		return parents.length;
	}

	/**
	 * Get the nth parent from this commit's parent list.
	 *
	 * @param nth
	 *            parent index to obtain. Must be in the range 0 through
	 *            {@link #getParentCount()}-1.
	 * @return the specified parent.
	 * @throws ArrayIndexOutOfBoundsException
	 *             an invalid parent index was specified.
	 */
	public final RevCommit getParent(final int nth) {
		return parents[nth];
	}

	/**
	 * Obtain an array of all parents (<b>NOTE - THIS IS NOT A COPY</b>).
	 * <p>
	 * This method is exposed only to provide very fast, efficient access to
	 * this commit's parent list. Applications relying on this list should be
	 * very careful to ensure they do not modify its contents during their use
	 * of it.
	 *
	 * @return the array of parents.
	 */
	public final RevCommit[] getParents() {
		return parents;
	}

	/**
	 * Obtain the raw unparsed commit body (<b>NOTE - THIS IS NOT A COPY</b>).
	 * <p>
	 * This method is exposed only to provide very fast, efficient access to
	 * this commit's message buffer within a RevFilter. Applications relying on
	 * this buffer should be very careful to ensure they do not modify its
	 * contents during their use of it.
	 *
	 * @return the raw unparsed commit body. This is <b>NOT A COPY</b>.
	 *         Altering the contents of this buffer may alter the walker's
	 *         knowledge of this commit, and the results it produces.
	 */
	public final byte[] getRawBuffer() {
		return buffer;
	}

	/**
	 * Parse the author identity from the raw buffer.
	 * <p>
	 * This method parses and returns the content of the author line, after
	 * taking the commit's character set into account and decoding the author
	 * name and email address. This method is fairly expensive and produces a
	 * new PersonIdent instance on each invocation. Callers should invoke this
	 * method only if they are certain they will be outputting the result, and
	 * should cache the return value for as long as necessary to use all
	 * information from it.
	 * <p>
	 * RevFilter implementations should try to use {@link RawParseUtils} to scan
	 * the {@link #getRawBuffer()} instead, as this will allow faster evaluation
	 * of commits.
	 *
	 * @return identity of the author (name, email) and the time the commit was
	 *         made by the author; null if no author line was found.
	 */
	public final PersonIdent getAuthorIdent() {
		final byte[] raw = buffer;
		final int nameB = RawParseUtils.author(raw, 0);
		if (nameB < 0)
			return null;
		return RawParseUtils.parsePersonIdent(raw, nameB);
	}

	/**
	 * Parse the committer identity from the raw buffer.
	 * <p>
	 * This method parses and returns the content of the committer line, after
	 * taking the commit's character set into account and decoding the committer
	 * name and email address. This method is fairly expensive and produces a
	 * new PersonIdent instance on each invocation. Callers should invoke this
	 * method only if they are certain they will be outputting the result, and
	 * should cache the return value for as long as necessary to use all
	 * information from it.
	 * <p>
	 * RevFilter implementations should try to use {@link RawParseUtils} to scan
	 * the {@link #getRawBuffer()} instead, as this will allow faster evaluation
	 * of commits.
	 *
	 * @return identity of the committer (name, email) and the time the commit
	 *         was made by the committer; null if no committer line was found.
	 */
	public final PersonIdent getCommitterIdent() {
		final byte[] raw = buffer;
		final int nameB = RawParseUtils.committer(raw, 0);
		if (nameB < 0)
			return null;
		return RawParseUtils.parsePersonIdent(raw, nameB);
	}

	/**
	 * Parse the complete commit message and decode it to a string.
	 * <p>
	 * This method parses and returns the message portion of the commit buffer,
	 * after taking the commit's character set into account and decoding the
	 * buffer using that character set. This method is a fairly expensive
	 * operation and produces a new string on each invocation.
	 *
	 * @return decoded commit message as a string. Never null.
	 */
	public final String getFullMessage() {
		final byte[] raw = buffer;
		final int msgB = RawParseUtils.commitMessage(raw, 0);
		if (msgB < 0)
			return "";
		final Charset enc = RawParseUtils.parseEncoding(raw);
		return RawParseUtils.decode(enc, raw, msgB, raw.length);
	}

	/**
	 * Parse the commit message and return the first "line" of it.
	 * <p>
	 * The first line is everything up to the first pair of LFs. This is the
	 * "oneline" format, suitable for output in a single line display.
	 * <p>
	 * This method parses and returns the message portion of the commit buffer,
	 * after taking the commit's character set into account and decoding the
	 * buffer using that character set. This method is a fairly expensive
	 * operation and produces a new string on each invocation.
	 *
	 * @return decoded commit message as a string. Never null. The returned
	 *         string does not contain any LFs, even if the first paragraph
	 *         spanned multiple lines. Embedded LFs are converted to spaces.
	 */
	public final String getShortMessage() {
		final byte[] raw = buffer;
		final int msgB = RawParseUtils.commitMessage(raw, 0);
		if (msgB < 0)
			return "";

		final Charset enc = RawParseUtils.parseEncoding(raw);
		final int msgE = RawParseUtils.endOfParagraph(raw, msgB);
		String str = RawParseUtils.decode(enc, raw, msgB, msgE);
		if (hasLF(raw, msgB, msgE))
			str = str.replace('\n', ' ');
		return str;
	}

	static boolean hasLF(final byte[] r, int b, final int e) {
		while (b < e)
			if (r[b++] == '\n')
				return true;
		return false;
	}

	/**
	 * Determine the encoding of the commit message buffer.
	 * <p>
	 * Locates the "encoding" header (if present) and then returns the proper
	 * character set to apply to this buffer to evaluate its contents as
	 * character data.
	 * <p>
	 * If no encoding header is present, {@link Constants#CHARSET} is assumed.
	 *
	 * @return the preferred encoding of {@link #getRawBuffer()}.
	 */
	public final Charset getEncoding() {
		return RawParseUtils.parseEncoding(buffer);
	}

	private static final FooterKey GIT_SVN_ID = new FooterKey("git-svn-id");
	/**
	 * Parse the footer lines (e.g. "Signed-off-by") for machine processing.
	 * <p>
	 * This method splits all of the footer lines out of the last paragraph of
	 * the commit message, providing each line as a key-value pair, ordered by
	 * the order of the line's appearance in the commit message itself.
	 * <p>
	 * A footer line's key must match the pattern {@code ^[A-Za-z0-9-]+:}, while
	 * the value is free-form, but must not contain an LF. Very common keys seen
	 * in the wild are:
	 * <ul>
	 * <li>{@code Signed-off-by} (agrees to Developer Certificate of Origin)
	 * <li>{@code Acked-by} (thinks change looks sane in context)
	 * <li>{@code Reported-by} (originally found the issue this change fixes)
	 * <li>{@code Tested-by} (validated change fixes the issue for them)
	 * <li>{@code CC}, {@code Cc} (copy on all email related to this change)
	 * <li>{@code Bug} (link to project's bug tracking system)
	 * </ul>
	 *
	 * @return ordered list of footer lines; empty list if no footers found.
	 */
	public final List<FooterLine> getFooterLines() {
		final byte[] raw = buffer;
		int ptr = raw.length - 1;
		final int msgB = RawParseUtils.commitMessage(raw, 0);
		final ArrayList<FooterLine> r = new ArrayList<FooterLine>(4);
		final Charset enc = getEncoding();

		boolean done = false;

		while (!done) {
			while (raw[ptr] == '\n') // trim any trailing LFs, not interesting
				ptr--;

			for (;;) {
				ptr = RawParseUtils.prevLF(raw, ptr);
				if (ptr <= msgB) {
					done = true;
					break; // Don't parse commit headers as footer lines.
				}

				final int keyStart = ptr + 2;
				if (raw[keyStart] == '\n')
					break; // Stop at first paragraph break, no footers above it.

				final int keyEnd = RawParseUtils.endOfFooterLineKey(raw, keyStart);
				if (keyEnd < 0)
					continue; // Not a well formed footer line, skip it.

				// Skip over the ': *' at the end of the key before the value.
				//
				int valStart = keyEnd + 1;
				while (valStart < raw.length && raw[valStart] == ' ')
					valStart++;

				// Value ends at the LF, and does not include it.
				//
				int valEnd = RawParseUtils.nextLF(raw, valStart);
				if (raw[valEnd - 1] == '\n')
					valEnd--;

				r.add(new FooterLine(raw, enc, keyStart, keyEnd, valStart, valEnd));
			}

			// If the paragraph consists only of a git-svn-id, then try again to
			// collect the next paragraph, otherwise stop.
			if (!(r.size() == 1 && r.get(0).matches(GIT_SVN_ID))) {
				done = true;
			}
		}
		Collections.reverse(r);
		return r;
	}

	/**
	 * Get the values of all footer lines with the given key.
	 *
	 * @param keyName
	 *            footer key to find values of, case insensitive.
	 * @return values of footers with key of {@code keyName}, ordered by their
	 *         order of appearance. Duplicates may be returned if the same
	 *         footer appeared more than once. Empty list if no footers appear
	 *         with the specified key, or there are no footers at all.
	 * @see #getFooterLines()
	 */
	public final List<String> getFooterLines(final String keyName) {
		return getFooterLines(new FooterKey(keyName));
	}

	/**
	 * Get the values of all footer lines with the given key.
	 *
	 * @param keyName
	 *            footer key to find values of, case insensitive.
	 * @return values of footers with key of {@code keyName}, ordered by their
	 *         order of appearance. Duplicates may be returned if the same
	 *         footer appeared more than once. Empty list if no footers appear
	 *         with the specified key, or there are no footers at all.
	 * @see #getFooterLines()
	 */
	public final List<String> getFooterLines(final FooterKey keyName) {
		final List<FooterLine> src = getFooterLines();
		if (src.isEmpty())
			return Collections.emptyList();
		final ArrayList<String> r = new ArrayList<String>(src.size());
		for (final FooterLine f : src) {
			if (f.matches(keyName))
				r.add(f.getValue());
		}
		return r;
	}

	/**
	 * Reset this commit to allow another RevWalk with the same instances.
	 * <p>
	 * Subclasses <b>must</b> call <code>super.reset()</code> to ensure the
	 * basic information can be correctly cleared out.
	 */
	public void reset() {
		inDegree = 0;
	}

	final void disposeBody() {
		buffer = null;
	}

	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder();
		s.append(Constants.typeString(getType()));
		s.append(' ');
		s.append(name());
		s.append(' ');
		s.append(commitTime);
		s.append(' ');
		appendCoreFlags(s);
		return s.toString();
	}
}
