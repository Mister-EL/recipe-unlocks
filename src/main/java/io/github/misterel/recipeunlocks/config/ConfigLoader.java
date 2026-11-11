package io.github.misterel.recipeunlocks.config;

import io.github.misterel.recipeunlocks.unlock.ExprParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code config/recipe-unlocks.toml}.
 *
 * <p>The file is a small subset of TOML: {@code setting = true} lines, then {@code [unlocks]}
 * and one rule per line. It is read line by line instead of with a TOML library so that a
 * mistake costs one line, not the whole file, and is reported with its line number. Quotes
 * around rule names and rules are optional.
 */
public final class ConfigLoader {
    public static final String FILE_NAME = "recipe-unlocks.toml";
    private static final String DEFAULT_RESOURCE = "/recipe-unlocks-default.toml";

    private static final String LOCK_ALL = "lock_all_recipes";
    private static final String TEMPLATES = "template_unlocks";
    private static final String LOCK_WITHOUT_UNLOCK = "lock_recipes_without_unlock";
    private static final String LOCK_CRAFTER = "lock_crafter";
    private static final String BLOCK_MODDED = "block_modded_stations";
    private static final String UNLOCKS = "unlocks";
    private static final Set<String> SETTINGS = Set.of(LOCK_ALL, TEMPLATES, LOCK_WITHOUT_UNLOCK, LOCK_CRAFTER, BLOCK_MODDED);
    private static final Set<String> TRUE_WORDS = Set.of("true", "yes", "on", "да");
    private static final Set<String> FALSE_WORDS = Set.of("false", "no", "off", "нет");

    /** What an empty file means: everything locked the vanilla way, no rules. */
    public static final ModConfig DEFAULTS = parse("");

    /** The file could not be read from disk at all, so there is no config to use. */
    public static final class UnreadableException extends Exception {
        public UnreadableException(String message) {
            super(message);
        }
    }

    private enum Section { SETTINGS, UNLOCKS, UNKNOWN }

    private record KeyValue(String key, String value) {}

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
        } catch (CharacterCodingException e) {
            throw new UnreadableException("файл " + file + " сохранён не в кодировке UTF-8; пересохраните его в UTF-8");
        } catch (IOException e) {
            throw new UnreadableException("не удалось прочитать " + file + ": " + e);
        }
    }

    public static ModConfig parse(String text) {
        List<String> problems = new ArrayList<>();
        Map<String, Boolean> settings = new HashMap<>();
        Map<String, ModConfig.Rule> rules = new LinkedHashMap<>();
        Map<String, Integer> ruleLines = new HashMap<>();
        Section section = Section.SETTINGS;

        String[] lines = (text.startsWith("﻿") ? text.substring(1) : text).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            String where = "строка " + (i + 1) + ": ";
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[")) {
                section = readHeader(line, where, problems);
                continue;
            }
            if (section == Section.UNKNOWN) {
                continue;
            }
            KeyValue pair = split(line, where, problems);
            if (pair == null) {
                continue;
            }
            String name = pair.key.toLowerCase(Locale.ROOT);
            if (section == Section.SETTINGS) {
                if (!SETTINGS.contains(name)) {
                    problems.add(where + "неизвестная настройка '" + pair.key + "' (правила пишутся ниже строки [unlocks])");
                } else {
                    Boolean flag = TRUE_WORDS.contains(pair.value.toLowerCase(Locale.ROOT)) ? Boolean.TRUE
                            : FALSE_WORDS.contains(pair.value.toLowerCase(Locale.ROOT)) ? Boolean.FALSE : null;
                    if (flag == null) {
                        problems.add(where + "'" + pair.key + "' должно быть true или false, а написано '" + pair.value + "'");
                    } else if (settings.put(name, flag) != null) {
                        problems.add(where + "настройка '" + pair.key + "' задана повторно, действует эта строка");
                    }
                }
            } else if (SETTINGS.contains(name)) {
                problems.add(where + "настройка '" + pair.key + "' должна стоять выше строки [unlocks]; здесь она не действует");
            } else if (pair.value.startsWith("[")) {
                problems.add(where + "'" + pair.key + "': списки не поддерживаются, соедините условия через &");
            } else if (ruleLines.containsKey(pair.key)) {
                problems.add(where + "для '" + pair.key + "' уже есть правило в строке " + ruleLines.get(pair.key)
                        + ", эта строка пропущена; соедините условия через & или |");
            } else {
                ruleLines.put(pair.key, i + 1);
                try {
                    rules.put(pair.key, new ModConfig.Rule(pair.key, ExprParser.parse(pair.value)));
                } catch (ExprParser.SyntaxException e) {
                    problems.add(where + "'" + pair.key + "': " + e.getMessage());
                }
            }
        }

        return new ModConfig(
                settings.getOrDefault(LOCK_ALL, true),
                settings.getOrDefault(TEMPLATES, true),
                settings.getOrDefault(LOCK_WITHOUT_UNLOCK, false),
                settings.getOrDefault(LOCK_CRAFTER, true),
                settings.getOrDefault(BLOCK_MODDED, true),
                List.copyOf(rules.values()),
                List.copyOf(problems));
    }

    private static Section readHeader(String line, String where, List<String> problems) {
        int close = line.indexOf(']');
        String name = (close < 0 ? line.substring(1) : line.substring(1, close)).strip();
        String rest = close < 0 ? "" : line.substring(close + 1).strip();
        if (close < 0 || !rest.isEmpty() && !rest.startsWith("#")) {
            problems.add(where + "заголовок раздела пишется отдельной строкой: [" + name + "]");
        }
        if (name.equalsIgnoreCase(UNLOCKS)) {
            return Section.UNLOCKS;
        }
        problems.add(where + "неизвестный раздел [" + name + "], его строки пропущены; правила пишутся ниже строки [unlocks]");
        return Section.UNKNOWN;
    }

    /** Splits {@code key = value}; both sides may be wrapped in single or double quotes. */
    private static @Nullable KeyValue split(String line, String where, List<String> problems) {
        char first = line.charAt(0);
        boolean quotedKey = first == '"' || first == '\'';
        // keyEnd: the closing quote of a quoted key, otherwise the "=" itself.
        int keyEnd = quotedKey ? line.indexOf(first, 1) : line.indexOf('=');
        if (quotedKey && keyEnd < 0) {
            problems.add(where + "не закрыта кавычка");
            return null;
        }
        int equals = keyEnd;
        if (quotedKey) {
            equals = keyEnd + 1;
            while (equals < line.length() && Character.isWhitespace(line.charAt(equals))) {
                equals++;
            }
        }
        if (equals < 0 || equals >= line.length() || line.charAt(equals) != '=') {
            problems.add(where + "ожидалось 'что = значение', а написано '" + line + "'");
            return null;
        }
        String key = (quotedKey ? line.substring(1, keyEnd) : line.substring(0, keyEnd)).strip();
        if (key.isEmpty()) {
            problems.add(where + "перед '=' ничего нет");
            return null;
        }
        String value = line.substring(equals + 1).strip();
        if (value.startsWith("\"") || value.startsWith("'")) {
            int close = value.indexOf(value.charAt(0), 1);
            String rest = close < 0 ? "" : value.substring(close + 1).strip();
            if (close < 0) {
                problems.add(where + "не закрыта кавычка");
                return null;
            }
            if (!rest.isEmpty() && !rest.startsWith("#")) {
                problems.add(where + "лишний текст после закрывающей кавычки: '" + rest + "' (кавычки можно не писать вовсе)");
                return null;
            }
            return new KeyValue(key, value.substring(1, close).strip());
        }
        return new KeyValue(key, cutComment(value));
    }

    /** A comment starts with a # that stands alone; "#minecraft:logs" is a tag, not a comment. */
    private static String cutComment(String value) {
        for (int i = value.indexOf('#'); i >= 0; i = value.indexOf('#', i + 1)) {
            boolean spaceBefore = i == 0 || Character.isWhitespace(value.charAt(i - 1));
            boolean spaceAfter = i == value.length() - 1 || Character.isWhitespace(value.charAt(i + 1));
            if (spaceBefore && spaceAfter) {
                return value.substring(0, i).strip();
            }
        }
        return value;
    }
}
