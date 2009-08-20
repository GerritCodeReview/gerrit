package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.reviewdb.PatchSet;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

  public class PatchSetIdHandler extends OptionHandler<PatchSet.Id> {
    public PatchSetIdHandler(
        final CmdLineParser parser,
        final OptionDef option,
        final Setter<? super PatchSet.Id> setter) {
      super(parser, option, setter);
    }

    @Override
    public final int parseArguments(final Parameters params) throws CmdLineException {
      final String idString = params.getParameter(0);
      final PatchSet.Id id;
      try {
        id = PatchSet.Id.parse(idString);
      } catch (IllegalArgumentException e) {
        throw new CmdLineException("Invalid patch set: " + idString);
      }

      setter.addValue(id);
      return 1;
    }

    @Override
    public final String getDefaultMetaVariable() {
      return "PATCH-SET-ID";
    }
  }
