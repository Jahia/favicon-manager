package org.jahia.community.modules.faviconmanager;

import org.jahia.bin.filters.AbstractServletFilter;
import org.jahia.exceptions.JahiaException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.seo.urlrewrite.ServerNameToSiteMapper;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;

@Component(immediate = true, service = AbstractServletFilter.class)
public class FaviconFilter extends AbstractServletFilter {

    static final String FAVICON_PATH_ATTRIBUTE = "faviconPath";

    private static final Logger logger = LoggerFactory.getLogger(FaviconFilter.class);

    @Reference
    private JahiaSitesService jahiaSitesService;

    @Override
    public void init(FilterConfig filterConfig) {
        // nothing to do
    }

    @Activate
    public void activate() {
        setUrlPatterns(new String[]{"/favicon.ico"});
        logger.info("FaviconFilter activated");
    }

    @Deactivate
    public void deactivate() {
        logger.info("FaviconFilter deactivated");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        handleFaviconRequest((HttpServletRequest) request);
        chain.doFilter(request, response);
    }

    private void handleFaviconRequest(HttpServletRequest request) {
        String serverName = request.getServerName();

        try {
            String siteKey = ServerNameToSiteMapper.getSiteKeyByServerName(request);
            if (siteKey == null) {
                logger.debug("No site mapped for server name: {}", serverName);
                return;
            }
            logger.debug("Site key resolved for {}: {}", serverName, siteKey);
            resolveFaviconPath(siteKey).ifPresentOrElse(
                    faviconPath -> {
                        logger.debug("Favicon path for site {}: {}", siteKey, faviconPath);
                        request.setAttribute(FAVICON_PATH_ATTRIBUTE, faviconPath);
                    },
                    () -> logger.debug("No favicon configured for site {}", siteKey)
            );
            // Checked exceptions come from the JCR API; RuntimeException is caught too so an
            // unexpected failure (e.g. from the injected OSGi service) never breaks the filter chain.
        } catch (RepositoryException | JahiaException | RuntimeException e) {
            logger.error("Error resolving favicon for server {}", serverName, e);
        }
    }

    Optional<String> resolveFaviconPath(String siteKey) throws RepositoryException, JahiaException {
        Object site = jahiaSitesService.getSiteByKey(siteKey);
        if (!(site instanceof JCRNodeWrapper)) {
            return Optional.empty();
        }

        JCRNodeWrapper siteNode = (JCRNodeWrapper) site;
        if (!siteNode.isNodeType("jmix:favicon") || !siteNode.hasProperty("favicon")) {
            return Optional.empty();
        }

        JCRNodeWrapper faviconNode = (JCRNodeWrapper) siteNode.getProperty("favicon").getNode();
        return Optional.of(faviconNode.getPath());
    }

    @Override
    public void destroy() {
        // nothing to do
    }
}
