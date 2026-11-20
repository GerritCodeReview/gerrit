// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.gpg;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.google.gerrit.server.git.ServerCommitSigner;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;

/** In-process OpenPGP signing; no external executable or unsigned failure fallback. */
@Singleton
class GerritServerCommitSigner implements ServerCommitSigner {
  private final ServerSigningKeys keys;

  @Inject
  GerritServerCommitSigner(ServerSigningKeys keys) {
    this.keys = keys;
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  public byte[] signIfUnsigned(byte[] rawCommit) throws IOException {
    ServerSigningKeys.ActiveKey active = keys.activeKey();
    if (active == null) {
      return rawCommit;
    }
    try {
      var payload = GerritCommitSignatureVerifier.extract(rawCommit);
      if (payload.signature() != null || payload.unsupportedHeader()) {
        return rawCommit;
      }
    } catch (IllegalArgumentException e) {
      // The independent policy check reports malformed/duplicate headers; never replace them.
      return rawCommit;
    }
    Instant time = TimeUtil.now();
    if (!keys.isActiveKeyValid(time)) {
      throw new IOException("The active server signing key is no longer uniquely eligible");
    }
    try {
      var generator =
          new PGPSignatureGenerator(
              new BcPGPContentSignerBuilder(
                  active.publicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
      generator.init(PGPSignature.BINARY_DOCUMENT, active.privateKey());
      var packets = new PGPSignatureSubpacketGenerator();
      packets.setSignatureCreationTime(false, Date.from(time));
      packets.setIssuerFingerprint(false, active.publicKey());
      generator.setHashedSubpackets(packets.generate());
      generator.update(rawCommit);
      var buffer = new ByteArrayOutputStream();
      try (var armor = new ArmoredOutputStream(buffer)) {
        generator.generate().encode(armor);
      }
      return addSignature(rawCommit, buffer.toString(US_ASCII));
    } catch (PGPException | IllegalArgumentException e) {
      throw new IOException("Cannot sign server-generated commit", e);
    }
  }

  private static byte[] addSignature(byte[] rawCommit, String armor) throws IOException {
    for (int i = 0; i + 1 < rawCommit.length; i++) {
      if (rawCommit[i] == '\n' && rawCommit[i + 1] == '\n') {
        var result = new ByteArrayOutputStream(rawCommit.length + armor.length());
        result.write(rawCommit, 0, i + 1);
        result.write(("gpgsig " + armor.strip().replace("\n", "\n ") + "\n").getBytes(US_ASCII));
        result.write(rawCommit, i + 1, rawCommit.length - i - 1);
        return result.toByteArray();
      }
    }
    throw new IOException("Cannot sign a commit without a header separator");
  }
}
