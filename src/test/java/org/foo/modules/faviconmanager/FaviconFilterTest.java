package org.foo.modules.faviconmanager;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.seo.urlrewrite.ServerNameToSiteMapper;
import org.jahia.services.sites.JahiaSite;
import org.jahia.services.sites.JahiaSitesService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class FaviconFilterTest {

    private FaviconFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @Before
    public void setUp() {
        filter = new FaviconFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @Test
    public void nonFaviconUri_doesNotResolveFavicon_andContinuesChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/home.html");

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class)) {
            filter.doFilter(request, response, chain);

            mapper.verifyNoInteractions();
        }

        verify(request, never()).setAttribute(eq("faviconPath"), any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    public void faviconUri_withFaviconSite_setsFaviconPath_andContinuesChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/favicon.ico");

        // The filter casts getSiteByKey()'s JahiaSite result to JCRNodeWrapper, so the
        // site mock must satisfy both types at once.
        JCRNodeWrapper siteNode = mock(JCRNodeWrapper.class, withSettings().extraInterfaces(JahiaSite.class));
        JCRNodeWrapper faviconNode = mock(JCRNodeWrapper.class);
        JCRPropertyWrapper faviconProperty = mock(JCRPropertyWrapper.class);

        when(siteNode.isNodeType("jmix:favicon")).thenReturn(true);
        when(siteNode.hasProperty("favicon")).thenReturn(true);
        when(siteNode.getProperty("favicon")).thenReturn(faviconProperty);
        when(faviconProperty.getNode()).thenReturn(faviconNode);
        when(faviconNode.getPath()).thenReturn("/sites/x/files/favicon.png");

        JahiaSitesService sitesService = mock(JahiaSitesService.class);
        when(sitesService.getSiteByKey("x")).thenReturn((JahiaSite) siteNode);

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class);
             MockedStatic<JahiaSitesService> sites = mockStatic(JahiaSitesService.class)) {

            mapper.when(() -> ServerNameToSiteMapper.getSiteKeyByServerName(request)).thenReturn("x");
            sites.when(JahiaSitesService::getInstance).thenReturn(sitesService);

            filter.doFilter(request, response, chain);
        }

        verify(request).setAttribute("faviconPath", "/sites/x/files/favicon.png");
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    public void faviconUri_withNullSiteNode_setsNoAttribute_andContinuesChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/favicon.ico");

        JahiaSitesService sitesService = mock(JahiaSitesService.class);
        when(sitesService.getSiteByKey(anyString())).thenReturn(null);

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class);
             MockedStatic<JahiaSitesService> sites = mockStatic(JahiaSitesService.class)) {

            mapper.when(() -> ServerNameToSiteMapper.getSiteKeyByServerName(request)).thenReturn("x");
            sites.when(JahiaSitesService::getInstance).thenReturn(sitesService);

            filter.doFilter(request, response, chain);
        }

        verify(request, never()).setAttribute(eq("faviconPath"), any());
        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    public void faviconUri_whenResolutionThrows_setsNoAttribute_andStillContinuesChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/favicon.ico");

        try (MockedStatic<ServerNameToSiteMapper> mapper = mockStatic(ServerNameToSiteMapper.class)) {
            mapper.when(() -> ServerNameToSiteMapper.getSiteKeyByServerName(request))
                    .thenThrow(new RuntimeException("boom"));

            filter.doFilter(request, response, chain);
        }

        verify(request, never()).setAttribute(eq("faviconPath"), any());
        verify(chain, times(1)).doFilter(request, response);
    }
}
