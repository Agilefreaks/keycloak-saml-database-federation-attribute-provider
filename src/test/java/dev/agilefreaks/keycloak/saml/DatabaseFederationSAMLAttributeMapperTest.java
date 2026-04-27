package dev.agilefreaks.keycloak.saml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseFederationSAMLAttributeMapperTest {

    private DatabaseFederationSAMLAttributeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DatabaseFederationSAMLAttributeMapper();
    }

    @Test
    void providerIdIsCorrect() {
        assertEquals("db-federation-saml-attribute-mapper", mapper.getId());
    }

    @Test
    void displayCategoryIsAttributeStatement() {
        assertNotNull(mapper.getDisplayCategory());
    }

    @Test
    void displayTypeIsSet() {
        assertEquals("Federated Database Attribute Mapper", mapper.getDisplayType());
    }

    @Test
    void helpTextIsSet() {
        assertNotNull(mapper.getHelpText());
        assertFalse(mapper.getHelpText().isEmpty());
    }

    @Test
    void configPropertiesContainExpectedFields() {
        var props = mapper.getConfigProperties();
        var names = props.stream().map(p -> p.getName()).toList();

        assertTrue(names.contains(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_URL));
        assertTrue(names.contains(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_USER));
        assertTrue(names.contains(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_PASSWORD));
        assertTrue(names.contains(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_QUERY));
        assertTrue(names.contains(DatabaseFederationSAMLAttributeMapper.CONFIG_USER_ID_ATTRIBUTE));
    }

    @Test
    void configPropertiesHaveDefaults() {
        var props = mapper.getConfigProperties();
        var dbUrlProp = props.stream()
                .filter(p -> p.getName().equals(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_URL))
                .findFirst().orElseThrow();
        assertEquals("", dbUrlProp.getDefaultValue());

        var userIdProp = props.stream()
                .filter(p -> p.getName().equals(DatabaseFederationSAMLAttributeMapper.CONFIG_USER_ID_ATTRIBUTE))
                .findFirst().orElseThrow();
        assertEquals("external_id", userIdProp.getDefaultValue());
    }

    @Nested
    class TransformAttributeStatement {

        // session and clientSession are not used by the mapper, pass null
        @Test
        void skipsWhenNoQueryConfigured() {
            ProtocolMapperModel mappingModel = new ProtocolMapperModel();
            mappingModel.setConfig(Map.of());

            AttributeStatementType attributeStatement = new AttributeStatementType();

            mapper.transformAttributeStatement(attributeStatement, mappingModel, null, null, null);

            assertTrue(attributeStatement.getAttributes().isEmpty());
        }

        @Test
        void skipsWhenQueryIsBlank() {
            ProtocolMapperModel mappingModel = new ProtocolMapperModel();
            mappingModel.setConfig(Map.of(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_QUERY, "   "));

            AttributeStatementType attributeStatement = new AttributeStatementType();

            mapper.transformAttributeStatement(attributeStatement, mappingModel, null, null, null);

            assertTrue(attributeStatement.getAttributes().isEmpty());
        }

        @Test
        void skipsWhenUserHasNoIdAttribute() {
            UserModel user = mock(UserModel.class);
            when(user.getFirstAttribute("external_id")).thenReturn(null);
            when(user.getUsername()).thenReturn("testuser");

            UserSessionModel userSession = mock(UserSessionModel.class);
            when(userSession.getUser()).thenReturn(user);

            ProtocolMapperModel mappingModel = new ProtocolMapperModel();
            Map<String, String> config = new HashMap<>();
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_QUERY, "SELECT name FROM orgs WHERE id = ?");
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_USER_ID_ATTRIBUTE, "external_id");
            mappingModel.setConfig(config);

            AttributeStatementType attributeStatement = new AttributeStatementType();

            mapper.transformAttributeStatement(attributeStatement, mappingModel, null, userSession, null);

            assertTrue(attributeStatement.getAttributes().isEmpty());
        }

        @Test
        void skipsWhenDbConnectionFails() {
            UserModel user = mock(UserModel.class);
            when(user.getFirstAttribute("external_id")).thenReturn("12345");
            when(user.getUsername()).thenReturn("testuser");

            UserSessionModel userSession = mock(UserSessionModel.class);
            when(userSession.getUser()).thenReturn(user);

            ProtocolMapperModel mappingModel = new ProtocolMapperModel();
            Map<String, String> config = new HashMap<>();
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_QUERY, "SELECT name FROM orgs WHERE id = ?");
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_USER_ID_ATTRIBUTE, "external_id");
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_URL, "jdbc:postgresql://nonexistent:5432/db");
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_USER, "nobody");
            config.put(DatabaseFederationSAMLAttributeMapper.CONFIG_DB_PASSWORD, "nothing");
            mappingModel.setConfig(config);

            AttributeStatementType attributeStatement = new AttributeStatementType();

            // Should not throw — errors are caught and logged
            mapper.transformAttributeStatement(attributeStatement, mappingModel, null, userSession, null);

            assertTrue(attributeStatement.getAttributes().isEmpty());
        }
    }

    @Nested
    class SamlAttributeType {

        @Test
        void attributeTypeHasCorrectNameFormat() {
            AttributeType attr = new AttributeType("TestAttr");
            attr.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
            attr.setFriendlyName("TestAttr");
            attr.addAttributeValue("testValue");

            assertEquals("TestAttr", attr.getName());
            assertEquals("TestAttr", attr.getFriendlyName());
            assertEquals("urn:oasis:names:tc:SAML:2.0:attrname-format:uri", attr.getNameFormat());
            assertEquals(1, attr.getAttributeValue().size());
            assertEquals("testValue", attr.getAttributeValue().get(0));
        }

        @Test
        void attributeTypeWithNoValueHasEmptyList() {
            AttributeType attr = new AttributeType("EmptyAttr");
            attr.setFriendlyName("EmptyAttr");
            attr.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:uri");

            assertEquals(0, attr.getAttributeValue().size());
        }

        @Test
        void attributeStatementAcceptsMultipleAttributes() {
            AttributeStatementType statement = new AttributeStatementType();

            AttributeType attr1 = new AttributeType("Attr1");
            attr1.addAttributeValue("val1");
            statement.addAttribute(new AttributeStatementType.ASTChoiceType(attr1));

            AttributeType attr2 = new AttributeType("Attr2");
            attr2.addAttributeValue("val2");
            statement.addAttribute(new AttributeStatementType.ASTChoiceType(attr2));

            assertEquals(2, statement.getAttributes().size());
        }
    }
}
