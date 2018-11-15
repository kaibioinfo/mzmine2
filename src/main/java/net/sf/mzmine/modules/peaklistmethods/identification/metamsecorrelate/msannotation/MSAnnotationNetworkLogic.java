package net.sf.mzmine.modules.peaklistmethods.identification.metamsecorrelate.msannotation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.sf.mzmine.datamodel.PeakIdentity;
import net.sf.mzmine.datamodel.PeakList;
import net.sf.mzmine.datamodel.PeakListRow;
import net.sf.mzmine.datamodel.identities.iontype.AnnotationNetwork;
import net.sf.mzmine.datamodel.identities.iontype.IonIdentity;
import net.sf.mzmine.datamodel.identities.iontype.IonType;
import net.sf.mzmine.datamodel.impl.RowGroup;
import net.sf.mzmine.parameters.parametertypes.tolerances.MZTolerance;
import net.sf.mzmine.util.PeakListRowSorter;
import net.sf.mzmine.util.SortingDirection;
import net.sf.mzmine.util.SortingProperty;

public class MSAnnotationNetworkLogic {
  private static final Logger LOG = Logger.getLogger(MSAnnotationNetworkLogic.class.getName());


  /**
   * Show the annotation with the highest numbers of links. Prefers charge state.
   * 
   * @param mseGroupedPeakList
   * @param g can be null. can be used to limit the number of links
   */
  public static void showMostlikelyAnnotations(PeakList pkl) {
    for (PeakListRow row : pkl.getRows()) {
      IonIdentity best = getMostLikelyAnnotation(row, null);
      // set best
      if (best != null)
        row.setPreferredPeakIdentity(best);
    }
  }

  /**
   * Show the annotation with the highest numbers of links. Prefers charge state.
   * 
   * @param mseGroupedPeakList
   * @param g can be null. can be used to limit the number of links
   */
  public static void showMostlikelyAnnotations(PeakList pkl, boolean useGroups) {
    for (PeakListRow row : pkl.getRows()) {
      IonIdentity best = getMostLikelyAnnotation(row, useGroups);
      // set best
      if (best != null)
        row.setPreferredPeakIdentity(best);
    }
  }


  /**
   * 
   * @param row
   * @param useGroup searches for a correlation group or null
   * @return Most likely annotation or null if none present
   */
  public static IonIdentity getMostLikelyAnnotation(PeakListRow row, boolean useGroup) {
    return getMostLikelyAnnotation(row, useGroup ? row.getGroup() : null);
  }

  /**
   * 
   * @param row
   * @param g can be null. can be used to limit the number of links
   * @return
   */
  public static IonIdentity getMostLikelyAnnotation(PeakListRow row, RowGroup g) {
    IonIdentity best = null;
    for (PeakIdentity id : row.getPeakIdentities()) {
      if (id instanceof IonIdentity) {
        IonIdentity esi = (IonIdentity) id;
        int compare = compareRows(best, esi, g);
        if (compare < 0)
          best = esi;
      }
    }
    return best;
  }


  /**
   * 
   * @param row
   * @param g can be null. can be used to limit the number of links
   * @return
   */
  public static boolean hasIonAnnotation(PeakListRow row) {
    for (PeakIdentity id : row.getPeakIdentities()) {
      if (id instanceof IonIdentity) {
        return true;
      }
    }
    return false;
  }

  /**
   * 
   * @param best
   * @param esi
   * @return -1 if esi is better than best 1 if opposite
   */
  public static int compareRows(IonIdentity best, IonIdentity esi, RowGroup g) {
    if (best == null || best.getIonType().isUndefinedAdductParent())
      return -1;
    else if (esi.getIonType().isUndefinedAdductParent())
      return 1;
    // size of network (ions pointing to the same neutral mass)
    else if (esi.getNetwork() != null
        && (best.getNetwork() == null || esi.getNetwork().size() > best.getNetwork().size()))
      return -1;
    // keep if has M>1 and was identified by MSMS
    else if (compareMSMSMolIdentity(esi, best))
      return 1;
    // always if M>1 backed by MSMS
    else if (compareMSMSMolIdentity(best, esi))
      return -1;
    // keep if insource fragment verified by MSMS
    else if (compareMSMSNeutralLossIdentity(esi, best))
      return 1;
    // keep if insource fragment verified by MSMS
    else if (compareMSMSNeutralLossIdentity(best, esi))
      return -1;

    int esiLinks = getLinksTo(esi, g);
    int bestLinks = getLinksTo(best, g);
    if (esiLinks == bestLinks && (compareCharge(best, esi))) {
      return -1;
    } else if (esiLinks > bestLinks) {
      return -1;
    }
    return 1;
  }

  /**
   * 
   * @param best
   * @param esi
   * @return onyl true if best so far was not verified by MSMS and esi was verified
   */
  private static boolean compareMSMSMolIdentity(IonIdentity best, IonIdentity esi) {
    if (best.getMSMSMultimerCount() == 0 && esi.getMSMSMultimerCount() > 0)
      return true;
    else
      return false;
  }

  /**
   * 
   * @param best
   * @param esi
   * @return onyl true if best was not verified by MSMS and and esi is
   */
  private static boolean compareMSMSNeutralLossIdentity(IonIdentity best, IonIdentity esi) {
    if (best.getMSMSModVerify() == 0 && esi.getMSMSModVerify() > 0)
      return true;
    else
      return false;
  }

  /**
   * 
   * @param row
   * @param g can be null. can be used to limit the number of links
   * @return
   */
  public static int getLinksTo(IonIdentity esi, RowGroup g) {
    // TODO change to real links after refinement
    if (g == null)
      return esi.getPartnerRowsID().length;
    else {
      int c = 0;
      for (int id : esi.getPartnerRowsID())
        if (g.contains(id))
          c++;

      return c;
    }
  }

  /**
   * 
   * @param a
   * @param b
   * @return True if b is a better choice
   */
  private static boolean compareCharge(IonIdentity a, IonIdentity b) {
    int ca = a.getIonType().getAbsCharge();
    int cb = b.getIonType().getAbsCharge();
    return cb != 0 // a is better if b is uncharged
        && ((ca == 0 && cb > 0) // b is better if charged and a uncharged
            || (ca > cb)); // b is better if charge is lower
  }


  /**
   * Create list of AnnotationNetworks and set net ID
   * 
   * @param groups
   * 
   * @param rows
   * @return
   */
  public static List<AnnotationNetwork> createAnnotationNetworks(PeakList pkl,
      MZTolerance mzTolerance, boolean useGrouping) {
    return createAnnotationNetworks(pkl.getRows(), mzTolerance, useGrouping);
  }

  /**
   * Create list of AnnotationNetworks and set net ID Method 1 ALl edges
   * 
   * @param rows
   * @return
   */
  public static List<AnnotationNetwork> createAnnotationNetworksOld(PeakListRow[] rows,
      boolean addNetworkNumber, MZTolerance mzTolerance) {
    List<AnnotationNetwork> nets = new ArrayList<>();

    if (rows != null) {
      // sort by rt
      Arrays.sort(rows, new PeakListRowSorter(SortingProperty.ID, SortingDirection.Ascending));

      AnnotationNetwork current = new AnnotationNetwork(mzTolerance, nets.size());
      // add all connections
      for (PeakListRow row : rows) {
        current.clear();
        boolean isNewNet = addRow(current, row, rows, row.getID());
        if (isNewNet && current.size() > 1) {
          // LOG.info("Add network " + current.getID() + " with n=" + current.size());
          // add
          nets.add(current);
          // add network number to annotations
          if (addNetworkNumber) {
            for (Iterator iterator = current.keySet().iterator();; iterator.hasNext()) {
              PeakListRow r = (PeakListRow) iterator.next();
              for (PeakIdentity pi : r.getPeakIdentities()) {
                // identity by ms annotation module
                if (pi instanceof IonIdentity) {
                  IonIdentity adduct = (IonIdentity) pi;
                  adduct.setNetwork(current);
                }
              }
            }
          }
          // new
          current = new AnnotationNetwork(mzTolerance, nets.size());
        }
      }
    }
    return nets;
  }


  /**
   * Method 2: all that point to the same molecule (even without edge)
   * 
   * @param rows
   * @return
   */
  public static List<AnnotationNetwork> createAnnotationNetworks(PeakListRow[] rows,
      MZTolerance mzTolerance, boolean useGrouping) {

    // bin neutral masses to annotation networks
    List<AnnotationNetwork> nets = new ArrayList<>(binNeutralMassToNetworks(rows, mzTolerance));

    // split by groups
    if (useGrouping) {
      splitByGroups(nets);
    }

    // add network to all identities
    setNetworksToAllAnnotations(nets);

    // fill in neutral losses [M-H2O] is not iserted yet
    // they might be if [M+H2O+X]+ was also annotated by another link
    fillInNeutralLosses(rows, nets, mzTolerance);

    resetNetworkIDs(nets);
    return nets;
  }


  public static void resetNetworkIDs(List<AnnotationNetwork> nets) {
    for (int i = 0; i < nets.size(); i++) {
      nets.get(i).setID(i);
    }
  }

  /**
   * Need to reset networks to annotations afterwards
   * 
   * @param nets
   */
  private static void splitByGroups(List<AnnotationNetwork> nets) {
    int size = nets.size();
    for (int i = 0; i < size; i++) {
      AnnotationNetwork net = nets.get(i);
      if (!net.allSameCorrGroup()) {
        nets.addAll(splitByGroup(net));
        nets.remove(i);
        i--;
        size--;
      }
    }
  }

  /**
   * Split network into correlation groups. need to reset network to ids afterwards
   * 
   * @param net
   * @return
   */
  private static Collection<AnnotationNetwork> splitByGroup(AnnotationNetwork net) {
    // GroupID, Network
    Map<Integer, AnnotationNetwork> map = new HashMap<>();
    for (Entry<PeakListRow, IonIdentity> e : net.entrySet()) {
      int id = e.getKey().getGroupID();
      if (id != -1) {
        AnnotationNetwork nnet = map.get(id);
        if (nnet == null) {
          // new network for group
          nnet = new AnnotationNetwork(net.getMZTolerance(), -1);
          map.put(id, nnet);
        }
        nnet.put(e.getKey(), e.getValue());
      } else {
        // delete id if no corr group
        e.getValue().delete(e.getKey());
      }
    }
    return map.values();
  }

  /**
   * fill in neutral losses [M-H2O+?] is not inserted yet. they might be if [M+H2O+X]+ was also
   * annotated by another link
   * 
   * @param rows
   * @param nets
   */
  private static void fillInNeutralLosses(PeakListRow[] rows, Collection<AnnotationNetwork> nets,
      MZTolerance mzTolerance) {
    for (PeakListRow row : rows) {
      for (PeakIdentity pi : row.getPeakIdentities()) {
        // identity by ms annotation module
        if (pi instanceof IonIdentity) {
          IonIdentity neutral = (IonIdentity) pi;
          // only if charged (neutral losses do not point to the real neutral mass)
          if (!neutral.getIonType().isModifiedUndefinedAdduct())
            continue;

          // all partners
          int[] partnerIDs = neutral.getPartnerRowsID();
          for (int p : partnerIDs) {
            PeakListRow partner = findRowByID(p, rows);
            if (partner == null)
              continue;

            AnnotationNetwork[] partnerNets = MSAnnotationNetworkLogic.getAllNetworks(partner);
            // create new net if partner was in no network
            if (partnerNets == null || partnerNets.length == 0) {
              // create new and put both
              AnnotationNetwork newNet = new AnnotationNetwork(mzTolerance, nets.size());
              nets.add(newNet);
              newNet.put(row, neutral);
              newNet.put(partner, IonIdentity.getIdentityOf(partner, row));
              newNet.setNetworkToAllRows();
            } else {
              // add neutral loss to nets
              // do not if its already in this network (e.g. as adduct)
              Arrays.stream(partnerNets).filter(pnet -> !pnet.containsKey(row)).forEach(pnet -> {
                // try to find real annotation
                IonType pid = pnet.get(partner).getIonType();
                // modified
                pid = pid.createModified(neutral.getIonType().getModification());

                IonIdentity realID = neutral;
                if (pnet.checkForAnnotation(row, pid)) {
                  // create new
                  realID = new IonIdentity(pid);
                  row.addPeakIdentity(realID, false);
                  realID.setNetwork(pnet);
                  // set partners
                  pnet.addAllLinksTo(row, realID);
                }

                // put
                pnet.put(row, realID);
              });
            }
          }
        }
      }
    }
  }

  /**
   * All annotation networks of all annotations of row
   * 
   * @param row
   * @return
   */
  public static AnnotationNetwork[] getAllNetworks(PeakListRow row) {
    return MSAnnotationNetworkLogic.getAllAnnotations(row).stream().map(IonIdentity::getNetwork)
        .filter(Objects::nonNull).distinct().toArray(AnnotationNetwork[]::new);
  }

  /**
   * Set the network to all its children rows
   * 
   * @param nets
   */
  public static void setNetworksToAllAnnotations(Collection<AnnotationNetwork> nets) {
    nets.stream().forEach(n -> n.setNetworkToAllRows());
  }

  /**
   * Binning of all neutral masses described by all annotations of rows with 0.1 Da binning width
   * (masses should be very different)
   * 
   * @param rows
   * @return AnnotationNetworks
   */
  private static Collection<AnnotationNetwork> binNeutralMassToNetworks(PeakListRow[] rows,
      MZTolerance mzTolerance) {
    Map<Integer, AnnotationNetwork> map = new HashMap<>();
    for (PeakListRow row : rows) {
      for (PeakIdentity pi : row.getPeakIdentities()) {
        // identity by ms annotation module
        if (pi instanceof IonIdentity) {
          IonIdentity adduct = (IonIdentity) pi;
          // only if charged (neutral losses do not point to the real neutral mass)
          if (adduct.getIonType().getAbsCharge() == 0)
            continue;

          double mass = adduct.getIonType().getMass(row.getAverageMZ());
          // bin to 0.1
          Integer nmass = (int) Math.round(mass * 10.0);

          AnnotationNetwork net = map.get(nmass);
          if (net == null) {
            // create new
            net = new AnnotationNetwork(mzTolerance, map.size());
            map.put(nmass, net);
          }
          // add row and id to network
          net.put(row, adduct);
        }
      }
    }
    return map.values();
  }

  /**
   * Neutral mass of AnnotationNetwork entry (ion and peaklistrow)
   * 
   * @param e
   * @return
   */
  public static double calcMass(Entry<PeakListRow, IonIdentity> e) {
    return e.getValue().getIonType().getMass(e.getKey().getAverageMZ());
  }

  /**
   * Add all rows of a network
   * 
   * @param current
   * @param row
   * @param rows
   * @return false if this network has already been created
   */
  private static boolean addRow(AnnotationNetwork current, PeakListRow row, PeakListRow[] rows,
      int masterID) {
    for (PeakIdentity pi : row.getPeakIdentities()) {
      // identity by ms annotation module
      if (pi instanceof IonIdentity) {
        IonIdentity adduct = (IonIdentity) pi;

        // try to add all
        if (current.isEmpty())
          current.put(row, adduct);

        // add all connection for ids>rowID
        int[] ids = adduct.getPartnerRowsID();
        for (int id : ids) {
          if (id != masterID) {
            if (id > masterID) {
              PeakListRow row2 = findRowByID(id, rows);
              IonIdentity adduct2 = IonIdentity.getIdentityOf(row2, row);
              // new row found?
              if (row2 != null && !current.containsKey(row2)) {
                current.put(row2, adduct2);
                boolean isNewNet = addRow(current, row2, rows, masterID);
                if (!isNewNet)
                  return false;
              }
            } else {
              // id was smaller - trash this network, its already added
              return false;
            }
          }
        }
      }
    }
    // is new network
    return true;
  }

  public static PeakListRow findRowByID(int id, PeakListRow[] rows) {
    if (rows == null)
      return null;
    else {
      for (PeakListRow r : rows)
        if (r.getID() == id)
          return r;

      return null;
    }
  }

  /**
   * All MS annotation connections
   * 
   * @return
   */
  public static List<PeakListRow> findAllAnnotationConnections(PeakListRow[] rows,
      PeakListRow row) {
    List<PeakListRow> connections = new ArrayList<>();

    for (PeakIdentity pi : row.getPeakIdentities()) {
      // identity by ms annotation module
      if (pi instanceof IonIdentity) {
        IonIdentity adduct = (IonIdentity) pi;

        // add all connection
        int[] ids = adduct.getPartnerRowsID();
        for (int id : ids) {
          PeakListRow row2 = findRowByID(id, rows);
          connections.add(row2);
        }
      }
    }
    return connections;
  }

  /**
   * 
   * @param row
   * @return list of annotations or an empty list
   */
  public static List<IonIdentity> getAllAnnotations(PeakListRow row) {
    List<IonIdentity> ident = new ArrayList<>();
    for (PeakIdentity pi : row.getPeakIdentities()) {
      if (pi instanceof IonIdentity)
        ident.add((IonIdentity) pi);
    }
    return ident;
  }

  /**
   * 
   * @param row
   * @return list of annotations or an empty list
   */
  public static List<IonIdentity> getAllAnnotationsSorted(PeakListRow row) {
    List<IonIdentity> ident = new ArrayList<>();
    for (PeakIdentity pi : row.getPeakIdentities()) {
      if (pi instanceof IonIdentity)
        ident.add((IonIdentity) pi);
    }
    ident.sort(new Comparator<IonIdentity>() {
      @Override
      public int compare(IonIdentity a, IonIdentity b) {
        return compareRows(a, b, (RowGroup) null);
      }
    });
    return ident;
  }

  /**
   * apply operation for each id
   * 
   * @param row
   * @param op
   */
  public static void forEachAnnotation(PeakListRow row, Consumer<IonIdentity> op) {
    for (PeakIdentity pi : row.getPeakIdentities()) {
      if (pi instanceof IonIdentity)
        op.accept((IonIdentity) pi);
    }
  }

  public static void recalcAllAnnotationNetworks(List<AnnotationNetwork> nets,
      boolean removeEmpty) {
    if (removeEmpty) {
      for (int i = 0; i < nets.size(); i++) {
        if (nets.get(i).size() < 2) {
          nets.remove(i);
          i--;
        }
      }
    }
    // recalc
    nets.stream().forEach(net -> {
      net.recalcConnections();
    });
  }

  /**
   * Best network of group (all rows)
   * 
   * @param g
   * @return
   */
  public static AnnotationNetwork getBestNetwork(RowGroup g) {
    AnnotationNetwork best = null;
    for (PeakListRow r : g) {
      IonIdentity id = getMostLikelyAnnotation(r, g);
      AnnotationNetwork net = id != null ? id.getNetwork() : null;
      if (net != null && (best == null || best.size() < net.size()))
        best = net;
    }
    return best;
  }

}
