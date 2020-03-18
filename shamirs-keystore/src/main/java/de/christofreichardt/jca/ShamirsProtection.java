package de.christofreichardt.jca;

import de.christofreichardt.scala.shamir.SecretMerging;

import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Collection;

public class ShamirsProtection implements KeyStore.ProtectionParameter {
    final Path[] paths;
    final char[] password;

    public ShamirsProtection(Path[] paths) {
        this.paths = paths;
        this.password = mergePassword();
    }

    public ShamirsProtection(Collection<Path> paths) {
        this.paths = paths.toArray(new Path[0]);
        this.password = mergePassword();
    }

    private char[] mergePassword() {
        return SecretMerging.apply(this.paths).password();
    }

    public char[] getPassword() {
        return password;
    }
}
