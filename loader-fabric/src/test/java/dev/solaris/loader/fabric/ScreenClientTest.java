package dev.solaris.loader.fabric;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.solaris.loader.LoaderActivatedContent;
import dev.solaris.loader.LoaderBlockDefinition;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ScreenClientTest {
    @Test
    void onlyTheDeclaredComputerMonitorCarrierOpensScreenSharing() {
        var monitor = new LoaderBlockDefinition("computer:monitor", "computer:block/monitor", "Monitor",
                "minecraft:gray_glazed_terracotta", "computer:monitor");
        var ruby = new LoaderBlockDefinition("ruby-live:ruby_block", "ruby-live:block/ruby_block", "Ruby",
                "minecraft:pink_glazed_terracotta", "ruby-live:ruby_block");
        var content = new LoaderActivatedContent(List.of(), Map.of(), Map.of(),
                Map.of(monitor.id(), monitor, ruby.id(), ruby), Map.of(), Map.of(), Map.of());
        assertTrue(ScreenClient.isMonitorCarrier(content, "minecraft:gray_glazed_terracotta"));
        assertFalse(ScreenClient.isMonitorCarrier(content, "minecraft:pink_glazed_terracotta"));
        var withoutMonitor = new LoaderActivatedContent(List.of(), Map.of(), Map.of(),
                Map.of(ruby.id(), ruby), Map.of(), Map.of(), Map.of());
        assertFalse(ScreenClient.isMonitorCarrier(withoutMonitor, "minecraft:pink_glazed_terracotta"));
    }
}
