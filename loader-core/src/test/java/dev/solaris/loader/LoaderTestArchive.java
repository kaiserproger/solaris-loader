package dev.solaris.loader;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class LoaderTestArchive {
    private LoaderTestArchive() {
    }

    /** One settlement screen with a paged table and no other content. */
    static byte[] screenOnly() {
        return archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome",
                  "widgets":[{
                    "type":"paged_table","id":"roster","columns":[
                      {"id":"name","label":"Name","align":"left","width_bucket":2}
                    ]
                  }]
                }],"blocks":[],"items":[],"assets":[]}
                """,
                Map.of());
    }

    /** One screen plus one exactly declared asset. */
    static byte[] screenAndAsset(byte[] asset) {
        return archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:welcome","kind":"settlement","title":"Welcome",
                  "widgets":[{
                    "type":"tabs","id":"pages","entries":[
                      {"id":"one","label":"One"}
                    ]
                  }]
                }],"blocks":[],"items":[],"assets":[{
                  "id":"example:logo","path":"assets/example/logo.bin",
                  "sha256":"%s","size_bytes":%d
                }]}
                """.formatted(sha256(asset), asset.length),
                Map.of("assets/example/logo.bin", asset));
    }

    /** One screen referencing one owned item and its exact item definition. */
    static byte[] screenAndItem() {
        byte[] definition = """
                {"model":{"type":"minecraft:model","model":"example:item/ruby"}}
                """.getBytes(StandardCharsets.UTF_8);
        return archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:catalog","kind":"economy","title":"Catalog",
                  "item_id":"example:ruby",
                  "widgets":[{
                    "type":"resource_panel","id":"cost","label":"Cost","entries":[
                      {"id":"ruby","label":"Ruby"}
                    ]
                  }]
                }],"blocks":[],"items":[{
                  "id":"example:ruby","base_item":"minecraft:paper","name":"Ruby"
                }],"assets":[{
                  "id":"example:ruby_definition",
                  "path":"assets/example/items/ruby.json",
                  "sha256":"%s","size_bytes":%d
                }]}
                """.formatted(sha256(definition), definition.length),
                Map.of("assets/example/items/ruby.json", definition));
    }

    /** One screen referencing one owned block and its exact owner model. */
    static byte[] screenAndBlock() {
        byte[] model = """
                {
                  "parent":"minecraft:block/cube_all",
                  "textures":{"all":"minecraft:block/redstone_block"}
                }
                """.getBytes(StandardCharsets.UTF_8);
        return archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:catalog","kind":"construction","title":"Catalog",
                  "block_id":"example:ruby_block",
                  "widgets":[{
                    "type":"action_button","action_id":"confirm","label":"Confirm"
                  }]
                }],"blocks":[{
                  "id":"example:ruby_block","model":"example:block/ruby_block",
                  "name":"Ruby Block"
                }],"items":[],"assets":[{
                  "id":"example:ruby_block_model",
                  "path":"assets/example/models/block/ruby_block.json",
                  "sha256":"%s","size_bytes":%d
                }]}
                """.formatted(sha256(model), model.length),
                Map.of("assets/example/models/block/ruby_block.json", model));
    }

    /** One screen declaring every implemented widget type. */
    static byte[] screenWithEveryWidget() {
        return archive(
                """
                {"schema":2,"screens":[{
                  "id":"example:showcase","kind":"hud","title":"Showcase",
                  "widgets":[{
                    "type":"paged_table","id":"roster","columns":[
                      {"id":"name","label":"Name","align":"left","width_bucket":1},
                      {"id":"count","label":"Count","align":"right","width_bucket":4}
                    ]
                  },{
                    "type":"tabs","id":"pages","entries":[
                      {"id":"one","label":"One"}
                    ]
                  },{
                    "type":"input_number","id":"amount","label":"Amount",
                    "min":1,"max":64,"step":1
                  },{
                    "type":"input_text","id":"note","label":"Note","max_bytes":32
                  },{
                    "type":"select_enum","id":"mode","label":"Mode","options":[
                      {"id":"fast","label":"Fast"}
                    ]
                  },{
                    "type":"resource_panel","id":"cost","label":"Cost","entries":[
                      {"id":"ruby","label":"Ruby"}
                    ]
                  },{
                    "type":"action_button","action_id":"confirm","label":"Confirm"
                  },{
                    "type":"action_button","action_id":"locked","label":"Locked",
                    "enabled":false,"deny_reason":"Garrison is not yours"
                  },{
                    "type":"world_marker","id":"anchor","label":"Anchor",
                    "action_id":"place","preview_id":"example:camp",
                    "formation":"wedge","radius":8
                  }]
                }],"blocks":[],"items":[],"assets":[]}
                """,
                Map.of());
    }

    static byte[] archive(String index, Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                write(zip, "solaris-client.json", index.getBytes(StandardCharsets.UTF_8));
                for (var entry : entries.entrySet()) {
                    write(zip, entry.getKey(), entry.getValue());
                }
            }
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    static byte[] archiveWithLeadingEntry(String index) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                write(zip, "assets/example/leading.bin", new byte[] {'x'});
                write(zip, "solaris-client.json", index.getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void write(ZipOutputStream zip, String path, byte[] bytes)
            throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
}
