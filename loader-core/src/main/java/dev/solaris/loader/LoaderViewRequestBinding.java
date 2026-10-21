package dev.solaris.loader;

/**
 * One validated native key binding of a schema-2 overlay that opens a declared
 * screen: pressing the canonical Minecraft {@code key.keyboard.*} {@code key}
 * sends the wire-3 {@code solaris:loader/view_action} {@code view_request}
 * whose {@code request_kind} is the exact {@code view} id. The target is a
 * screen the same owner declares; the server routes the request to that owner
 * and answers with an ordinary {@code open_view}, so the key carries no
 * presentation. Unlike an {@link LoaderInputBinding} it needs no live view
 * instance: it is armed when the content is activated.
 */
public record LoaderViewRequestBinding(
        String key,
        LoaderViewKey view) {
}
