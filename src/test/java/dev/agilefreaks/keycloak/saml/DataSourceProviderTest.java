package dev.agilefreaks.keycloak.saml;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceProviderTest {

    @Test
    void instanceIsSingleton() {
        DataSourceProvider a = DataSourceProvider.getInstance();
        DataSourceProvider b = DataSourceProvider.getInstance();
        assertSame(a, b);
    }

    @Test
    void returnsSameDataSourceForSameCredentials() {
        DataSourceProvider provider = DataSourceProvider.getInstance();
        // Use an H2 in-memory URL so HikariCP can actually create the pool
        String url = "jdbc:h2:mem:test_same_creds;DB_CLOSE_DELAY=-1";
        DataSource ds1 = provider.getDataSource(url, "sa", "");
        DataSource ds2 = provider.getDataSource(url, "sa", "");
        assertSame(ds1, ds2);
    }

    @Test
    void returnsDifferentDataSourceForDifferentUrls() {
        DataSourceProvider provider = DataSourceProvider.getInstance();
        DataSource ds1 = provider.getDataSource("jdbc:h2:mem:test_diff_a;DB_CLOSE_DELAY=-1", "sa", "");
        DataSource ds2 = provider.getDataSource("jdbc:h2:mem:test_diff_b;DB_CLOSE_DELAY=-1", "sa", "");
        assertNotSame(ds1, ds2);
    }
}
