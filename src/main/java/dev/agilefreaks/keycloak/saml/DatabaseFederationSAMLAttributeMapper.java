package dev.agilefreaks.keycloak.saml;

import org.jboss.logging.Logger;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.AbstractSAMLProtocolMapper;
import org.keycloak.protocol.saml.mappers.AttributeStatementHelper;
import org.keycloak.protocol.saml.mappers.SAMLAttributeStatementMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;

public class DatabaseFederationSAMLAttributeMapper extends AbstractSAMLProtocolMapper
        implements SAMLAttributeStatementMapper {

    private static final String PROVIDER_ID = "db-federation-saml-attribute-mapper";
    private static final Logger LOG = Logger.getLogger(DatabaseFederationSAMLAttributeMapper.class);

    static final String CONFIG_DB_URL = "db.url";
    static final String CONFIG_DB_USER = "db.user";
    static final String CONFIG_DB_PASSWORD = "db.password";
    static final String CONFIG_DB_QUERY = "db.query";
    static final String CONFIG_USER_ID_ATTRIBUTE = "user.id.attribute";

    private static final String NAME_FORMAT_URI = "urn:oasis:names:tc:SAML:2.0:attrname-format:uri";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;

    static {
        CONFIG_PROPERTIES = ProviderConfigurationBuilder.create()
                .property()
                    .name(CONFIG_DB_URL)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("Database URL")
                    .helpText("JDBC connection URL")
                    .defaultValue("")
                    .add()
                .property()
                    .name(CONFIG_DB_USER)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("Database User")
                    .helpText("Database username")
                    .defaultValue("")
                    .add()
                .property()
                    .name(CONFIG_DB_PASSWORD)
                    .type(ProviderConfigProperty.PASSWORD)
                    .label("Database Password")
                    .helpText("Database password")
                    .defaultValue("")
                    .add()
                .property()
                    .name(CONFIG_DB_QUERY)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("SQL Query")
                    .helpText("SQL query with a single ? placeholder for the user ID. "
                            + "Column names become SAML attribute names. "
                            + "Use SQL aliases to control the attribute names, e.g. SELECT o.name AS \"CompanyName\".")
                    .add()
                .property()
                    .name(CONFIG_USER_ID_ATTRIBUTE)
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .label("User ID Attribute")
                    .helpText("Keycloak user attribute that contains the external user ID "
                            + "used as the query parameter.")
                    .defaultValue("external_id")
                    .add()
                .build();
    }

    @Override
    public String getDisplayCategory() {
        return AttributeStatementHelper.ATTRIBUTE_STATEMENT_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Federated Database Attribute Mapper";
    }

    @Override
    public String getHelpText() {
        return "Fetches user attributes from an external database at SAML assertion build time. "
                + "Each column returned by the SQL query becomes a SAML attribute.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public void transformAttributeStatement(
            AttributeStatementType attributeStatement,
            ProtocolMapperModel mappingModel,
            KeycloakSession session,
            UserSessionModel userSession,
            AuthenticatedClientSessionModel clientSession) {

        String query = mappingModel.getConfig().get(CONFIG_DB_QUERY);
        if (query == null || query.isBlank()) {
            LOG.warn("No SQL query configured for db-federation-saml-attribute-mapper, skipping");
            return;
        }

        UserModel user = userSession.getUser();
        String userIdAttribute = mappingModel.getConfig().getOrDefault(CONFIG_USER_ID_ATTRIBUTE, "external_id");
        String userId = user.getFirstAttribute(userIdAttribute);
        if (userId == null) {
            LOG.warnf("User %s has no attribute '%s', skipping DB attribute lookup", user.getUsername(), userIdAttribute);
            return;
        }

        String dbUrl = mappingModel.getConfig().get(CONFIG_DB_URL);
        String dbUser = mappingModel.getConfig().get(CONFIG_DB_USER);
        String dbPassword = mappingModel.getConfig().get(CONFIG_DB_PASSWORD);

        try (Connection conn = DataSourceProvider.getInstance().getDataSource(dbUrl, dbUser, dbPassword).getConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();

                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = meta.getColumnLabel(i);
                        String value = rs.getString(i);

                        AttributeType attr = new AttributeType(columnName);
                        attr.setFriendlyName(columnName);
                        attr.setNameFormat(NAME_FORMAT_URI);

                        if (value != null && !value.isEmpty()) {
                            attr.addAttributeValue(value);
                        }

                        attributeStatement.addAttribute(
                                new AttributeStatementType.ASTChoiceType(attr));
                    }

                    LOG.debugf("Added %d DB attributes for user %s", columnCount, user.getUsername());
                } else {
                    LOG.warnf("No DB results for user %s (id=%s)", user.getUsername(), userId);
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch DB attributes for user %s (id=%s)", user.getUsername(), userId);
        }
    }

}
