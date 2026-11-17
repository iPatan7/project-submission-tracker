package edu.cu.tracker;

/**
 * All settings come from environment variables so the same image
 * can run in dev, test and production without a rebuild.
 */
public record Config(String env, int port, String storeType,
                     String redisHost, int redisPort, String redisPassword,
                     String facultyToken, String version) {

    public static Config fromEnv() {
        return new Config(
                get("APP_ENV", "dev"),
                Integer.parseInt(get("APP_PORT", "8080")),
                get("STORE_TYPE", "redis"),
                get("REDIS_HOST", "localhost"),
                Integer.parseInt(get("REDIS_PORT", "6379")),
                System.getenv("REDIS_PASSWORD"),
                System.getenv("FACULTY_TOKEN"),
                get("APP_VERSION", "dev"));
    }

    private static String get(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }
}
