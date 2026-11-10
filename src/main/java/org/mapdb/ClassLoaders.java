package org.mapdb;

/**
 * Resolves the class loader used by MapDB serializers.
 *
 * <p>Android pool threads (for example the AsyncTask pool behind
 * {@code ContentProvider.openPipeHelper}) expose a null context class loader. A null must not
 * leak into {@link DB} or the class serializers, so the loader that loaded MapDB is used as a
 * fallback.
 */
public final class ClassLoaders {
    private ClassLoaders() {
    }

    public static ClassLoader defaultClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : ClassLoaders.class.getClassLoader();
    }
}
