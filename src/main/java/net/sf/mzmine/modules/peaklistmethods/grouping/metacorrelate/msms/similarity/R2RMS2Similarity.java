package net.sf.mzmine.modules.peaklistmethods.grouping.metacorrelate.msms.similarity;

import java.util.ArrayList;
import java.util.List;
import net.sf.mzmine.datamodel.PeakListRow;

public class R2RMS2Similarity {
  private PeakListRow a, b;
  //
  private List<MS2Similarity> massDiffSim = new ArrayList<>();
  private List<MS2Similarity> spectralSim = new ArrayList<>();

  public R2RMS2Similarity(PeakListRow a, PeakListRow b) {
    super();
    this.a = a;
    this.b = b;
  }

  public void addMassDiffSim(MS2Similarity sim) {
    massDiffSim.add(sim);
  }

  public void addSpectralSim(MS2Similarity sim) {
    spectralSim.add(sim);
  }

  public int size() {
    return Math.max(massDiffSim.size(), spectralSim.size());
  }

  public List<MS2Similarity> getMassDiffSim() {
    return massDiffSim;
  }

  public List<MS2Similarity> getSpectralSim() {
    return spectralSim;
  }

  public PeakListRow getA() {
    return a;
  }

  public PeakListRow getB() {
    return b;
  }

  public int getDiffMaxOverlap() {
    if (massDiffSim.isEmpty())
      return 0;
    return massDiffSim.stream().mapToInt(MS2Similarity::getOverlap).max().getAsInt();
  }

  public int getDiffMinOverlap() {
    if (massDiffSim.isEmpty())
      return 0;
    return massDiffSim.stream().mapToInt(MS2Similarity::getOverlap).min().getAsInt();
  }

  public double getDiffAvgOverlap() {
    if (massDiffSim.isEmpty())
      return 0;
    return massDiffSim.stream().mapToInt(MS2Similarity::getOverlap).average().getAsDouble();
  }

  public int getSpectralMaxOverlap() {
    if (spectralSim.isEmpty())
      return 0;
    return spectralSim.stream().mapToInt(MS2Similarity::getOverlap).max().getAsInt();
  }

  public int getSpectralMinOverlap() {
    if (spectralSim.isEmpty())
      return 0;
    return spectralSim.stream().mapToInt(MS2Similarity::getOverlap).min().getAsInt();
  }

  public double getSpectralAvgOverlap() {
    if (spectralSim.isEmpty())
      return 0;
    return spectralSim.stream().mapToInt(MS2Similarity::getOverlap).average().getAsDouble();
  }

  // COSINE
  public double getSpectralAvgCosine() {
    if (spectralSim.isEmpty())
      return 0;
    return spectralSim.stream().mapToDouble(MS2Similarity::getCosine).average().getAsDouble();
  }

  public double getSpectralMaxCosine() {
    if (spectralSim.isEmpty())
      return 0;
    return spectralSim.stream().mapToDouble(MS2Similarity::getCosine).max().getAsDouble();
  }

  public double getSpectralMinCosine() {
    if (spectralSim.isEmpty())
      return 0;
    return spectralSim.stream().mapToDouble(MS2Similarity::getCosine).min().getAsDouble();
  }

  public double getDiffAvgCosine() {
    if (massDiffSim.isEmpty())
      return 0;
    return massDiffSim.stream().mapToDouble(MS2Similarity::getCosine).average().getAsDouble();
  }

  public double getDiffMaxCosine() {
    if (massDiffSim.isEmpty())
      return 0;
    return massDiffSim.stream().mapToDouble(MS2Similarity::getCosine).max().getAsDouble();
  }

  public double getDiffMinCosine() {
    if (massDiffSim.isEmpty())
      return 0;
    return massDiffSim.stream().mapToDouble(MS2Similarity::getCosine).min().getAsDouble();
  }

}
