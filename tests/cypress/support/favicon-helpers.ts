/**
 * Shared helpers for the favicon-manager Cypress suites (01-Tests.cy.ts and
 * 02-UI-Tests.cy.ts). Kept in one place so a change to the setup/teardown
 * flow or the request contract only needs to be made once.
 */

import {addMixins, getNodeByPath, uploadFile, publishAndWaitJobEnding} from '@jahia/cypress';

/**
 * Uploads a PNG fixture to a site's /files folder, adds the jmix:favicon mixin
 * to the site root node, sets the favicon weak-reference property, then publishes.
 * Intended for initial setup only — if the file already exists, use restoreFavicon instead.
 */
export const configureFavicon = (siteKey: string, fixtureRelPath: string, fileName: string): void => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    uploadFile(fixtureRelPath, `/sites/${siteKey}/files`, fileName, 'image/png').then((resp: any) => {
        const uuid: string = resp.data.jcr.addNode.uuid;
        addMixins(`/sites/${siteKey}`, ['jmix:favicon']);
        cy.apollo({
            mutationFile: 'graphql/mutation/setFaviconProperty.graphql',
            variables: {sitePath: `/sites/${siteKey}`, faviconUUID: uuid}
        });
        publishAndWaitJobEnding(`/sites/${siteKey}`);
    });
};

/**
 * Restores a site's favicon to an already-uploaded file without re-uploading.
 * Looks up the existing file node's UUID, re-asserts jmix:favicon mixin
 * (safe to call even if mixin is present), and re-points the reference property.
 * Use this in after() hooks to avoid duplicate-node errors.
 */
export const restoreFavicon = (siteKey: string, existingFilePath: string): void => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    getNodeByPath(existingFilePath).then((resp: any) => {
        const uuid: string = resp.data.jcr.nodeByPath.uuid;
        addMixins(`/sites/${siteKey}`, ['jmix:favicon']);
        cy.apollo({
            mutationFile: 'graphql/mutation/setFaviconProperty.graphql',
            variables: {sitePath: `/sites/${siteKey}`, faviconUUID: uuid}
        });
        publishAndWaitJobEnding(`/sites/${siteKey}`);
    });
};

/**
 * Issues a GET /favicon.ico to the Jahia instance with a custom Host header,
 * simulating a browser visiting that virtual domain.
 */
export const faviconRequest = (serverName: string): Cypress.Chainable<Cypress.Response<string>> => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const jahiaUrl: string = (Cypress as any).env('JAHIA_URL') || 'http://jahia:8080';
    return cy.request<string>({
        method: 'GET',
        url: `${jahiaUrl}/favicon.ico`,
        headers: {Host: serverName},
        failOnStatusCode: false,
        encoding: 'base64'
    });
};
