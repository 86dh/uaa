package org.cloudfoundry.identity.uaa.oauth.provider.config.xml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Moved test class of from spring-security-oauth2 into UAA
 * Scope: Test class
 */
class AuthorizationServerBeanDefinitionParserTest {

    private AuthorizationServerBeanDefinitionParser authorizationServerBeanDefinitionParser;
    private Element element;
    private Element elementAuthzCode;
    private ParserContext parserContext;
    private XmlReaderContext xmlReaderContext;

    @BeforeEach
    void setUp() {
        authorizationServerBeanDefinitionParser = new AuthorizationServerBeanDefinitionParser();
        element = mock(Element.class);
        elementAuthzCode = mock(Element.class);
        Element elementRefreshToken = mock(Element.class);
        Element elementImplicit = mock(Element.class);
        Element elementClientCredentials = mock(Element.class);
        Element elementCustomGrant = mock(Element.class);
        Element elementPassword = mock(Element.class);
        parserContext = mock(ParserContext.class);
        NodeList nodeList = mock(NodeList.class);
        xmlReaderContext = mock(XmlReaderContext.class);
        when(parserContext.getReaderContext()).thenReturn(xmlReaderContext);
        when(parserContext.getRegistry()).thenReturn(mock(BeanDefinitionRegistry.class));
        when(element.getAttribute("client-details-service-ref")).thenReturn("client_id");
        when(element.getChildNodes()).thenReturn(nodeList);
        when(nodeList.getLength()).thenReturn(6);
        when(nodeList.item(0)).thenReturn(elementAuthzCode);
        when(nodeList.item(1)).thenReturn(elementRefreshToken);
        when(nodeList.item(2)).thenReturn(elementImplicit);
        when(nodeList.item(3)).thenReturn(elementClientCredentials);
        when(nodeList.item(4)).thenReturn(elementPassword);
        when(nodeList.item(5)).thenReturn(elementCustomGrant);
        when(elementAuthzCode.getNodeName()).thenReturn("authorization-code");
        when(elementRefreshToken.getNodeName()).thenReturn("refresh-token");
        when(elementImplicit.getNodeName()).thenReturn("implicit");
        when(elementClientCredentials.getNodeName()).thenReturn("client-credentials");
        when(elementPassword.getNodeName()).thenReturn("password");
        when(elementCustomGrant.getNodeName()).thenReturn("custom-grant");
        when(elementCustomGrant.getAttribute("token-granter-ref")).thenReturn("custom-grant");
    }

    @Test
    void parseEndpointAndReturnFilter() {
        assertThat(authorizationServerBeanDefinitionParser.
                parseEndpointAndReturnFilter(element, parserContext, "tokenRef", "serialRef")).isNull();
    }

    @Test
    void parseEndpointAndReturnFilterUsingEndpoints() {
        when(element.getAttribute("token-endpoint-url")).thenReturn("token-endpoint-url");
        when(element.getAttribute("authorization-endpoint-url")).thenReturn("authorization-endpoint-url");
        when(element.getAttribute("user-approval-page")).thenReturn("user-approval-page");
        when(element.getAttribute("approval-parameter-name")).thenReturn("approval-parameter-name");
        when(element.getAttribute("check-token-enabled")).thenReturn("true");
        when(element.getAttribute("check-token-endpoint-url")).thenReturn("check-token-endpoint-url");
        when(element.getAttribute("redirect-resolver-ref")).thenReturn("redirect-resolver-ref");
        when(elementAuthzCode.getAttribute("client-token-cache-ref")).thenReturn("client-token-cache-ref");
        when(element.getAttribute("redirect-strategy-ref")).thenReturn("redirect-strategy-ref");
        when(element.getAttribute("error-page")).thenReturn("error");
        assertThat(authorizationServerBeanDefinitionParser.
                parseEndpointAndReturnFilter(element, parserContext, "tokenRef", "serialRef")).isNull();
    }

    @Test
    void parseEndpointAndReturnFilterExistingUserApprovalPage() {
        when(element.getAttribute("token-endpoint-url")).thenReturn("token-endpoint-url");
        when(element.getAttribute("authorization-endpoint-url")).thenReturn("authorization-endpoint-url");
        when(element.getAttribute("user-approval-page")).thenReturn("user-approval-page");
        when(element.getAttribute("approval-parameter-name")).thenReturn("approval-parameter-name");
        when(element.getAttribute("check-token-enabled")).thenReturn("true");
        when(element.getAttribute("user-approval-handler-ref")).thenReturn("user-approval-handler-ref");
        assertThat(authorizationServerBeanDefinitionParser.
                parseEndpointAndReturnFilter(element, parserContext, "tokenRef", "serialRef")).isNull();
    }

    @Test
    void parseEndpointNoClientRef() {
        when(element.getAttribute("client-details-service-ref")).thenReturn(null);
        assertThat(authorizationServerBeanDefinitionParser.
                parseEndpointAndReturnFilter(element, parserContext, "tokenRef", "serialRef")).isNull();
        verify(xmlReaderContext).error("ClientDetailsService must be provided", element);
    }
}
