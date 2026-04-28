# keycloak-saml-database-federation-attribute-provider

### Compatible with Keycloak 26.4.

Keycloak SAML protocol mapper that fetches user attributes from an external JDBC database at assertion build time. Column names from the SQL query result become SAML attribute names.

## Build

```bash
mvn clean package
```

## Add to Keycloak

### Dockerfile

Add the .jar package before building Keycloak.

```Dockerfile
# ...
ADD --chown=keycloak:keycloak --chmod=644 \
    https://github.com/Agilefreaks/keycloak-saml-database-federation-attribute-provider/releases/download/v0.1.0/keycloak-saml-database-federation-attribute-provider-with-dependencies.jar \
    /opt/keycloak/providers/
# ...
```

### Local

1. Copy `keycloak-saml-database-federation-attribute-provider-with-dependencies.jar` to /providers.
2. Build and start keycloak:

```bash
./bin/kc.sh start-dev
```

## Configuration

Add the **Database Attribute Mapper** protocol mapper to a SAML client with:

| Property | Description |
|----------|-------------|
| Database URL | JDBC connection URL |
| Database User | Database username |
| Database Password | Database password |
| SQL Query | Query with `?` placeholder for user ID. Column aliases become attribute names. |
| User ID Attribute | Keycloak user attribute containing the external user ID (default: `external_id`) |
