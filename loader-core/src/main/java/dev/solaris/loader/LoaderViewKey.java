package dev.solaris.loader;

/** Exact plugin-owned resource identifier shared by declarations and view requests. */
public record LoaderViewKey(String value) {
    public static final int MAX_BYTES = 128;

    public LoaderViewKey {
        if (value == null || value.isEmpty() || value.length() > MAX_BYTES) {
            throw new IllegalArgumentException("Loader view key must contain 1..128 ASCII bytes");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Loader view key must be namespaced");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (index == separator) {
                continue;
            }
            if (!(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_' && character != '-' && character != '.'
                    && !(index > separator && character == '/')) {
                throw new IllegalArgumentException("Loader view key is not a lowercase resource identifier");
            }
        }
    }
}
