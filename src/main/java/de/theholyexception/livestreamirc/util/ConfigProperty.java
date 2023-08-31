package de.theholyexception.livestreamirc.util;


import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Objects;
import java.util.Properties;

@Slf4j
public class ConfigProperty {

    private final File file;
    private Properties properties;
    private final String headline;

    public ConfigProperty(File file, String headline) {
        Objects.requireNonNull(file);
        this.file = file;
        this.headline = (headline == null ? "HolyAPI ConfigProperty" : headline);
        properties = new Properties();
    }
    public void createNew() {
        try {
            file.createNewFile();
            FileOutputStream os = new FileOutputStream(file);
            properties.store(os, headline);
            os.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public boolean createNewIfNotExists() {
        if (!file.exists()) {
            createNew();
            return true;
        }
        return false;
    }

    public void loadConfig() {
        try (FileInputStream fis = new FileInputStream(file)) {
            properties = new Properties();
            properties.load(fis);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            properties.store(fos, null);
            fos.flush();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public String getValue(Object path) {
        if (properties == null) throw new IllegalStateException("Configuration is not loaded");
        if (!properties.containsKey(path)) {
            log.error("Failed to get config value of path: " + path);
            return null;
        }
        return properties.get(path).toString();
    }

    public void setDefault(String path, String value) {
        if (properties == null) throw new IllegalStateException("Configuration is not loaded");
        properties.computeIfAbsent(path, k->value);
    }

    public void setValue(Object path, Object value) {
        if (properties == null) throw new IllegalStateException("Configuration is not loaded");
        properties.put(path, value);
    }

    public File getFile() {
        return file;
    }

}
