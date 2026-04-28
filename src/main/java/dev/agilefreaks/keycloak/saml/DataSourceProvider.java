package dev.agilefreaks.keycloak.saml;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataSourceProvider implements Closeable {

    private static final Logger LOG = Logger.getLogger(DataSourceProvider.class);
    private static final DataSourceProvider INSTANCE = new DataSourceProvider();

    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private DataSourceProvider() {
    }

    public static DataSourceProvider getInstance() {
        return INSTANCE;
    }

    public DataSource getDataSource(String url, String user, String password) {
        String key = url + "|" + user + "|" + password;
        return pools.computeIfAbsent(key, k -> createDataSource(url, user, password));
    }

    private HikariDataSource createDataSource(String url, String user, String password) {
        LOG.infof("Creating connection pool for %s", url);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setPoolName("saml-db-federation");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        return new HikariDataSource(config);
    }

    private void disposeAsync(HikariDataSource ds) {
        executor.submit(() -> {
            try {
                ds.close();
            } catch (Exception e) {
                LOG.error("Failed to close connection pool", e);
            }
        });
    }

    @Override
    public void close() {
        pools.values().forEach(this::disposeAsync);
        pools.clear();
        executor.shutdown();
    }
}
