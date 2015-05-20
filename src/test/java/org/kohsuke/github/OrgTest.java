/*
 * © Copyright 2015 -  SourceClear Inc
 */
package org.kohsuke.github;

import org.junit.Test;

import java.io.IOException;

public class OrgTest extends AbstractGitHubApiTestBase {

    // Validate by getting an organization's owner team and then using the new
    // getUsersMembership call to see if admin was returned.
    // Otherwise this will assert nothing
    @Test
    public void testOrgMembership() throws IOException {
        GHMyself self = this.gitHub.getMyself();
        GHPersonSet<GHOrganization> orgs = self.getOrganizations();
        for (GHOrganization ghOrganization : orgs) {
            GHTeam ghTeam = ghOrganization.getTeamByName("Owners");

            GHOrganizationMembership membership = ghOrganization.getUsersMembership(self);

            if (ghTeam != null) {
                boolean ownerTeamMember = ghTeam.hasMember(self);
                if (ownerTeamMember){
                    assertTrue(membership.getRole().equalsIgnoreCase("admin"));
                }
            }
        }
    }

}