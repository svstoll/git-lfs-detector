package ch.svenstoll.mbm.gitlfsdetector.utility;

import java.util.Collection;

public class CollectionUtility {

  public static boolean isNullOrEmpty(Collection collection) {
    return collection == null || collection.isEmpty();
  }
}
