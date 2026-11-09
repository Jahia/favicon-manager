package org.jahia.community.modules.faviconmanager;

import org.jahia.exceptions.JahiaException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.seo.urlrewrite.ServerNameToSiteMapper;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.jcr.RepositoryException;
import javax.servlet.FilterChain;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FaviconFilterTest {

    private static final String SITE_KEY = "mySite";

    @Mock
    private JahiaSitesService jahiaSitesService;

    @InjectMocks
    private FaviconFilter filter;

    private JCRNodeWrapper mockSiteNode() {
        JahiaSite site = mock(JahiaSite.class, withSettings().extraInterfaces(JCRNodeWrapper.class));
        return (JCRNodeWrapper) site;
    }

    // ─── resolveFaviconPath ─────────────────────────────────────────────────

    @Test
    void returnsEmptyWhenNoSiteIsFound() throws Exception {
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn(null);

        assertTrue(filter.resolveFaviconPath(SITE_KEY).isEmpty());
    }

    @Test
    void returnsEmptyWhenSiteIsNotAJcrNodeWrapper() throws Exception {
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn(mock(JahiaSite.class));

        assertTrue(filter.resolveFaviconPath(SITE_KEY).isEmpty());
    }

    @Test
    void returnsEmptyWhenSiteLacksFaviconMixin() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        when(siteNode.isNodeType("jmix:favicon")).thenReturn(false);
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn((JahiaSite) siteNode);

        assertTrue(filter.resolveFaviconPath(SITE_KEY).isEmpty());
    }

    @Test
    void returnsEmptyWhenFaviconPropertyIsNotSet() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        when(siteNode.isNodeType("jmix:favicon")).thenReturn(true);
        when(siteNode.hasProperty("favicon")).thenReturn(false);
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn((JahiaSite) siteNode);

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
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn((JahiaSite) siteNode);

        Optional<String> result = filter.resolveFaviconPath(SITE_KEY);

        assertTrue(result.isPresent());
        assertEquals("/sites/mySite/files/favicon.png", result.get());
    }

    @Test
    void propagatesRepositoryExceptionFromJcrCalls() throws Exception {
        JCRNodeWrapper siteNode = mockSiteNode();
        when(siteNode.isNodeType("jmix:favicon")).thenThrow(new RepositoryException("boom"));
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn((JahiaSite) siteNode);

        assertThrows(RepositoryException.class, () -> filter.resolveFaviconPath(SITE_KEY));
    }

    @Test
    void propagatesJahiaExceptionFromSiteLookup() throws Exception {
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenThrow(new JahiaException("msg", "desc", 1, 1));

        assertThrows(JahiaException.class, () -> filter.resolveFaviconPath(SITE_KEY));
    }

    // ─── doFilter ───────────────────────────────────────────────────────────
    // These verify the robustness guarantee that the filter chain always
    // proceeds, regardless of how site/favicon resolution goes.

    @Test
    void doFilterSetsFaviconPathAttributeAndContinuesChain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        JCRNodeWrapper siteNode = mockSiteNode();
        JCRPropertyWrapper faviconProperty = mock(JCRPropertyWrapper.class);
        JCRNodeWrapper faviconNode = mock(JCRNodeWrapper.class);
        when(siteNode.isNodeType("jmix:favicon")).thenReturn(true);
        when(siteNode.hasProperty("favicon")).thenReturn(true);
        when(siteNode.getProperty("favicon")).thenReturn(faviconProperty);
        when(faviconProperty.getNode()).thenReturn(faviconNode);
        when(faviconNode.getPath()).thenReturn("/sites/mySite/files/favicon.png");
        when(jahiaSitesService.getSiteByKey(SITE_KEY)).thenReturn((JahiaSite) siteNode);

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class)) {
            mapper.when(() -> ServerNameToSiteMapper.getSiteKeyByServerName(request)).thenReturn(SITE_KEY);

            filter.doFilter(request, response, chain);
        }

        verify(request).setAttribute(FaviconFilter.FAVICON_PATH_ATTRIBUTE, "/sites/mySite/files/favicon.png");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterContinuesChainWhenNoSiteIsMapped() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class)) {
            mapper.when(() -> ServerNameToSiteMapper.getSiteKeyByServerName(request)).thenReturn(null);

            filter.doFilter(request, response, chain);
        }

        verify(request, never()).setAttribute(eq(FaviconFilter.FAVICON_PATH_ATTRIBUTE), any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterAlwaysContinuesChainEvenWhenSiteResolutionThrows() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        ServletResponse response = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class)) {
            mapper.when(() -> ServerNameToSiteMapper.getSiteKeyByServerName(request))
                    .thenThrow(new RuntimeException("boom"));

            filter.doFilter(request, response, chain);
        }

        verify(request, never()).setAttribute(eq(FaviconFilter.FAVICON_PATH_ATTRIBUTE), any());
        verify(chain, times(1)).doFilter(request, response);
    }
}
