package dev.solaris.loader;

public record LoaderBlockDefinition(
        String id,
        String model,
        String name,
        String carrier,
        String dropItem) {
}
