/*
 * © Copyright 2015 -  SourceClear Inc
 */
package org.kohsuke.github;

import java.net.URL;

/**
 *
 */
public class GHOrganizationMembership {

    private URL url, organization_url;

    private String state, role;

    private GHUser user;

    private GHOrganization organization;

    public URL getUrl() {
        return url;
    }

    public URL getOrganizationUrl() {
        return organization_url;
    }

    public String getState() {
      return state;
    }

    public String getRole() {
        return role;
    }

    public GHUser getUser() {
        return user;
    }

    public GHOrganization getOrganization() {
        return organization;
    }

    /*package*/ void wrap(GitHub root) {
        if (user != null) {
            user.root = root;
        }
        if (organization != null) {
            organization.root = root;
        }
    }
}
