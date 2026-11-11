package io.github.misterel.recipeunlocks.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import io.github.misterel.recipeunlocks.unlock.Expr;
import io.github.misterel.recipeunlocks.unlock.ExprParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads {@code config/recipe-unlocks.toml}. */
public final class ConfigLoader {
    public static final String FILE_NAME = "recipe-unlocks.toml";
    private static final String DEFAULT_RESOURCE = "/recipe-unlocks-default.toml";

    private static final String LOCK_ALL = "lock_all_recipes";
    private static final String TEMPLATES = "template_unlocks";
    private static final String LOCK_WITHOUT_UNLOCK = "lock_recipes_without_unlock";
    private static final String LOCK_CRAFTER = "lock_crafter";
    private static final String BLOCK_MODDED = "block_modded_stations";
    private static final String UNLOCKS = "unlocks";
    private static final Set<String> KNOWN_KEYS = Set.of(LOCK_ALL, TEMPLATES, LOCK_WITHOUT_UNLOCK, LOCK_CRAFTER, BLOCK_MODDED, UNLOCKS);

    /** The file could not be read as TOML at all, so there is no config to use. */
    public static final class UnreadableException extends Exception {
        public UnreadableException(String message) {
            super(message);
        }
    }

    /** What an empty file means: everything locked the vanilla way, no rules. */
    public static final ModConfig DEFAULTS = new ModConfig(true, true, false, true, true, List.of(), List.of());

    private ConfigLoader() {}

    /** Reads the file, creating it with the commented defaults when it does not exist yet. */
    public static ModConfig load(Path file) throws UnreadableException {
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                try (InputStream defaults = ConfigLoader.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    Files.copy(defaults, file);
                }
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UnreadableException("не удалось прочитать " + file + ": " + e);
        }
    }

    public static ModConfig parse(String text) throws UnreadableException {
        CommentedConfig root = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        try {
            new TomlParser().parse(new StringReader(addQuotes(text)), root, ParsingMode.MERGE);
        } catch (RuntimeException e) {
            throw new UnreadableException("ошибка в разметке файла (TOML): " + e.getMessage());
        }

        List<String> problems = new ArrayList<>();
        Map<String, Object> values = rawValues(root);
        for (String key : values.keySet()) {
            if (!KNOWN_KEYS.contains(key)) {
                problems.add("неизвестная настройка '" + key + "' (условия пишутся ниже строки [unlocks])");
            }
        }

        List<ModConfig.Rule> rules = new ArrayList<>();
        Object unlocks = values.get(UNLOCKS);
        if (unlocks instanceof Config table) {
            for (Map.Entry<String, Object> entry : rawValues(table).entrySet()) {
                try {
                    rules.add(new ModConfig.Rule(entry.getKey().trim(), parseRule(entry.getValue())));
                } catch (ExprParser.SyntaxException e) {
                    problems.add("'" + entry.getKey() + "': " + e.getMessage());
                }
            }
        } else if (unlocks != null) {
            problems.add("'" + UNLOCKS + "' должен быть разделом: строка [unlocks] и условия под ней");
        }

        return new ModConfig(
                flag(values, LOCK_ALL, true, problems),
                flag(values, TEMPLATES, true, problems),
                flag(values, LOCK_WITHOUT_UNLOCK, false, problems),
                flag(values, LOCK_CRAFTER, true, problems),
                flag(values, BLOCK_MODDED, true, problems),
                List.copyOf(rules),
                List.copyOf(problems));
    }

    /**
     * Lets rules be written without quotes: {@code minecraft:torch = break stone 3} becomes
     * {@code "minecraft:torch" = "break stone 3"}. Only lines below [unlocks] are touched.
     */
    static String addQuotes(String text) {
        StringBuilder out = new StringBuilder(text.length() + 64);
        boolean inUnlocks = false;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("[")) {
                inUnlocks = trimmed.replace(" ", "").startsWith("[" + UNLOCKS + "]");
            } else if (inUnlocks && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                line = addQuotesToRule(trimmed);
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String addQuotesToRule(String line) {
        char first = line.charAt(0);
        boolean quotedKey = first == '"' || first == '\'';
        // For a quoted key: the position right after its closing quote.
        int keyEnd = quotedKey ? line.indexOf(first, 1) + 1 : 0;
        int equals = quotedKey && keyEnd == 0 ? -1 : line.indexOf('=', keyEnd);
        if (equals < 0 || quotedKey && !line.substring(keyEnd, equals).isBlank()) {
            return line; // not "key = value": a line of a multi-line list
        }
        String key = quotedKey ? line.substring(0, keyEnd) : quote(line.substring(0, equals).strip());
        String value = line.substring(equals + 1).strip();
        if (!value.startsWith("\"") && !value.startsWith("'") && !value.startsWith("[")) {
            // "#tag" has no space after the #, a comment does.
            int comment = value.indexOf("# ");
            value = quote((comment < 0 ? value : value.substring(0, comment)).strip());
        }
        return key + " = " + value;
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** Keys exactly as written in the file (a key may contain dots), in file order. */
    private static Map<String, Object> rawValues(Config config) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Config.Entry entry : config.entrySet()) {
            values.put(entry.getKey(), entry.getRawValue());
        }
        return values;
    }

    /** A rule is one text line, or a list of lines that must all be fulfilled. */
    private static Expr parseRule(Object value) throws ExprParser.SyntaxException {
        if (value instanceof String text) {
            return ExprParser.parse(text);
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<Expr> parts = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String text)) {
                    throw new ExprParser.SyntaxException("в списке условий должны быть только строки в кавычках");
                }
                parts.add(ExprParser.parse(text));
            }
            return parts.size() == 1 ? parts.getFirst() : new Expr.And(List.copyOf(parts));
        }
        throw new ExprParser.SyntaxException("условие должно быть строкой в кавычках");
    }

    private static boolean flag(Map<String, Object> values, String key, boolean fallback, List<String> problems) {
        Object value = values.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value != null) {
            problems.add("'" + key + "' должно быть true или false, взято " + fallback);
        }
        return fallback;
    }
}
