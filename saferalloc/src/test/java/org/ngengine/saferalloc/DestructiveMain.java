package org.ngengine.saferalloc;

/**
 * Invoked by DestructiveForkedTest.
 */
public final class DestructiveMain {
  public static void main(String[] args) {
    if (args.length == 1) {
      switch (args[0]) {
        case "doubleFree":
          System.out.println("probeDoubleFree=" + SaferAllocProbe.probeDoubleFree());
          System.out.println("DONE");
          return;
        case "overflow":
          System.out.println("probeOverflow=" + SaferAllocProbe.probeOverflow());
          System.out.println("DONE");
          return;
        case "useAfterFree":
          System.out.println("probeUseAfterFree=" + SaferAllocProbe.probeUseAfterFree());
          System.out.println("DONE");
          return;
        default:
          throw new IllegalArgumentException("Unknown probe: " + args[0]);
      }
    }

    System.out.println("probeDoubleFree=" + SaferAllocProbe.probeDoubleFree());
    System.out.println("probeOverflow=" + SaferAllocProbe.probeOverflow());
    System.out.println("probeUseAfterFree=" + SaferAllocProbe.probeUseAfterFree());
    System.out.println("DONE");
  }
}
