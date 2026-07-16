/**
 * Favicon Manager – End-to-End Tests
 *
 * Scenarios covered:
 *  1. JCR state  – both test sites carry the jmix:favicon mixin and a correct favicon reference
 *  2. Per-domain serving – /favicon.ico returns the right image based on the Host header
 *  3. Cross-site isolation – responses for site A and site B are distinct
 *  4. Cache invalidation – changing a site's favicon and publishing reflects immediately
 *  5. Fallback / removal – removing the mixin produces a graceful non-error response
 *  6. Unknown domain – no 5xx when the Host maps to no known site
 */

import {createSite, deleteSite, removeMixins, uploadFile, publishAndWaitJobEnding} from '@jahia/cypress';
import {configureFavicon, restoreFavicon, faviconRequest} from '../support/favicon-helpers';

// ─── Test-site constants ──────────────────────────────────────────────────────

const SITE_A = {
    key: 'faviconTestSiteA',
    serverName: 'favicon-a.test',
    templateSet: 'digitall-website',
    faviconFixture: 'images/favicon-a.png',
    faviconFileName: 'test-favicon-a.png'
};

const SITE_B = {
    key: 'faviconTestSiteB',
    serverName: 'favicon-b.test',
    templateSet: 'digitall-website',
    faviconFixture: 'images/favicon-b.png',
    faviconFileName: 'test-favicon-b.png'
};

// ─── Pure helpers (no Cypress commands — safe to nest-count) ─────────────────

const hasFaviconMixin = (mixinTypes: {name: string}[]): boolean =>
    mixinTypes.some(m => m.name === 'jmix:favicon');

// ─── Test suite ───────────────────────────────────────────────────────────────

describe('Favicon Manager', () => {
    // ── Suite-level setup / teardown ─────────────────────────────────────────

    before(() => {
        cy.login();
        createSite(SITE_A.key, {serverName: SITE_A.serverName, templateSet: SITE_A.templateSet, locale: 'en'});
        createSite(SITE_B.key, {serverName: SITE_B.serverName, templateSet: SITE_B.templateSet, locale: 'en'});
        configureFavicon(SITE_A.key, SITE_A.faviconFixture, SITE_A.faviconFileName);
        configureFavicon(SITE_B.key, SITE_B.faviconFixture, SITE_B.faviconFileName);
    });

    after(() => {
        cy.login();
        deleteSite(SITE_A.key);
        deleteSite(SITE_B.key);
    });

    // ── 1. JCR configuration ─────────────────────────────────────────────────

    describe('JCR configuration', () => {
        it('site A should have the jmix:favicon mixin and a favicon reference', () => {
            cy.apollo({
                queryFile: 'graphql/query/getSiteFavicon.graphql',
                variables: {siteNodePath: `/sites/${SITE_A.key}`}
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
            }).then((resp: any) => {
                const node = resp.data.jcr.nodeByPath;
                expect(hasFaviconMixin(node.mixinTypes), 'jmix:favicon mixin is set').to.be.true;
                expect(node.property?.refNode?.path, 'favicon property points to the uploaded file').to.include(SITE_A.faviconFileName);
            });
        });

        it('site B should have the jmix:favicon mixin and a favicon reference', () => {
            cy.apollo({
                queryFile: 'graphql/query/getSiteFavicon.graphql',
                variables: {siteNodePath: `/sites/${SITE_B.key}`}
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
            }).then((resp: any) => {
                const node = resp.data.jcr.nodeByPath;
                expect(hasFaviconMixin(node.mixinTypes), 'jmix:favicon mixin is set').to.be.true;
                expect(node.property?.refNode?.path, 'favicon property points to the uploaded file').to.include(SITE_B.faviconFileName);
            });
        });
    });

    // ── 2. Per-domain serving ─────────────────────────────────────────────────

    describe('Domain-based favicon serving', () => {
        it('should serve an image for site A based on its domain', () => {
            faviconRequest(SITE_A.serverName).then(resp => {
                expect(resp.status, 'HTTP 200').to.eq(200);
                expect(resp.headers['content-type'], 'content-type is image/*').to.match(/^image\//);
                expect(resp.body, 'body is non-empty').to.have.length.greaterThan(0);
            });
        });

        it('should serve an image for site B based on its domain', () => {
            faviconRequest(SITE_B.serverName).then(resp => {
                expect(resp.status, 'HTTP 200').to.eq(200);
                expect(resp.headers['content-type'], 'content-type is image/*').to.match(/^image\//);
                expect(resp.body, 'body is non-empty').to.have.length.greaterThan(0);
            });
        });

        // ── 3. Cross-site isolation ───────────────────────────────────────────

        it('should serve DIFFERENT favicons for sites A and B', () => {
            // Capture site A response, then assert site B differs — sequential,
            // no need to nest one then() inside another.
            let bodyA: string;
            faviconRequest(SITE_A.serverName).then(respA => {
                expect(respA.status).to.eq(200);
                bodyA = respA.body;
            });
            faviconRequest(SITE_B.serverName).then(respB => {
                expect(respB.status).to.eq(200);
                expect(respB.body, 'site B favicon differs from site A favicon').to.not.equal(bodyA);
            });
        });

        // ── 6. Unknown domain ─────────────────────────────────────────────────

        it('should not crash (no 5xx) for an unknown domain', () => {
            faviconRequest('totally-unknown-host-99999.test').then(resp => {
                expect(resp.status, 'no server error').to.be.lessThan(500);
            });
        });
    });

    // ── 4. Cache invalidation ─────────────────────────────────────────────────

    describe('Favicon modification reflects immediately after publication', () => {
        const updatedFileName = 'test-favicon-a-v2.png';

        after(() => {
            // Restore site A's favicon by re-pointing to the already-uploaded original file.
            // Do NOT re-upload — configureFavicon would fail because the file already exists.
            cy.login();
            restoreFavicon(SITE_A.key, `/sites/${SITE_A.key}/files/${SITE_A.faviconFileName}`);
        });

        it('should serve the new favicon after changing the reference and publishing (no manual cache flush)', () => {
            // Step 1 – record what is currently being served for site A
            let originalBody: string;
            faviconRequest(SITE_A.serverName).then(respBefore => {
                expect(respBefore.status).to.eq(200);
                originalBody = respBefore.body;
            });

            // Step 2 – upload a different image (reuse site B's pixel) and update the reference.
            // Sequential: runs after Step 1 because Cypress queues all commands.
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            uploadFile(SITE_B.faviconFixture, `/sites/${SITE_A.key}/files`, updatedFileName, 'image/png').then((resp: any) => {
                const newUUID: string = resp.data.jcr.addNode.uuid;
                cy.apollo({
                    mutationFile: 'graphql/mutation/setFaviconProperty.graphql',
                    variables: {sitePath: `/sites/${SITE_A.key}`, faviconUUID: newUUID}
                });
            });

            // Step 3 – publish the change (sequential, runs after Step 2)
            publishAndWaitJobEnding(`/sites/${SITE_A.key}`);

            // Step 4 – the very next request must return the updated image without a server restart
            faviconRequest(SITE_A.serverName).then(respAfter => {
                expect(respAfter.status).to.eq(200);
                expect(respAfter.body, 'updated favicon is served immediately after publication').to.not.equal(originalBody);
            });
        });
    });

    // ── 5. Fallback / mixin removal ───────────────────────────────────────────

    describe('Graceful fallback when favicon is removed', () => {
        after(() => {
            // Restore site B's favicon by re-pointing to the already-uploaded original file.
            // Also re-adds jmix:favicon mixin that was removed during the test.
            cy.login();
            restoreFavicon(SITE_B.key, `/sites/${SITE_B.key}/files/${SITE_B.faviconFileName}`);
        });

        it('should not return a 5xx after removing the jmix:favicon mixin', () => {
            removeMixins(`/sites/${SITE_B.key}`, ['jmix:favicon']);
            publishAndWaitJobEnding(`/sites/${SITE_B.key}`);

            faviconRequest(SITE_B.serverName).then(resp => {
                expect(resp.status, 'no server error after mixin removal').to.be.lessThan(500);
            });
        });

        it('should no longer serve the site-specific favicon after mixin removal', () => {
            // Without the mixin the filter must not set the faviconPath attribute,
            // so the URL rewrite rule must not trigger and the custom image must not be served.
            // Capture fixture value sequentially to stay within the 4-callback nesting limit.
            let configuredFavicon: string;
            cy.fixture(SITE_B.faviconFixture, 'base64').then(f => {
                configuredFavicon = f as string;
            });
            faviconRequest(SITE_B.serverName).then(resp => {
                // Asserted unconditionally: an empty body is trivially "not equal" to the
                // configured favicon's bytes too, so this must never be skipped.
                expect(resp.body, 'site-specific favicon is no longer served').to.not.equal(configuredFavicon);
            });
        });
    });
});
