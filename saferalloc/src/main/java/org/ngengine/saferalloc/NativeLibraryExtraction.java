package org.ngengine.saferalloc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Creates a private, unpredictable directory for one native extraction. */
final class NativeLibraryExtraction {
  private NativeLibraryExtraction() {}

  static Path createDirectory(Path root, String prefix) throws IOException {
    Path ancestor = root.toAbsolutePath();
    while (!Files.exists(ancestor)) {
      ancestor = ancestor.getParent();
      if (ancestor == null) throw new IOException("No existing ancestor for " + root);
    }

    FileAttribute<?> privatePermissions = privatePermissions(ancestor);
    Files.createDirectories(root, privatePermissions);
    // Resolve aliases such as macOS /var before checking who controls the parents.
    root = root.toRealPath();
    if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
      checkPosixParents(root);
    }

    Path directory = Files.createTempDirectory(root, prefix, privatePermissions);
    boolean usable = false;
    try {
      directory.toFile().deleteOnExit();
      if (privatePermissions.name().equals("acl:acl")) {
        @SuppressWarnings("unchecked")
        List<AclEntry> ownerAcl = (List<AclEntry>) privatePermissions.value();
        AclFileAttributeView view = Files.getFileAttributeView(directory, AclFileAttributeView.class);
        view.setAcl(ownerAcl);
        // Windows may retain inherited entries. Read access alone cannot replace a DLL;
        // reject any foreign principal that can modify the directory or its contents.
        for (AclEntry entry : view.getAcl()) {
          if (entry.type() == AclEntryType.ALLOW
              && !entry.principal().equals(ownerAcl.get(0).principal())
              && canModify(entry)) {
            throw new IOException("Cannot restrict native directory ACL: " + directory);
          }
        }
      }
      checkExecutable(directory);
      usable = true;
      return directory;
    } finally {
      if (!usable) Files.deleteIfExists(directory);
    }
  }

  private static boolean canModify(AclEntry entry) {
    for (AclEntryPermission permission : entry.permissions()) {
      switch (permission) {
        case WRITE_DATA:
        case APPEND_DATA:
        case WRITE_NAMED_ATTRS:
        case WRITE_ATTRIBUTES:
        case DELETE:
        case DELETE_CHILD:
        case WRITE_ACL:
        case WRITE_OWNER:
          return true;
        default:
          break;
      }
    }
    return false;
  }

  private static FileAttribute<?> privatePermissions(Path existing) throws IOException {
    if (Files.getFileStore(existing).supportsFileAttributeView("posix")) {
      return PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    }
    if (Files.getFileStore(existing).supportsFileAttributeView("acl")) {
      UserPrincipal owner = existing.getFileSystem().getUserPrincipalLookupService()
          .lookupPrincipalByName(System.getProperty("user.name"));
      final List<AclEntry> acl = Collections.singletonList(AclEntry.newBuilder()
          .setType(AclEntryType.ALLOW)
          .setPrincipal(owner)
          .setPermissions(EnumSet.allOf(AclEntryPermission.class))
          .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT)
          .build());
      return new FileAttribute<List<AclEntry>>() {
        @Override public String name() { return "acl:acl"; }
        @Override public List<AclEntry> value() { return acl; }
      };
    }
    throw new IOException("Filesystem cannot create private native directories: " + existing);
  }

  private static void checkPosixParents(Path root) throws IOException {
    UserPrincipal user = root.getFileSystem().getUserPrincipalLookupService()
        .lookupPrincipalByName(System.getProperty("user.name"));
    List<Path> parents = new ArrayList<>();
    for (Path path = root; path != null; path = path.getParent()) parents.add(path);
    Collections.reverse(parents);

    boolean privateAncestor = false;
    for (Path path : parents) {
      PosixFileAttributes attributes = Files.readAttributes(path, PosixFileAttributes.class);
      boolean systemOwned = attributes.owner().getName().equals("root");
      if (!attributes.owner().equals(user) && !systemOwned) {
        throw new IOException("Native extraction ancestor belongs to another user: " + path);
      }
      if (!privateAncestor && (attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)
          || attributes.permissions().contains(PosixFilePermission.OTHERS_WRITE))) {
        int mode = ((Number) Files.getAttribute(path, "unix:mode")).intValue();
        if ((mode & 01000) == 0) {
          throw new IOException("Native extraction ancestor is writable by other users: " + path);
        }
      }
      if (attributes.owner().equals(user)
          && !attributes.permissions().contains(PosixFilePermission.GROUP_EXECUTE)
          && !attributes.permissions().contains(PosixFilePermission.OTHERS_EXECUTE)) {
        privateAncestor = true;
      }
    }
  }

  private static void checkExecutable(Path directory) throws IOException {
    if (!Files.getFileStore(directory).supportsFileAttributeView("posix")) return;
    Path probe = Files.createTempFile(directory, "exec-probe-", null);
    try {
      Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rwx------"));
      if (!Files.isExecutable(probe)) {
        throw new IOException("Native execution is not permitted (possibly noexec): " + directory);
      }
    } finally {
      Files.deleteIfExists(probe);
    }
  }
}
