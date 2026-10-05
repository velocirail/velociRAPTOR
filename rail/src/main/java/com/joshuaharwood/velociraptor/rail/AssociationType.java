package com.joshuaharwood.velociraptor.rail;

/** How one train becomes another where a passenger stays aboard: the CIF association categories. */
public enum AssociationType {
  /** The train divides, and the passenger stays in the portion that carries on to their destination. CIF VV. */
  DIVIDE,
  /** The train joins another, and the two run on as one. CIF JJ. */
  JOIN,
  /** The train ends and forms the next service. CIF NP. */
  NEXT
}
