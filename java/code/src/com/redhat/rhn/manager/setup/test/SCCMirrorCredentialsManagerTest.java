/**
 * Copyright (c) 2014 SUSE
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.manager.setup.test;

import com.redhat.rhn.domain.credentials.CredentialsFactory;
import com.redhat.rhn.manager.content.ContentSyncException;
import com.redhat.rhn.manager.setup.MirrorCredentialsDto;
import com.redhat.rhn.manager.setup.NCCMirrorCredentialsManager;
import com.redhat.rhn.manager.setup.SCCMirrorCredentialsManager;
import com.redhat.rhn.testing.RhnMockStrutsTestCase;

import java.util.List;

/**
 * Tests for {@link NCCMirrorCredentialsManager}.
 */
public class SCCMirrorCredentialsManagerTest extends RhnMockStrutsTestCase {

    // Manager class instance
    private SCCMirrorCredentialsManager credsManager;

    /**
     * Test findMirrorCredentials().
     * @throws Exception if something goes wrong
     */
    public void testFindAllMirrorCreds() throws Exception {
        MirrorCredentialsDto creds0 = storeTestCredentials(0);
        MirrorCredentialsDto creds1 = storeTestCredentials(1);
        MirrorCredentialsDto creds2 = storeTestCredentials(2);
        List<MirrorCredentialsDto> creds = credsManager.findMirrorCredentials();
        assertTrue(creds.size() >= 3);
        assertTrue(creds.contains(creds0));
        assertTrue(creds.contains(creds1));
        assertTrue(creds.contains(creds2));
    }

    /**
     * Test findMirrorCredentials(long).
     * @throws Exception if something goes wrong
     */
    public void testFindMirrorCredsById() throws Exception {
        MirrorCredentialsDto creds0 = storeTestCredentials(0);
        MirrorCredentialsDto creds1 = storeTestCredentials(1);
        MirrorCredentialsDto creds2 = storeTestCredentials(2);
        assertEquals(creds0, credsManager.findMirrorCredentials(creds0.getId()));
        assertEquals(creds1, credsManager.findMirrorCredentials(creds1.getId()));
        assertEquals(creds2, credsManager.findMirrorCredentials(creds2.getId()));
    }

    /**
     * Test deleteMirrorCredentials().
     * @throws Exception if something goes wrong
     */
    public void testDeleteCredentials() {
        MirrorCredentialsDto creds0 = storeTestCredentials(0);
        MirrorCredentialsDto creds1 = storeTestCredentials(1);
        int size = credsManager.findMirrorCredentials().size();
        assertTrue(size >= 2);
        credsManager.deleteMirrorCredentials(creds0.getId(), user, request);
        List<MirrorCredentialsDto> creds = credsManager.findMirrorCredentials();
        assertEquals(size - 1, creds.size());
        assertFalse(creds.contains(creds0));
        assertTrue(creds.contains(creds1));
    }

    /**
     * Test makePrimaryCredentials()
     * @throws Exception if something goes wrong
     */
    public void testMakePrimaryCredentials() {
        MirrorCredentialsDto creds0 = storeTestCredentials(0);
        MirrorCredentialsDto creds1 = storeTestCredentials(1);
        MirrorCredentialsDto creds2 = storeTestCredentials(2);

        credsManager.makePrimaryCredentials(creds0.getId(), user, request);
        assertTrue(credsManager.findMirrorCredentials(creds0.getId()).isPrimary());
        assertFalse(credsManager.findMirrorCredentials(creds1.getId()).isPrimary());
        assertFalse(credsManager.findMirrorCredentials(creds2.getId()).isPrimary());

        credsManager.makePrimaryCredentials(creds1.getId(), user, request);
        assertFalse(credsManager.findMirrorCredentials(creds0.getId()).isPrimary());
        assertTrue(credsManager.findMirrorCredentials(creds1.getId()).isPrimary());
        assertFalse(credsManager.findMirrorCredentials(creds2.getId()).isPrimary());

        credsManager.makePrimaryCredentials(creds2.getId(), user, request);
        assertFalse(credsManager.findMirrorCredentials(creds0.getId()).isPrimary());
        assertFalse(credsManager.findMirrorCredentials(creds1.getId()).isPrimary());
        assertTrue(credsManager.findMirrorCredentials(creds2.getId()).isPrimary());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();
        credsManager = new SCCMirrorCredentialsManager();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Clear test credentials from database
        for (MirrorCredentialsDto c : credsManager.findMirrorCredentials()) {
            if (c.getUser().startsWith("testuser")) {
                removeTestCredentials(c.getId());
            }
        }

        // Tear down the manager class instance
        credsManager = null;
    }

    /**
     * Store test credentials for a given id.
     *
     * @param id the id of stored credentials
     */
    private MirrorCredentialsDto storeTestCredentials(long id) {
        MirrorCredentialsDto creds = new MirrorCredentialsDto();
        creds.setUser("testuser" + id);
        creds.setPassword("testpass" + id);
        try {
            long dbId = credsManager.storeMirrorCredentials(creds, user, request);
            creds.setId(dbId);
        } catch (ContentSyncException e) {
            e.printStackTrace();
        }
        return creds;
    }

    /**
     * Clean up credentials from database by calling remove().
     *
     * @param id the index of credentials to remove
     */
    private void removeTestCredentials(long id) {
        CredentialsFactory.removeCredentials(
                CredentialsFactory.lookupCredentialsById(id));
    }
}
