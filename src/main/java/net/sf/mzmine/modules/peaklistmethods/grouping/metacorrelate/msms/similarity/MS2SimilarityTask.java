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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import com.google.common.util.concurrent.AtomicDouble;
import net.sf.mzmine.datamodel.DataPoint;
import net.sf.mzmine.datamodel.Feature;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.identities.iontype.IonNetwork;
import net.sf.mzmine.datamodel.impl.RowGroupList;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.MS2SimilarityProviderGroup;
import net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.datastructure.R2RMap;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import net.sf.mzmine.taskcontrol.AbstractTask;
import net.sf.mzmine.taskcontrol.TaskStatus;
import net.sf.mzmine.util.maths.similarity.Similarity;
import net.sf.mzmine.util.scans.ScanAlignment;
import net.sf.mzmine.util.scans.ScanMZDiffConverter;

public class MS2SimilarityTask extends AbstractTask {
  // Logger.
  private static final Logger LOG = Logger.getLogger(MS2SimilarityTask.class.getName());

  public static Function<List<DataPoint[]>, Integer> DIFF_OVERLAP =
      list -> ScanMZDiffConverter.getOverlapOfAlignedDiff(list, 0, 1);
  public static Function<List<DataPoint[]>, Integer> SIZE_OVERLAP = List::size;

  private AtomicDouble stageProgress;

  private String massList;
  private int minMatch;
  private int minDP;
  private int maxDPForDiff = 25;
  private MZTolerance mzTolerance;
  private double minHeight;

  private PeakListRow[] rows;
  private RowGroupList groups;
  private MS2SimilarityProviderGroup group;
  private IonNetwork[] nets;

  // target
  private R2RMap<R2RMS2Similarity> map;

  private PeakList peakList;



  /**
   * 
   * @param parameterSet
   */
  public MS2SimilarityTask(final ParameterSet parameterSet, PeakList peakList) {
    this.peakList = peakList;
    massList = parameterSet.getParameter(MS2SimilarityParameters.MASS_LIST).getValue();
    mzTolerance = parameterSet.getParameter(MS2SimilarityParameters.MZ_TOLERANCE).getValue();
    minHeight = parameterSet.getParameter(MS2SimilarityParameters.MIN_HEIGHT).getValue();
    minDP = parameterSet.getParameter(MS2SimilarityParameters.MIN_DP).getValue();
    minMatch = parameterSet.getParameter(MS2SimilarityParameters.MIN_MATCH).getValue();
    maxDPForDiff = parameterSet.getParameter(MS2SimilarityParameters.MAX_DP_FOR_DIFF).getValue();
    stageProgress = new AtomicDouble(0);
  }

  /**
   * Create the task. to run on list of groups
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public MS2SimilarityTask(final ParameterSet parameterSet, PeakList peakList,
      RowGroupList groups) {
    this(parameterSet, peakList);
    // performed on groups
    this.groups = groups;
  }

  /**
   * Create the task on set of rows
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public MS2SimilarityTask(final ParameterSet parameterSet, PeakList peakList, PeakListRow[] rows) {
    this(parameterSet, peakList);
    this.rows = rows;
  }

  /**
   * Create the task on single group (the result is automatically set to the group
   *
   * @param parameterSet the parameters.
   * @param list peak list.
   */
  public MS2SimilarityTask(final ParameterSet parameterSet, PeakList peakList,
      MS2SimilarityProviderGroup group) {
    this(parameterSet, peakList);
    this.group = group;
  }

  public MS2SimilarityTask(ParameterSet parameters, PeakList peakList, IonNetwork[] nets) {
    this(parameters, peakList);
    this.nets = nets;
  }

  @Override
  public double getFinishedPercentage() {
    return stageProgress.get();
  }

  @Override
  public String getTaskDescription() {
    return "Check similarity of MSMS scans (mass lists)";
  }

  @Override
  public void run() {
    setStatus(TaskStatus.PROCESSING);
    doCheck();
    setStatus(TaskStatus.FINISHED);
  }

  public void doCheck() {
    if (nets != null)
      map = checkNetworks(this, stageProgress, peakList, nets, massList, mzTolerance, minHeight,
          minDP, minMatch, maxDPForDiff);
    else if (group != null)
      map = checkGroup(this, stageProgress, group, massList, mzTolerance, minHeight, minDP,
          minMatch, maxDPForDiff);
    else if (rows != null)
      map = checkRows(this, stageProgress, rows, massList, mzTolerance, minHeight, minDP, minMatch,
          maxDPForDiff);
    else if (groups != null)
      checkGroupList(this, stageProgress, groups, massList, mzTolerance, minHeight, minDP, minMatch,
          maxDPForDiff);
  }


  /**
   * Resulting map
   * 
   * @return
   */
  public R2RMap<R2RMS2Similarity> getMap() {
    return map;
  }


  public void checkGroupList(AbstractTask task, AtomicDouble stageProgress, RowGroupList groups) {
    checkGroupList(task, stageProgress, groups, massList, mzTolerance, minHeight, minDP, minMatch,
        maxDPForDiff);
  }

  public static void checkGroupList(AbstractTask task, AtomicDouble stageProgress,
      RowGroupList groups, String massList, MZTolerance mzTolerance, double minHeight, int minDP,
      int minMatch, int maxDPForDiff) {
    LOG.info("Calc MS/MS similarity of groups");
    final int size = groups.size();
    groups.parallelStream().forEach(g -> {
      if (!task.isCanceled()) {
        if (g instanceof MS2SimilarityProviderGroup)
          checkGroup(task, stageProgress, (MS2SimilarityProviderGroup) g, massList, mzTolerance,
              minHeight, minDP, minMatch, maxDPForDiff);
        stageProgress.addAndGet(1d / size);
      }
    });
  }

  /**
   * Checks for MS2 similarity of all rows in a group. the resulting map is set to the groups3
   */
  public R2RMap<R2RMS2Similarity> checkGroup(MS2SimilarityProviderGroup g) {
    return checkGroup(null, null, g, massList, mzTolerance, minHeight, minDP, minMatch,
        maxDPForDiff);
  }


  public static R2RMap<R2RMS2Similarity> checkNetworks(AbstractTask task,
      AtomicDouble stageProgress, PeakList peakList, IonNetwork[] nets, String massList,
      MZTolerance mzTolerance, double minHeight, int minDP, int minMatch, int maxDPForDiff) {
    // get all rows of all networks
    Map<Integer, PeakListRow> rows = new HashMap<>();
    Arrays.stream(nets).flatMap(n -> n.keySet().stream()).forEach(r -> rows.put(r.getID(), r));
    PeakListRow[] allRows = rows.values().toArray(new PeakListRow[0]);

    // add all to this map
    R2RMap<R2RMS2Similarity> map = checkRows(task, stageProgress, allRows, massList, mzTolerance,
        minHeight, minMatch, minDP, maxDPForDiff);

    peakList.addR2RSimilarity(map);
    return map;
  }

  /**
   * Checks for MS2 similarity of all rows in a group. the resulting map is set to the groups3
   * 
   * 
   * @param g
   * @param massList
   * @param mzTolerance
   * @param maxMassDiff
   * @param minMatch
   * @param minDP
   * @param maxDPForDiff
   * @return
   */
  public static R2RMap<R2RMS2Similarity> checkGroup(AbstractTask task, AtomicDouble stageProgress,
      MS2SimilarityProviderGroup g, String massList, MZTolerance mzTolerance, double minHeight,
      int minDP, int minMatch, int maxDPForDiff) {
    R2RMap<R2RMS2Similarity> map =
        checkRows(task, stageProgress, g.toArray(new PeakListRow[g.size()]), massList, mzTolerance,
            minHeight, minMatch, minDP, maxDPForDiff);

    g.setMS2SimilarityMap(map);
    return map;
  }

  /**
   * Parallel check of all r2r similarities
   * 
   * @param rows
   * @param massList
   * @param mzTolerance
   * @param minHeight
   * @param minDP
   * @param minMatch
   * @param maxDPForDiff
   * @return
   */
  public static R2RMap<R2RMS2Similarity> checkRows(AbstractTask task, AtomicDouble stageProgress,
      PeakListRow[] rows, String massList, MZTolerance mzTolerance, double minHeight, int minDP,
      int minMatch, int maxDPForDiff) {
    LOG.info("Checking MS2 similarity on " + rows.length + " rows");
    R2RMap<R2RMS2Similarity> map = new R2RMap<>();

    IntStream.range(0, rows.length - 1).parallel().forEach(i -> {
      if (task == null || !task.isCanceled()) {
        for (int j = i + 1; j < rows.length; j++) {
          if (task == null || !task.isCanceled()) {
            R2RMS2Similarity r2r = checkR2R(rows[i], rows[j], massList, mzTolerance, minHeight,
                minDP, minMatch, maxDPForDiff);
            if (r2r != null)
              map.add(rows[i], rows[j], r2r);
          }
        }
      }
      if (stageProgress != null)
        stageProgress.getAndAdd(1d / rows.length);
    });
    LOG.info("MS2 similarity check on rows done");
    return map;
  }

  public static R2RMS2Similarity checkR2R(PeakListRow a, PeakListRow b, String massList,
      MZTolerance mzTolerance, double minHeight, int minDP, int minMatch, int maxDPForDiff) {
    R2RMS2Similarity r2r = new R2RMS2Similarity(a, b);

    for (Feature fa : a.getPeaks()) {
      DataPoint[] dpa = getMassList(fa, massList);
      if (dpa != null && dpa.length >= minDP) {
        // create mass diff array
        DataPoint[] massDiffA =
            ScanMZDiffConverter.getAllMZDiff(dpa, mzTolerance, minHeight, maxDPForDiff);
        for (Feature fb : b.getPeaks()) {
          DataPoint[] dpb = getMassList(fb, massList);
          if (dpb != null && dpb.length >= minDP) {
            // align and check spectra
            MS2Similarity spectralSim = createMS2Sim(mzTolerance, dpa, dpb, minMatch, SIZE_OVERLAP);

            // alignment and sim of neutral losses
            DataPoint[] massDiffB =
                ScanMZDiffConverter.getAllMZDiff(dpb, mzTolerance, maxDPForDiff);
            MS2Similarity massDiffSim =
                createMS2Sim(mzTolerance, massDiffA, massDiffB, minMatch, DIFF_OVERLAP);

            if (massDiffSim != null)
              r2r.addMassDiffSim(massDiffSim);
            if (spectralSim != null)
              r2r.addSpectralSim(spectralSim);
          }
        }
      }
    }
    return r2r.size() > 0 ? r2r : null;
  }

  /**
   * 
   * @param mzTol
   * @param a
   * @param b
   * @param minMatch minimum overlapping signals in the two mass lists a and b
   * @param overlapFunction different functions to determin the size of overlap
   * @return
   */
  private static MS2Similarity createMS2Sim(MZTolerance mzTol, DataPoint[] a, DataPoint[] b,
      double minMatch, Function<List<DataPoint[]>, Integer> overlapFunction) {
    // align
    List<DataPoint[]> aligned = ScanAlignment.align(mzTol, b, a);
    aligned = ScanAlignment.removeUnaligned(aligned);
    // overlapping mass diff
    int overlap = overlapFunction.apply(aligned);

    if (overlap >= minMatch) {
      // cosine
      double[][] diffArray = ScanAlignment.toIntensityArray(aligned);
      double diffCosine = Similarity.COSINE.calc(diffArray);
      return new MS2Similarity(diffCosine, overlap);
    }
    return null;
  }

  private static DataPoint[] getMassList(Feature fa, String massList) {
    if (fa.getMostIntenseFragmentScanNumber() <= 0)
      return null;
    return fa.getDataFile().getScan(fa.getMostIntenseFragmentScanNumber()).getMassList(massList)
        .getDataPoints();
  }

}
