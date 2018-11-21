/*
 * Copyright 2006-2015 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine 2; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.msms.similarity;

import java.awt.Window;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.parameters.Parameter;
import net.sf.mzmine.parameters.dialogs.ParameterSetupDialog;
import net.sf.mzmine.parameters.impl.SimpleParameterSet;
import net.sf.mzmine.parameters.parametertypes.DoubleParameter;
import net.sf.mzmine.parameters.parametertypes.IntegerParameter;
import net.sf.mzmine.parameters.parametertypes.MassListParameter;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsParameter;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZToleranceParameter;
import net.sf.mzmine.util.ExitCode;

/**
 * MS/MS similarity check based on difference and signal comparison
 * 
 * @author Robin Schmid (robinschmid@uni-muenster.de)
 *
 */
public class MS2SimilarityParameters extends SimpleParameterSet {

  // NOT INCLUDED in sub
  // General parameters
  public static final PeakListsParameter PEAK_LISTS = new PeakListsParameter();

  public static final MassListParameter MASS_LIST =
      new MassListParameter("Mass lists (MS2)", "MS2 mass lists");

  // INCLUDED in sub
  // MZ-tolerance: deisotoping, adducts
  public static final MZToleranceParameter MZ_TOLERANCE = new MZToleranceParameter(
      "m/z tolerance (MS2)",
      "Tolerance value of the m/z difference between MS2 signals (add absolute tolerance to cover small neutral losses (5 ppm on m=18 is insufficient))");

  public static final DoubleParameter MIN_HEIGHT = new DoubleParameter("Min height (in MS2)",
      "Minimum height of signal", MZmineCore.getConfiguration().getIntensityFormat(), 1E3);

  public static final IntegerParameter MIN_DP = new IntegerParameter("Minimum data points (DP)",
      "Minimum data points in MS2 scan mass list", 3);
  public static final IntegerParameter MIN_MATCH = new IntegerParameter("Minimum matched signals",
      "Minimum matched signals or neutral losses (m/z differences)", 3);
  public static final IntegerParameter MAX_DP_FOR_DIFF = new IntegerParameter(
      "Maximum DP for differences matching",
      "Difference (neutral loss) matching is done on a maximum of n MS2 signals per scan. All differences between these signals are calculated and matched between spectra.",
      25);


  // Constructor
  public MS2SimilarityParameters() {
    this(false);
  }

  public MS2SimilarityParameters(boolean isSub) {
    super(isSub ? // no peak list and rt tolerance
        new Parameter[] {MIN_HEIGHT, MIN_DP, MIN_MATCH, MAX_DP_FOR_DIFF}
        : new Parameter[] {PEAK_LISTS, MASS_LIST, MZ_TOLERANCE, MIN_HEIGHT, MIN_DP, MIN_MATCH,
            MAX_DP_FOR_DIFF});
  }

  @Override
  public ExitCode showSetupDialog(Window parent, boolean valueCheckRequired) {
    ParameterSetupDialog dialog = new ParameterSetupDialog(parent, valueCheckRequired, this);
    dialog.setVisible(true);
    return dialog.getExitCode();
  }
}
