package de.theholyexception.livestreamirc.util;

import lombok.extern.slf4j.Slf4j;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

@Slf4j
public class ConfigProvider {

    private final TomlParseResult result;

    public ConfigProvider(TomlParseResult result) {
        this.result = result;
    }

    public TomlTable getTable(String table) {
        TomlTable table1 = result.getTable(table);
        if (table1 == null) {
            log.warn("Failed to load table {}", table);
            return null;
        }
        return table1;
    }

    public String get(String table, String dottedKeys) {
        TomlTable table1 = result.getTable(table);
        if (table1 == null) {
            log.warn("Failed to load table {}", table);
            return null;
        }

        return table1.getString(dottedKeys);
    }
}
