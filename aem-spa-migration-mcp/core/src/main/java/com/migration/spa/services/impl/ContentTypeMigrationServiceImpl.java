package com.migration.spa.services.impl;

import com.migration.spa.services.ContentTypeMigrationService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = ContentTypeMigrationService.class, immediate = true)
public class ContentTypeMigrationServiceImpl implements ContentTypeMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ContentTypeMigrationServiceImpl.class);

    private static final String PROP_RESOURCE_TYPE = "sling:resourceType";
    private static final String PROP_PRIMARY_TYPE = "jcr:primaryType";

    // Content type constants
    public static final String TYPE_PAGE = "PAGE";
    public static final String TYPE_PARSYS = "PARSYS";
    public static final String TYPE_CONTAINER = "CONTAINER";
    public static final String TYPE_FORM = "FORM";
    public static final String TYPE_EXPERIENCE_FRAGMENT = "EXPERIENCE_FRAGMENT";
    public static final String TYPE_CONTENT_FRAGMENT = "CONTENT_FRAGMENT";
    public static final String TYPE_COMPONENT = "COMPONENT";

    @Override
    public boolean isPageComponent(Resource r) {
        if (r == null) {
            return false;
        }
        String primaryType = r.getValueMap().get(PROP_PRIMARY_TYPE, String.class);
        if ("cq:Page".equals(primaryType)) {
            return true;
        }
        String resourceType = getResourceType(r);
        return resourceType != null && (
                resourceType.contains("page") ||
                resourceType.endsWith("/page") ||
                resourceType.contains("wcm/foundation/components/page"));
    }

    @Override
    public boolean isParsysComponent(Resource r) {
        if (r == null) {
            return false;
        }
        String resourceType = getResourceType(r);
        if (resourceType == null) {
            return false;
        }
        return resourceType.contains("parsys") ||
               resourceType.equals("wcm/foundation/components/parsys") ||
               resourceType.equals("foundation/components/parsys");
    }

    @Override
    public boolean isContainerComponent(Resource r) {
        if (r == null) {
            return false;
        }
        String resourceType = getResourceType(r);
        if (resourceType == null) {
            return false;
        }
        return resourceType.contains("responsivegrid") ||
               resourceType.contains("container") ||
               resourceType.contains("layout-container") ||
               isParsysComponent(r);
    }

    @Override
    public boolean isFormComponent(Resource r) {
        if (r == null) {
            return false;
        }
        String resourceType = getResourceType(r);
        if (resourceType == null) {
            return false;
        }
        return resourceType.contains("form") ||
               resourceType.contains("forms/") ||
               resourceType.startsWith("foundation/components/form");
    }

    @Override
    public boolean isExperienceFragment(Resource r) {
        if (r == null) {
            return false;
        }
        String primaryType = r.getValueMap().get(PROP_PRIMARY_TYPE, String.class);
        if ("dam:Asset".equals(primaryType)) {
            return false;
        }
        String resourceType = getResourceType(r);
        if (resourceType != null && resourceType.contains("experience-fragment")) {
            return true;
        }
        // Check if it's under /content/experience-fragments
        return r.getPath() != null && r.getPath().startsWith("/content/experience-fragments");
    }

    @Override
    public boolean isContentFragment(Resource r) {
        if (r == null) {
            return false;
        }
        String primaryType = r.getValueMap().get(PROP_PRIMARY_TYPE, String.class);
        if ("dam:Asset".equals(primaryType)) {
            // Check jcr:content/contentFragment property
            Resource content = r.getChild("jcr:content");
            if (content != null) {
                Boolean isFragment = content.getValueMap().get("contentFragment", Boolean.class);
                return Boolean.TRUE.equals(isFragment);
            }
        }
        String resourceType = getResourceType(r);
        return resourceType != null && resourceType.contains("content-fragment");
    }

    @Override
    public String detectContentType(Resource r) {
        if (r == null) {
            log.warn("detectContentType called with null resource");
            return TYPE_COMPONENT;
        }
        if (isExperienceFragment(r)) {
            return TYPE_EXPERIENCE_FRAGMENT;
        }
        if (isContentFragment(r)) {
            return TYPE_CONTENT_FRAGMENT;
        }
        if (isPageComponent(r)) {
            return TYPE_PAGE;
        }
        if (isParsysComponent(r)) {
            return TYPE_PARSYS;
        }
        if (isContainerComponent(r)) {
            return TYPE_CONTAINER;
        }
        if (isFormComponent(r)) {
            return TYPE_FORM;
        }
        return TYPE_COMPONENT;
    }

    private String getResourceType(Resource r) {
        if (r == null) {
            return null;
        }
        ValueMap vm = r.getValueMap();
        return vm.get(PROP_RESOURCE_TYPE, String.class);
    }
}
