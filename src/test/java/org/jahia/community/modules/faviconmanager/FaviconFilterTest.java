package org.jahia.community.modules.faviconmanager;

import org.jahia.exceptions.JahiaException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.jcr.RepositoryException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaviconFilterTest {

    private static final String SITE_KEY = "mySite";

    @Mock
    private JahiaSitesService jahiaSitesService;

    private final FaviconFilter filter = new FaviconFilter();

    private JCRNodeWrapper mockSiteNode() {
        JahiaSite site = mock(JahiaSite.class, withSettings().extraInterfaces(JCRNodeWrapper.class));
        return (JCRNodeWrapper) site;
    }

    @Test
    void returnsEmptyWhenSiteIsNotAJcrNodeWrapper() throws Exception {
        setSiteByKeyResult(mock(JahiaSite.class));

        assertTrue(filter.resolveFaviconPath(SITE_KEY).isEmpty());
    }

    @Test
    void returnsEmptyWhenSiteLacksFaviconMixin() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        when(siteNode.isNodeType("jmix:favicon")).thenReturn(false);
        setSiteByKeyResult(siteNode);

        assertTrue(filter.resolveFaviconPath(SITE_KEY).isEmpty());
    }

    @Test
    void returnsEmptyWhenFaviconPropertyIsNotSet() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        when(siteNode.isNodeType("jmix:favicon")).thenReturn(true);
        when(siteNode.hasProperty("favicon")).thenReturn(false);
        setSiteByKeyResult(siteNode);

        assertTrue(filter.resolveFaviconPath(SITE_KEY).isEmpty());
    }

    @Test
    void returnsFaviconNodePathWhenMixinAndPropertyArePresent() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        JCRPropertyWrapper faviconProperty = mock(JCRPropertyWrapper.class);
        JCRNodeWrapper faviconNode = mock(JCRNodeWrapper.class);

        when(siteNode.isNodeType("jmix:favicon")).thenReturn(true);
        when(siteNode.hasProperty("favicon")).thenReturn(true);
        when(siteNode.getProperty("favicon")).thenReturn(faviconProperty);
        when(faviconProperty.getNode()).thenReturn(faviconNode);
        when(faviconNode.getPath()).thenReturn("/sites/mySite/files/favicon.png");
        setSiteByKeyResult(siteNode);

        Optional<String> result = filter.resolveFaviconPath(SITE_KEY);

        assertTrue(result.isPresent());
        assertEquals("/sites/mySite/files/favicon.png", result.get());
    }

    @Test
    void propagatesRepositoryExceptionFromJcrCalls() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        when(siteNode.isNodeType("jmix:favicon")).thenThrow(new RepositoryException("boom"));
        setSiteByKeyResult(siteNode);

        assertThrows(RepositoryException.class, () -> filter.resolveFaviconPath(SITE_KEY));
    }

    @Test
    void propagatesJahiaExceptionFromSiteLookup() throws Exception {
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenThrow(new JahiaException("msg", "desc", 1, 1));
        injectJahiaSitesService();

        assertThrows(JahiaException.class, () -> filter.resolveFaviconPath(SITE_KEY));
    }

    private void setSiteByKeyResult(Object siteByKeyResult) throws JahiaException {
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn((JahiaSite) siteByKeyResult);
        injectJahiaSitesService();
    }

    private void injectJahiaSitesService() {
        try {
            java.lang.reflect.Field field = FaviconFilter.class.getDeclaredField("jahiaSitesService");
            field.setAccessible(true);
            field.set(filter, jahiaSitesService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
