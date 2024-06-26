package cn.marci.raft.conf;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

@Slf4j
public class PropertiesManager {

    private static Properties properties;

    private static void load() {
        try (InputStream resourceAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("conf.properties")) {
            properties = new Properties();
            if (resourceAsStream != null) {
                properties.load(resourceAsStream);
                log.info("load conf.properties success");
                log.info("{}", properties.toString());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties getProperties() {
        if (properties == null) {
            synchronized (PropertiesManager.class) {
                if (properties == null) {
                    load();
                }
            }
        }
        return properties;
    }

    public static String getConf(String key) {
        return getProperty(key);
    }

    public static String getConf(String key, String defaultValue) {
        String val = getProperty(key);
        return val == null ? defaultValue : val;
    }

    public static int getInt(String key, int defaultValue) {
        return Optional.ofNullable(getProperty(key))
                .map(Integer::parseInt)
                .orElse(defaultValue);
    }

    private static String getProperty(String key) {
        String property = System.getenv(key);
        if (property == null) {
            property = getProperties().getProperty(key);
        }
        return property;
    }
}
