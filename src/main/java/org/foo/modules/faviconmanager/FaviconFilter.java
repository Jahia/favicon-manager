package org.foo.modules.faviconmanager;

import org.jahia.bin.filters.AbstractServletFilter;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.seo.urlrewrite.ServerNameToSiteMapper;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component(immediate = true, service = AbstractServletFilter.class)
public class FaviconFilter extends AbstractServletFilter {

    private final Logger logger = LoggerFactory.getLogger(FaviconFilter.class);

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to do
    }

    @Activate
    public void activate() {
        setMatchAllUrls(true);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();

        if (requestURI.endsWith("/favicon.ico")) {
            try {
                String siteKey = ServerNameToSiteMapper.getSiteKeyByServerName(httpRequest);
                logger.info("Site key: {}", siteKey);
                JCRNodeWrapper siteNode = (JCRNodeWrapper) JahiaSitesService.getInstance().getSiteByKey(siteKey);

                if (siteNode != null && siteNode.isNodeType("jmix:favicon") && siteNode.hasProperty("favicon")) {
                    JCRNodeWrapper faviconNode = (JCRNodeWrapper) siteNode.getProperty("favicon").getNode();
                    logger.info("Favicon node path: {}", faviconNode.getPath());
                    request.setAttribute("faviconPath", faviconNode.getPath());
                } else {
                    logger.debug("No favicon found for site {}", siteKey);
                }
            } catch (Exception e) {
                logger.error("Error getting favicon", e);
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // nothing to do
    }
}